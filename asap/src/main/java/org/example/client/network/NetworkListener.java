package org.example.client.network;

import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketIO;

import java.io.DataInputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkListener implements Runnable {

    private final Socket socket;
    private final Consumer<Packet> onPacket;
    private final Runnable onDisconnect;

    public NetworkListener(Socket socket, Consumer<Packet> onPacket, Runnable onDisconnect) {
        this.socket = socket;
        this.onPacket = onPacket;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void run() {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            while (!socket.isClosed()) {
                Packet packet = PacketIO.read(in);
                onPacket.accept(packet);
            }
        } catch (Exception e) {
            if (!socket.isClosed()) {
                System.out.println("[Client] Disconnected from server");
            }
        } finally {
            onDisconnect.run();
        }
    }
}
