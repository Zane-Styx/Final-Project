package com.jjmc.chromashift.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple authoritative host:
 * - Accepts JoinRequest, assigns ids
 * - Receives InputMessage and applies simple position updates
 * - Broadcasts GameState (~20Hz) with all PlayerState entries
 */
public class HostServer {

    private Server server;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Network.PlayerState> playerStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Network.PlayerProfile> playerProfiles = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile long serverTime = 0;
    private final Object stateLock = new Object();

        // Physics and movement constants
    private static final float SERVER_GRAVITY = -900f;
    private static final float GROUND_Y = 100f;
    private static final float SERVER_SPEED = 200f;
    private static final float LERP_FACTOR = 0.15f;
    private static final float DASH_IMPULSE = 400f;
    private static final float DASH_COOLDOWN = 0.6f;
    private static final float ATTACK_COOLDOWN = 0.4f;
    
    // Update rate
    private static final float UPDATE_RATE = 1f/60f;
    private float updateAccumulator = 0;

    // Timing
    private long lastUpdateTime = System.currentTimeMillis();
    private long lastCleanupTime = System.currentTimeMillis();
    private long lastBroadcastTime = System.currentTimeMillis();

    public void start() throws IOException {
        server = new Server();
        Network.register(server);
        server.start();
        server.bind(Network.TCP_PORT, Network.UDP_PORT);
        running = true;

        server.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                long currentTime = System.currentTimeMillis();
                serverTime = currentTime;
                float frameDelta = (currentTime - lastUpdateTime) / 1000f;
                updateAccumulator += frameDelta;
                lastUpdateTime = currentTime;
                serverTime = currentTime;

                synchronized (stateLock) {
                    // Fixed timestep updates
                    while (updateAccumulator >= UPDATE_RATE) {
                        if (object instanceof Network.InputMessage) {
                            handleInputMessage(connection, (Network.InputMessage) object, UPDATE_RATE);
                        }
                        updateAccumulator -= UPDATE_RATE;
                    }

                    // Handle non-physics messages immediately
                    if (object instanceof Network.JoinRequest) {
                        handleJoinRequest(connection, (Network.JoinRequest) object);
                    }
                }

                // Cleanup and broadcast
                if (currentTime - lastCleanupTime > 5000) {
                    cleanupDisconnectedPlayers();
                    lastCleanupTime = currentTime;
                }
                
                // Broadcast world state (~20Hz)
                if (currentTime - lastBroadcastTime > 50) {
                    broadcastWorldState();
                    lastBroadcastTime = currentTime;
                }
            }

            private void handleJoinRequest(Connection connection, Network.JoinRequest request) {
                Network.JoinResponse response = new Network.JoinResponse();
                response.currentPlayers = playerStates.size();
                
                // Validate version and enforce 2-player limit
                if (!request.version.equals("1.0") || playerStates.size() >= 2) {
                    response.accepted = false;
                    response.reason = !request.version.equals("1.0") ? "Version mismatch" : "Server full (2 players max)";
                    connection.sendTCP(response);
                    System.out.println("[Host] Rejected join: " + response.reason);
                    return;
                }

                // Handle returning player
                int playerId = -1;
                if (request.profile != null && request.profile.id != 0) {
                    for (Network.PlayerProfile p : playerProfiles.values()) {
                        if (p.id == request.profile.id) {
                            playerId = p.id;
                            break;
                        }
                    }
                }

                // New player
                if (playerId == -1) {
                    playerId = nextId.getAndIncrement();
                    request.profile.id = playerId;
                }

                // Update connection and profile
                connections.put(playerId, connection);
                playerProfiles.put(playerId, request.profile);

                // Create or update player state
                Network.PlayerState state = playerStates.computeIfAbsent(playerId, id -> {
                    Network.PlayerState ps = new Network.PlayerState();
                    ps.id = id;
                    ps.x = 100 + id * 60;
                    ps.y = GROUND_Y;
                    ps.onGround = true;
                    return ps;
                });

                // Send join accepted
                response.accepted = true;
                response.assignedId = playerId;
                connection.sendTCP(response);

                // Broadcast new player joined
                Network.PlayerJoined joined = new Network.PlayerJoined();
                joined.id = playerId;
                joined.profile = request.profile;
                server.sendToAllTCP(joined);

                // Send full world state to new player
                sendWorldState(connection);

                System.out.println("[Host] Player joined: " + request.profile.name + " (ID: " + playerId + ")");
            }

            private void handleInputMessage(Connection connection, Network.InputMessage input, float delta) {
                Network.PlayerState state = playerStates.get(input.playerId);
                if (state == null || connections.get(input.playerId) != connection) {
                    return; // Invalid player or wrong connection
                }

                // Echo server time for latency calculation
                input.serverTime = serverTime;

                // Apply input and simulate
                simulatePlayer(state, input, delta);

                // Save last processed input time
                state.lastUpdateTime = serverTime;
            }

            @Override
            public void disconnected(Connection connection) {
                // Find and remove the player ID associated with this connection
                int playerIdToRemove = -1;
                for (ConcurrentHashMap.Entry<Integer, Connection> entry : connections.entrySet()) {
                    if (entry.getValue().equals(connection)) {
                        playerIdToRemove = entry.getKey();
                        break;
                    }
                }

                if (playerIdToRemove != -1) {
                    connections.remove(playerIdToRemove);
                    playerStates.remove(playerIdToRemove);
                }

                System.out.println("[Host] Connection disconnected. Players now: " + playerStates.size());
            }
        });

        // broadcast loop in background
        Thread t = new Thread(() -> {
            long tick = 0;
            while (running) {
                try {
                    Thread.sleep(50); // 20 Hz
                } catch (InterruptedException ignored) {}

                // Note: continuous per-player simulation is handled in received();
                // skip per-player processing in the broadcast loop to avoid unused-variable warnings.

                Network.GameState gs = new Network.GameState();
                gs.tick = tick++;
                gs.players = new ArrayList<>(playerStates.values());

                // Debug log (can be commented out for performance)
                // System.out.println("[Host] Broadcasting GameState with " + gs.players.size() + " players");

                for (Connection c : connections.values()) {
                    c.sendUDP(gs);
                }
            }
        }, "Host-Broadcast-Thread");
        t.setDaemon(true);
        t.start();
    }

    // --- Add a simulation method ---
    private void simulatePlayer(Network.PlayerState ps, Network.InputMessage im, float delta) {
        // Save previous state for interpolation
        ps.lastX = ps.x;
        ps.lastY = ps.y;
        ps.lastUpdateTime = serverTime;

        // Update animation state based on movement
        updateAnimation(ps, im);

        // Handle cooldowns
        updateCooldowns(ps, delta);

        // Apply horizontal movement
        float targetVelocityX = im.horizontal * SERVER_SPEED;
        ps.vx = lerp(ps.vx, targetVelocityX, LERP_FACTOR);
        
        // Apply dash if triggered
        if (im.isDashing && ps.dashCooldownRemaining <= 0f) {
            float dashDir = ps.facingLeft ? -1f : 1f;
            ps.vx += DASH_IMPULSE * dashDir;
            ps.dashCooldownRemaining = DASH_COOLDOWN;
            ps.currentAnim = "dash";
        }

        // Apply horizontal movement
        ps.x += ps.vx * delta;

        // Handle jump
        if (im.jump && ps.canJump && ps.onGround) {
            ps.vy = 350f; // Jump velocity (match Player.java)
            ps.onGround = false;
            ps.canJump = false;
            ps.currentAnim = "jump";
        }

        // Apply gravity and vertical movement
        if (!ps.onGround) {
            ps.vy += SERVER_GRAVITY * delta;
            ps.currentAnim = ps.vy < 0 ? "fall" : "jump";
        }
        ps.y += ps.vy * delta;

        // Ground collision
        if (ps.y <= GROUND_Y) {
            ps.y = GROUND_Y;
            ps.vy = 0;
            if (!ps.onGround) {
                ps.onGround = true;
                ps.canJump = true;
            }
        }

        // Mirror client flags with validation
        ps.isDashing = im.isDashing && ps.dashCooldownRemaining > 0;
        ps.isAttacking = im.isAttacking && ps.attackCooldownRemaining <= 0;
        if (ps.isAttacking) {
            ps.attackCooldownRemaining = ATTACK_COOLDOWN;
            ps.currentAnim = "attack";
        }
        ps.facingLeft = im.facingLeft;

        // Update timestamp
        ps.timestamp = serverTime;
    }

    private void updateAnimation(Network.PlayerState ps, Network.InputMessage im) {
        if (ps.isAttacking) return; // Don't interrupt attack animation
        
        if (Math.abs(ps.vx) > 1f && ps.onGround) {
            ps.currentAnim = "run";
            ps.animTime += 0.1f;
        } else if (ps.onGround) {
            ps.currentAnim = "idle";
            ps.animTime = 0f;
        }
    }

    private void updateCooldowns(Network.PlayerState ps, float delta) {
        if (ps.dashCooldownRemaining > 0f) {
            ps.dashCooldownRemaining -= delta;
        }
        if (ps.attackCooldownRemaining > 0f) {
            ps.attackCooldownRemaining -= delta;
        }
    }

    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private void cleanupDisconnectedPlayers() {
        long timeout = 10000; // 10 seconds
        long now = System.currentTimeMillis();
        
        synchronized (stateLock) {
            playerStates.entrySet().removeIf(entry -> {
                int id = entry.getKey();
                Connection conn = connections.get(id);
                if (conn == null || !conn.isConnected() || 
                    (now - entry.getValue().lastUpdateTime > timeout)) {
                    
                    connections.remove(id);
                    playerProfiles.remove(id);
                    
                    Network.PlayerLeft left = new Network.PlayerLeft();
                    left.id = id;
                    left.reason = "Timeout/Disconnected";
                    server.sendToAllTCP(left);
                    
                    return true;
                }
                return false;
            });
        }
    }

    private void broadcastWorldState() {
        Network.GameState state = new Network.GameState();
        state.tick = serverTime;
        state.serverTime = serverTime;
        synchronized (stateLock) {
            state.players = new ArrayList<>(playerStates.values());
            // Always include player profiles in gameData
            for (Integer id : playerProfiles.keySet()) {
                Network.PlayerProfile profile = playerProfiles.get(id);
                if (profile != null) {
                    state.gameData.put("profile_" + id, profile);
                }
            }
        }
        server.sendToAllTCP(state);
    }

    private void sendWorldState(Connection connection) {
        Network.GameState state = new Network.GameState();
        state.tick = serverTime;
        state.serverTime = serverTime;
        synchronized (stateLock) {
            state.players = new ArrayList<>(playerStates.values());
            for (Integer id : playerProfiles.keySet()) {
                Network.PlayerProfile profile = playerProfiles.get(id);
                if (profile != null) {
                    state.gameData.put("profile_" + id, profile);
                }
            }
        }
        connection.sendTCP(state);
    }

    public void stop() {
        running = false;
        if (server != null) server.stop();
    }
}
