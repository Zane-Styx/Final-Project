package com.jjmc.chromashift.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lightweight client wrapper:
 * - sends JoinRequest on connect
 * - collects incoming messages on a queue
 * - holds latest GameState.players in latestStates
 */
public class GameClient {

    private final Client client = new Client();
    private final String playerName;
    private volatile int myId = -1;
    private volatile long serverTimeOffset = 0;
    private volatile long lastPingTime = 0;
    private volatile float averageLatency = 0;
    private static final float LATENCY_ALPHA = 0.2f; // For exponential moving average
    
    private Network.PlayerProfile myProfile;
    private volatile Network.GameState currentState = new Network.GameState();
    private volatile Network.GameState previousState = new Network.GameState();
    private final ConcurrentLinkedQueue<Object> receivedQueue = new ConcurrentLinkedQueue<>();
    
    public GameClient(String playerName, int characterType) {
        this.playerName = playerName == null ? "Player" : playerName;
        this.myProfile = new Network.PlayerProfile(playerName, characterType);
    }

    public void start() {
        Network.register(client);
        client.start();

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Network.JoinRequest jr = new Network.JoinRequest();
                jr.profile = myProfile;
                connection.sendTCP(jr);
                System.out.println("[Client] connected -> sent JoinRequest as '" + myProfile.name + "'");
                lastPingTime = System.currentTimeMillis();
            }

            @Override
            public void received(Connection connection, Object object) {
                receivedQueue.add(object);
            }

            @Override
            public void disconnected(Connection connection) {
                System.out.println("[Client] disconnected from host");
            }
        });
    }

    public void connectAsync(String host) {
        new Thread(() -> {
            try {
                client.connect(Network.TIMEOUT, host, Network.TCP_PORT, Network.UDP_PORT);
            } catch (IOException e) {
                System.err.println("[Client] Connection failed: " + e.getMessage());
            }
        }, "kryonet-connect-thread").start();
    }

    /**
     * Call regularly from main loop. This will process messages and update local state.
     */
    public void consumeNetworkMessages() {
        Object obj;
        while ((obj = receivedQueue.poll()) != null) {
            if (obj instanceof Network.JoinResponse) {
                handleJoinResponse((Network.JoinResponse) obj);
            } else if (obj instanceof Network.GameState) {
                handleGameState((Network.GameState) obj);
            } else if (obj instanceof Network.PlayerJoined) {
                Network.PlayerJoined joined = (Network.PlayerJoined) obj;
                System.out.println("[Client] Player joined: " + joined.profile.name + " (ID: " + joined.id + ")");
            } else if (obj instanceof Network.PlayerLeft) {
                Network.PlayerLeft left = (Network.PlayerLeft) obj;
                System.out.println("[Client] Player left: ID " + left.id + " (" + left.reason + ")");
            }
        }
    }

    private void handleJoinResponse(Network.JoinResponse jr) {
        if (jr.accepted) {
            myId = jr.assignedId;
            myProfile.id = jr.assignedId;
            System.out.println("[Client] JoinResponse accepted. assignedId=" + myId);
        } else {
            System.out.println("[Client] JoinResponse rejected: " + jr.reason);
        }
    }

    private void handleGameState(Network.GameState gs) {
        // Update server time offset for better sync
        long now = System.currentTimeMillis();
        serverTimeOffset = gs.serverTime - now;
        
        // Store previous state for interpolation
        previousState = currentState;
        currentState = gs.copy();
        
        // Update latency tracking
        for (Network.PlayerState ps : gs.players) {
            if (ps.id == myId) {
                float latency = (now - ps.lastUpdateTime) / 2f;
                averageLatency = averageLatency * (1 - LATENCY_ALPHA) + latency * LATENCY_ALPHA;
                break;
            }
        }
    }

    public void sendInput(Network.InputMessage msg) {
        if (!client.isConnected()) return;
        
        msg.playerId = myId;
        msg.clientTime = System.currentTimeMillis();
        client.sendUDP(msg);
    }

    public Network.GameState getCurrentState() {
        return currentState;
    }

    public Network.GameState getPreviousState() {
        return previousState;
    }
    
    public Network.PlayerState interpolatePlayerState(int playerId, float alpha) {
        Network.PlayerState current = findPlayerState(currentState, playerId);
        Network.PlayerState previous = findPlayerState(previousState, playerId);
        
        if (current == null) return null;
        if (previous == null) return current;
        
        Network.PlayerState interpolated = new Network.PlayerState();
        interpolated.id = playerId;
        
        // Interpolate position
        interpolated.x = lerp(previous.x, current.x, alpha);
        interpolated.y = lerp(previous.y, current.y, alpha);
        
        // Copy non-interpolated state
        interpolated.isDashing = current.isDashing;
        interpolated.isAttacking = current.isAttacking;
        interpolated.onGround = current.onGround;
        interpolated.facingLeft = current.facingLeft;
        interpolated.currentAnim = current.currentAnim;
        interpolated.animTime = current.animTime;
        
        return interpolated;
    }
    
    private Network.PlayerState findPlayerState(Network.GameState state, int playerId) {
        if (state == null || state.players == null) return null;
        for (Network.PlayerState ps : state.players) {
            if (ps.id == playerId) return ps;
        }
        return null;
    }
    
    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    public float getAverageLatency() {
        return averageLatency;
    }

    public long getServerTime() {
        return System.currentTimeMillis() + serverTimeOffset;
    }

    public int getMyId() { 
        return myId; 
    }

    public Network.PlayerProfile getMyProfile() {
        return myProfile;
    }

    public void stop() { 
        client.stop(); 
    }
}
