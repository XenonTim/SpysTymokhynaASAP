package org.example.client.network;

import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketIO;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerConnection {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    private Socket socket;
    private DataOutputStream out;
    private Thread listenerThread;

    public void connect(Consumer<Packet> onPacket, Runnable onDisconnect) throws Exception {
        socket = new Socket(HOST, PORT);
        out = new DataOutputStream(socket.getOutputStream());

        NetworkListener listener = new NetworkListener(socket, onPacket, onDisconnect);
        listenerThread = new Thread(listener, "network-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void send(Packet packet) throws Exception {
        PacketIO.write(out, packet);
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }
}
