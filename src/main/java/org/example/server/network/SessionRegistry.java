package org.example.server.network;

import org.example.shared.protocol.Packet;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {

    private final Set<String> onlineLogins = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ClientHandler> handlers = new ConcurrentHashMap<>();

    public void register(String login, ClientHandler handler) {
        onlineLogins.add(login);
        if (handler != null) {
            handlers.put(login, handler);
        }
    }

    public void unregister(String login) {
        onlineLogins.remove(login);
        handlers.remove(login);
    }

    public ClientHandler getHandler(String login) {
        return handlers.get(login);
    }

    public boolean isOnline(String login) {
        return onlineLogins.contains(login);
    }

    public int getActiveCount() {
        return onlineLogins.size();
    }

    public void broadcast(Packet packet, String excludeLogin) {
        handlers.forEach((login, handler) -> {
            if (!login.equals(excludeLogin)) {
                try {
                    handler.sendPacket(packet);
                } catch (Exception ignored) {}
            }
        });
    }
}
