package org.example.server.network;

import org.example.db.ChatRepository;
import org.example.db.MessageRepository;
import org.example.db.UserRepository;
import org.example.models.Chat;
import org.example.models.Message;
import org.example.models.User;
import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketIO;
import org.example.shared.protocol.PacketType;
import org.example.shared.protocol.PayloadBuilder;
import org.example.shared.security.AesUtil;
import org.example.shared.security.PasswordUtil;

import javax.crypto.SecretKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final SessionRegistry registry;
    private final UserRepository userRepo;
    private final MessageRepository messageRepo;
    private final ChatRepository chatRepo;

    private DataInputStream in;
    private DataOutputStream out;
    private String authenticatedLogin;
    private SecretKey sessionKey;

    public ClientHandler(Socket socket, SessionRegistry registry) {
        this.socket = socket;
        this.registry = registry;
        this.userRepo = new UserRepository();
        this.messageRepo = new MessageRepository();
        this.chatRepo = new ChatRepository();
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            sessionKey = AesUtil.generateKey();

            while (!socket.isClosed()) {
                try {
                    Packet packet = PacketIO.read(in);
                    dispatch(packet);
                } catch (EOFException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("[Server Error] Помилка обробки пакету: " + e.getMessage());
                    try {
                        sendError("Помилка: " + e.getLocalizedMessage());
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.out.println("[Server] Помилка з'єднання: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void dispatch(Packet packet) throws Exception {
        switch (packet.getType()) {
            case REGISTER_REQUEST -> handleRegister(packet);
            case LOGIN_REQUEST    -> handleLogin(packet);
            case SEND_MSG         -> handleSendMessage(packet);
            case GET_HISTORY      -> handleGetHistory(packet);
            case GET_CONTACTS     -> handleGetContacts(packet);
            case CREATE_CHAT      -> handleCreateChat(packet);
            case ADMIN_ACTION     -> handleAdminAction(packet);
            case PING             -> sendPacket(new Packet(PacketType.PONG, new byte[0]));
            default               -> sendError("Unknown packet type");
        }
    }

    private void handleRegister(Packet packet) throws Exception {
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String login    = data.get("login");
        String password = data.get("password");

        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            sendPacket(new Packet(PacketType.REGISTER_FAIL,
                    new PayloadBuilder().add("reason", "Login and password are required").build()));
            return;
        }

        if (userRepo.existsByLogin(login)) {
            sendPacket(new Packet(PacketType.REGISTER_FAIL,
                    new PayloadBuilder().add("reason", "Login already taken").build()));
            return;
        }

        String hash = PasswordUtil.hash(password);
        User user = new User(login, hash, User.Role.USER);
        userRepo.save(user);

        sendPacket(new Packet(PacketType.REGISTER_SUCCESS,
                new PayloadBuilder().add("login", login).build()));
    }

    private void handleLogin(Packet packet) throws Exception {
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String login    = data.get("login");
        String password = data.get("password");

        User user = userRepo.findByLogin(login);
        if (user == null) {
            sendPacket(new Packet(PacketType.LOGIN_FAIL,
                    new PayloadBuilder().add("reason", "Invalid credentials").build()));
            return;
        }

        if (password == null || password.isBlank()) {
            authenticatedLogin = login;
            registry.register(login, this);
            userRepo.setOnline(login, true);
            broadcastStatusUpdate(login, true);
            sendPacket(new Packet(PacketType.LOGIN_SUCCESS,
                    new PayloadBuilder().add("login", login).add("role", user.getRole().name()).build()));
            return;
        }

        if (user.getStatus() == User.Status.BANNED) {
            sendPacket(new Packet(PacketType.LOGIN_FAIL,
                    new PayloadBuilder().add("reason", "Your account is banned").build()));
            return;
        }

        authenticatedLogin = login;
        registry.register(login, this);
        userRepo.setOnline(login, true);

        broadcastStatusUpdate(login, true);

        sendPacket(new Packet(PacketType.LOGIN_SUCCESS,
                new PayloadBuilder()
                        .add("login", login)
                        .add("role", user.getRole().name())
                        .build()));
    }

    private void handleSendMessage(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String chatId           = data.get("chatId");
        String encryptedContent = data.get("content");

        Message message = new Message(chatId, authenticatedLogin, encryptedContent);
        messageRepo.save(message);

        Chat chat = chatRepo.findById(chatId);
        if (chat == null) {
            sendError("Chat not found");
            return;
        }

        Packet forward = new Packet(PacketType.SEND_MSG,
                new PayloadBuilder()
                        .add("chatId", chatId)
                        .add("sender", authenticatedLogin)
                        .add("content", encryptedContent)
                        .add("messageId", message.getId())
                        .build());

        for (String member : chat.getMemberLogins()) {
            if (!member.equals(authenticatedLogin)) {
                ClientHandler handler = registry.getHandler(member);
                if (handler != null) {
                    handler.sendPacket(forward);
                }
            }
        }

        sendPacket(new Packet(PacketType.MSG_DELIVERED,
                new PayloadBuilder().add("messageId", message.getId()).build()));
    }

    private void handleGetHistory(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String chatId = data.get("chatId");

        List<Message> messages = messageRepo.findByChatId(chatId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (i > 0) sb.append(",");

            Map<String, String> item = new java.util.LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("sender", m.getSenderLogin());
            item.put("content", m.getEncryptedContent());
            item.put("sentAt", String.valueOf(m.getSentAt().toEpochMilli()));

            sb.append(org.example.gateway.SimpleJson.toJson(item));
        }
        sb.append("]");

        sendPacket(new Packet(PacketType.HISTORY_RESPONSE,
                new PayloadBuilder()
                        .add("chatId", chatId)
                        .add("messages", sb.toString())
                        .build()));
    }

    private void handleGetContacts(Packet packet) throws Exception {
        requireAuth();
        List<Chat> chats = chatRepo.findByMember(authenticatedLogin);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < chats.size(); i++) {
            Chat c = chats.get(i);
            if (i > 0) sb.append(",");

            Map<String, String> item = new java.util.LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getName());
            item.put("type", c.getType().name());

            sb.append(org.example.gateway.SimpleJson.toJson(item));
        }
        sb.append("]");

        sendPacket(new Packet(PacketType.CONTACTS_RESPONSE,
                new PayloadBuilder().add("chats", sb.toString()).build()));
    }

    private void handleCreateChat(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());

        String typeStr = data.get("chatType");
        if (typeStr == null || typeStr.isBlank()) {
            typeStr = data.get("type");
        }

        if (typeStr == null || typeStr.isBlank()) {
            typeStr = "PRIVATE";
        }

        typeStr = typeStr.trim().toUpperCase();

        String name    = data.get("name");
        String members = data.get("members");

        if (members == null || members.isBlank()) {
            sendError("Members list cannot be empty");
            return;
        }

        List<String> memberList = List.of(members.split(","));

        if ("PRIVATE".equals(typeStr) && memberList.size() == 2) {
            Chat existing = chatRepo.findPrivateChat(memberList.get(0), memberList.get(1));
            if (existing != null) {
                sendPacket(new Packet(PacketType.CHAT_CREATED,
                        new PayloadBuilder().add("chatId", existing.getId()).build()));
                return;
            }
        }

        Chat chat = new Chat(Chat.Type.valueOf(typeStr), name, memberList);
        chatRepo.save(chat);

        sendPacket(new Packet(PacketType.CHAT_CREATED,
                new PayloadBuilder().add("chatId", chat.getId()).build()));
    }

    private void handleAdminAction(Packet packet) throws Exception {
        requireAuth();

        User actor = userRepo.findByLogin(authenticatedLogin);
        if (actor == null || actor.getRole() != User.Role.ADMIN) {
            sendError("Access denied: admin only");
            return;
        }

        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String action   = data.get("action");
        String target   = data.get("target");
        String messageId = data.get("messageId");

        switch (action) {
            case "BAN_USER" -> {
                userRepo.setStatus(target, User.Status.BANNED);
                ClientHandler targetHandler = registry.getHandler(target);
                if (targetHandler != null) {
                    targetHandler.sendPacket(new Packet(PacketType.LOGIN_FAIL,
                            new PayloadBuilder().add("reason", "Your account has been banned").build()));
                    targetHandler.disconnect();
                }
                sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                        new PayloadBuilder().add("result", "User " + target + " banned").build()));
            }
            case "DELETE_USER" -> {
                ClientHandler targetHandler = registry.getHandler(target);
                if (targetHandler != null) {
                    targetHandler.disconnect();
                }
                userRepo.delete(target);
                sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                        new PayloadBuilder().add("result", "User " + target + " deleted").build()));
            }
            case "DELETE_MESSAGE" -> {
                messageRepo.markDeleted(messageId);
                sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                        new PayloadBuilder().add("result", "Message deleted").build()));
            }
            case "SERVER_STATS" -> {
                int activeConnections = registry.getActiveCount();
                sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                        new PayloadBuilder()
                                .add("result", "stats")
                                .add("connections", String.valueOf(activeConnections))
                                .build()));
            }
            default -> sendError("Unknown admin action: " + action);
        }
    }

    private void broadcastStatusUpdate(String login, boolean online) {
        Packet statusPacket = new Packet(PacketType.USER_STATUS_UPDATE,
                new PayloadBuilder()
                        .add("login", login)
                        .add("online", String.valueOf(online))
                        .build());
        registry.broadcast(statusPacket, login);
    }

    public synchronized void sendPacket(Packet packet) throws Exception {
        PacketIO.write(out, packet);
    }

    private void sendError(String reason) throws Exception {
        sendPacket(new Packet(PacketType.ERROR,
                new PayloadBuilder().add("reason", reason).build()));
    }

    private void requireAuth() {
        if (authenticatedLogin == null) {
            throw new IllegalStateException("Not authenticated");
        }
    }

    public void disconnect() {
        try {
            socket.close();
        } catch (Exception ignored) {}
    }

    private void cleanup() {
        if (authenticatedLogin != null) {
            registry.unregister(authenticatedLogin);
            userRepo.setOnline(authenticatedLogin, false);
            try {
                broadcastStatusUpdate(authenticatedLogin, false);
            } catch (Exception ignored) {}
        }
        try {
            socket.close();
        } catch (Exception ignored) {}
    }
}
