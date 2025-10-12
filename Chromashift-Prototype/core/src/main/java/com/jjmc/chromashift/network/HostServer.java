package com.jjmc.chromashift.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HostServer {

    private Server server;
    private AtomicInteger nextId = new AtomicInteger(1);
    private ConcurrentHashMap<Integer, Connection> players = new ConcurrentHashMap<>();

    public void start() throws IOException {
        server = new Server();
        Network.register(server);
        server.start();
        server.bind(Network.TCP_PORT, Network.UDP_PORT);

        server.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof Network.JoinRequest) {
                    Network.JoinRequest jr = (Network.JoinRequest) object;
                    int assignedId = nextId.getAndIncrement();
                    players.put(assignedId, connection);

                    Network.JoinResponse resp = new Network.JoinResponse();
                    resp.accepted = true;
                    resp.assignedId = assignedId;
                    connection.sendTCP(resp);

                    System.out.println("[Host] Accepted join from '" + jr.name + "' assignedId=" + assignedId);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                System.out.println("[Host] Client disconnected: " + connection);
            }
        });
    }

    public void stop() {
        if (server != null) server.stop();
    }
}
