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

    private volatile boolean running = false;

    // Define a simple gravity constant inside HostServer
    private static final float SERVER_GRAVITY = -900f;
    private static final float GROUND_Y = 100f;
    // NOTE: Use the same value for speedPerMsg as used in the InputMessage handling
    private static final float SERVER_SPEED_PER_MSG = 120f * (1f / 20f);
    private long lastUpdateTime = System.currentTimeMillis();

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
                float delta = (currentTime - lastUpdateTime) / 1000f; // Calculate delta time
                lastUpdateTime = currentTime;
                if (object instanceof Network.JoinRequest) {
                    Network.JoinRequest jr = (Network.JoinRequest) object;
                    int assignedId = nextId.getAndIncrement();
                    connections.put(assignedId, connection);

                    Network.JoinResponse resp = new Network.JoinResponse();
                    resp.accepted = true;
                    resp.assignedId = assignedId;
                    connection.sendTCP(resp);

                    // initialize player
                    Network.PlayerState ps = new Network.PlayerState();
                    ps.id = assignedId;
                    ps.x = 100 + assignedId * 60;
                    ps.y = 100;
                    // Note: vx/vy are currently unused by the host's simple physics but are included in PlayerState
                    ps.vx = 0;
                    ps.vy = 0;
                    playerStates.put(assignedId, ps);

                    System.out.println("[Host] Accepted join from '" + jr.name + "' assignedId=" + assignedId);
                    System.out.println("[Host] Now tracking players: " + playerStates.size());
                } else if (object instanceof Network.InputMessage) {
                    Network.InputMessage im = (Network.InputMessage) object;
                    Network.PlayerState ps = playerStates.get(im.playerId);
                    if (ps == null) return;

                    // Simple physics and state mirroring (from previous fix):
                    float speedPerMsg = 120f * delta; // Use actual delta time
                    ps.x += im.horizontal * speedPerMsg;

                    // JUMP AND GRAVITY LOGIC
                    if (im.jump && ps.y <= GROUND_Y) { // Only jump if on ground
                        ps.vy = 350f; // Jump force
                    }

                    // Apply gravity continuously
                    if (ps.y > GROUND_Y) {
                        ps.vy += SERVER_GRAVITY * delta;
                    }
                    ps.y += ps.vy * delta;

                    // Ground check
                    if (ps.y <= GROUND_Y) {
                        ps.y = GROUND_Y;
                        ps.vy = 0;
                        ps.onGround = true;
                    } else {
                        ps.onGround = false;
                    }

                    // Mirror Client State Flags
                    ps.isDashing = im.isDashing;
                    ps.isAttacking = im.isAttacking;
                    ps.facingLeft = im.facingLeft;
                    ps.vx = im.horizontal * 200f; // Update velocity X for facing direction logic
                }
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

                for (Network.PlayerState ps : playerStates.values()) {
                    // Since we only get input from received(), this simple loop won't work well
                    // unless we refactor to store the last InputMessage per player.

                    // For now, let's skip continuous simulation in the broadcast loop
                    // and simply apply continuous gravity inside the received() method
                    // where input is processed (the least efficient way, but simplest fix):
                }

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

        // 1. Apply Input (Horizontal/Jump)
        // Horizontal movement (simple)
        ps.x += im.horizontal * SERVER_SPEED_PER_MSG;
        ps.vx = im.horizontal * 200f; // Set a velocity for facing direction

        // Check for jump input and set initial velocity
        if (im.jump && ps.onGround) {
            ps.vy = 350f; // Jump force (Must match client/Player.java jumpForce)
            ps.onGround = false;
        }

        // 2. Apply Gravity
        if (!ps.onGround) {
            ps.vy += SERVER_GRAVITY * delta;
        }
        ps.y += ps.vy * delta;

        // 3. Apply Ground Collision
        if (ps.y <= GROUND_Y) {
            ps.y = GROUND_Y;
            ps.vy = 0;
            ps.onGround = true;
        }

        // 4. Mirror Client State Flags (Dash/Attack)
        ps.isDashing = im.isDashing;
        ps.isAttacking = im.isAttacking;
        ps.facingLeft = im.facingLeft;
    }

    public void stop() {
        running = false;
        if (server != null) server.stop();
    }
}
