package com.jjmc.chromashift.network;

import com.esotericsoftware.kryonet.EndPoint;
import com.esotericsoftware.kryo.Kryo;

import java.util.ArrayList;

public class Network {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;
    public static final int TIMEOUT = 5000; // ms

    // Call on both server and client right after creation and before start()
    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        kryo.setRegistrationRequired(true);
        kryo.register(JoinRequest.class);
        kryo.register(JoinResponse.class);
        kryo.register(InputMessage.class);
        kryo.register(PlayerState.class);
        kryo.register(GameState.class);
        kryo.register(ArrayList.class);
    }

    // Messages
    public static class JoinRequest {
        public String name;
    }

    public static class JoinResponse {
        public boolean accepted;
        public int assignedId;
        public String reason;
    }

    // In com.jjmc.chromashift.network.Network.java

    public static class InputMessage {
        public int playerId;
        public float horizontal; // -1..1
        public boolean jump;
        public long clientTime;

        // --- ADD THESE FIELDS (State Mirroring) ---
        public boolean isDashing;
        public boolean isAttacking;
        public boolean facingLeft;
        // ------------------------------------------
    }

    public static class PlayerState {
        public int id;
        public float x, y;
        public float vx, vy;

        // --- ADD THESE FIELDS ---
        public boolean isDashing;
        public boolean isAttacking;
        public boolean onGround;
        public boolean facingLeft;
        // ------------------------
    }

    public static class GameState {
        public long tick;
        public ArrayList<PlayerState> players = new ArrayList<>();
    }
}
