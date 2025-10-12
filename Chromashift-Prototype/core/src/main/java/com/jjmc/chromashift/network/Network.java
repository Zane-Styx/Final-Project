// Network.java
package com.jjmc.chromashift.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import java.util.ArrayList;

public class Network {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;
    public static final int TIMEOUT = 5000; // ms

    // Call on both server and client right after creation and before start()
    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        kryo.setRegistrationRequired(true); // optional but recommended
        kryo.register(JoinRequest.class);
        kryo.register(JoinResponse.class);
        kryo.register(InputMessage.class);
        kryo.register(PlayerState.class);
        kryo.register(GameState.class);
        kryo.register(ArrayList.class); // used inside GameState
        // register primitives / arrays / other collections if you use them
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

    public static class InputMessage {
        public int playerId;
        public float horizontal; // example
        public float vertical;
        public boolean jump;
        public long clientTime; // optional for latency compensation
    }

    public static class PlayerState {
        public int id;
        public float x, y;
        public float vx, vy;
    }

    public static class GameState {
        public long tick; // server tick
        public ArrayList<PlayerState> players = new ArrayList<>();
    }
}
