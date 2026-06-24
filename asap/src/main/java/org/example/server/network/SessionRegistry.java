package org.example.server.network;

import org.example.shared.protocol.PacketType;

import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {

    private final ConcurrentHashMap<String, ClientHandler> handlers = new ConcurrentHashMap<>();

    public void addSession(String login, ClientHandler handler) {
        handlers.put(login, handler);
    }

    public void removeSession(String login) {
        handlers.remove(login);
    }

    public boolean isUserOnline(String login) {
        return handlers.containsKey(login);
    }

    public ClientHandler getHandler(String login) {
        return handlers.get(login);
    }

    public void kickUser(String login) {
        ClientHandler handler = handlers.remove(login);
        if (handler != null) {
            handler.disconnectCleanly();
        }
    }
    public int getActiveCount() {
        return handlers.size();
    }
    // Розсилка всім підключеним клієнтам (наприклад, для статусів ONLINE/OFFLINE)
    public void broadcastAll(PacketType type, String payload) {
        handlers.values().forEach(handler -> {
            try {
                handler.sendPacket(type, payload);
            } catch (Exception ignored) {}
        });
    }
}