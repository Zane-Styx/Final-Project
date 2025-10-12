package com.jjmc.chromashift.network;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameClient {

    private final Client client = new Client();
    private final String playerName;
    private volatile int myId = -1;

    // Queue to safely pass network events to the main thread
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
                // Send join request immediately
                Network.JoinRequest jr = new Network.JoinRequest();
                jr.name = playerName;
                connection.sendTCP(jr);
                System.out.println("[Client] Connected - sent JoinRequest as '" + playerName + "'");
            }

            @Override
            public void received(Connection connection, Object object) {
                receivedQueue.add(object);
            }

            @Override
            public void disconnected(Connection connection) {
                System.out.println("[Client] Disconnected from host");
            }
        });
    }

    public void connectAsync(String host) throws IOException {
        // Connect on a background thread to avoid freezing UI
        new Thread(() -> {
            try {
                client.connect(Network.TIMEOUT, host, Network.TCP_PORT, Network.UDP_PORT);
            } catch (IOException e) {
                System.err.println("[Client] Connection failed: " + e.getMessage());
                e.printStackTrace();
            }
        }, "kryonet-connect-thread").start();
    }

    public void consumeNetworkMessages() {
        Object obj;
        while ((obj = receivedQueue.poll()) != null) {
            if (obj instanceof Network.JoinResponse) {
                Network.JoinResponse jr = (Network.JoinResponse) obj;
                myId = jr.assignedId;
                Gdx.app.postRunnable(() ->
                    System.out.println("[Client] Joined! assignedId=" + myId));
            }
        }
    }

    public void stop() {
        client.stop();
    }

    public int getMyId() {
        return myId;
    }
}
