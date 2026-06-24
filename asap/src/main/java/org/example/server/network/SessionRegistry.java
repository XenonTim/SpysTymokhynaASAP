package org.example.server.network;

import org.example.shared.protocol.Packet;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {

    private final Set<String> onlineLogins = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ClientHandler> handlers = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> sessionTokens = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

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

    public Set<String> getAllOnlineLogins() {
        return onlineLogins;
    }

    public String issueSessionToken(String login) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessionTokens.put(login, token);
        return token;
    }

    public boolean isValidSessionToken(String login, String token) {
        if (token == null || token.isBlank()) return false;
        String expected = sessionTokens.get(login);
        return expected != null && expected.equals(token);
    }

    public void revokeSessionToken(String login) {
        sessionTokens.remove(login);
    }
}
