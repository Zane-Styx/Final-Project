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

    private volatile ArrayList<Network.PlayerState> latestStates = new ArrayList<>();
    private final ConcurrentLinkedQueue<Object> receivedQueue = new ConcurrentLinkedQueue<>();

    public GameClient(String playerName) {
        this.playerName = playerName == null ? "Player" : playerName;
    }

    public void start() {
        Network.register(client);
        client.start();

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Network.JoinRequest jr = new Network.JoinRequest();
                jr.name = playerName;
                connection.sendTCP(jr);
                System.out.println("[Client] connected -> sent JoinRequest as '" + playerName + "'");
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
                Network.JoinResponse jr = (Network.JoinResponse) obj;
                myId = jr.assignedId;
                System.out.println("[Client] JoinResponse received. assignedId=" + myId);
            } else if (obj instanceof Network.GameState) {
                Network.GameState gs = (Network.GameState) obj;
                latestStates = gs.players;
                // System.out.println("[Client] Received GameState with " + gs.players.size() + " players (tick " + gs.tick + ")"); // Log reduced
            }
        }
    }

    public void sendInput(Network.InputMessage msg) {
        if (client.isConnected()) client.sendUDP(msg);
    }

    public ArrayList<Network.PlayerState> getLatestStates() {
        return latestStates;
    }

    public int getMyId() { return myId; }

    public void stop() { client.stop(); }
}
