package com.jjmc.chromashift.network;

import com.esotericsoftware.kryonet.EndPoint;
import com.esotericsoftware.kryo.Kryo;

import java.util.ArrayList;
import java.util.HashMap;

public class Network {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;
    public static final int TIMEOUT = 5000; // ms

    // Call on both server and client right after creation and before start()
    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        kryo.setRegistrationRequired(true);
        // Basic connection messages
        kryo.register(PlayerProfile.class);
        kryo.register(JoinRequest.class);
        kryo.register(JoinResponse.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        // Game state and input
        kryo.register(InputMessage.class);
        kryo.register(PlayerState.class);
        kryo.register(GameState.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(String[].class);
        kryo.register(float[].class);
    }

    public static class PlayerProfile {
        public int id;          // Persistent player ID
        public String name;     // Display name
        public int characterType; // Visual style/color
        
        public PlayerProfile() {}
        
        public PlayerProfile(String name, int characterType) {
            this.name = name;
            this.characterType = characterType;
        }
    }

    // Connection Messages
    public static class JoinRequest {
        public String playerName;
        public PlayerProfile profile;
        public String version = "1.0"; // Protocol version
    }

    public static class JoinResponse {
        public boolean accepted;
        public int assignedId;      // Server-assigned ID if new player
        public String reason;       // Rejection reason if !accepted
        public int maxPlayers = 2;  // Server configuration
        public int currentPlayers;  // Current player count
    }

    public static class PlayerJoined {
        public int id;
        public PlayerProfile profile;
    }

    public static class PlayerLeft {
        public int id;
        public String reason;
    }

    // Game Messages
    public static class InputMessage {
        public int playerId;
        public float horizontal;   // -1..1
        public boolean jump;
        public long clientTime;    // For latency calculation
        public long serverTime;    // Server can echo back
        public float[] position;   // Client prediction [x,y]
        
        // Action states
        public boolean isDashing;
        public boolean isAttacking;
        public boolean facingLeft;
        
        // Movement info
        public float velocityX;
        public float velocityY;
    }

    public static class PlayerState {
        public int id;
        // Position and physics
        public float x, y;
        public float vx, vy;
        public long timestamp;    // Server time when this state was created
        
        // Core state
        public boolean isDashing;
        public boolean isAttacking;
        public boolean onGround;
        public boolean facingLeft;
        
        // Animation state
        public String currentAnim;
        public float animTime;
        
        // Gameplay state
        public transient float dashCooldownRemaining = 0f;
        public transient float attackCooldownRemaining = 0f;
        public boolean canJump = true;
        
        // Interpolation helpers
        public float lastX, lastY;
        public long lastUpdateTime;
    }

    public static class GameState {
        public long tick;
        public long serverTime;
        public ArrayList<PlayerState> players = new ArrayList<>();
        public HashMap<String, Object> gameData = new HashMap<>(); // Custom game state
        
        public GameState copy() {
            GameState gs = new GameState();
            gs.tick = this.tick;
            gs.serverTime = this.serverTime;
            gs.players = new ArrayList<>(players);
            gs.gameData = new HashMap<>(gameData);
            return gs;
        }
    }
}
