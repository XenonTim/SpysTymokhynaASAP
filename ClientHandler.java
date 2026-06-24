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
import org.example.shared.security.RsaUtil;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
    private boolean handshakeComplete = false;
    private volatile boolean disconnected = false;

    public ClientHandler(Socket socket, SessionRegistry registry,
                         MessageRepository messageRepo,
                         ChatRepository chatRepo,
                         UserRepository userRepo) {
        this.socket      = socket;
        this.registry    = registry;
        this.userRepo    = userRepo;
        this.messageRepo = messageRepo;
        this.chatRepo    = chatRepo;
    }

    @Override
    public void run() {
        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            executeKeyExchangeHandshake();
            
            while (!socket.isClosed()) {
                try {
                    Packet packet = readPacket();
                    dispatch(packet);
                } catch (EOFException e) {
                    break;
                } catch (IllegalStateException e) {
                    sendError(e.getMessage());
                } catch (Exception e) {
                    System.err.println("[ClientHandler] Помилка обробки пакету: " + e.getMessage());
                    try { sendError("Помилка: " + e.getLocalizedMessage()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[ClientHandler] Обрив з'єднання: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void executeKeyExchangeHandshake() throws Exception {
        var serverKeyPair = RsaUtil.generateKeyPair();
        byte[] publicKeyBytes = serverKeyPair.getPublic().getEncoded();
        
        out.writeInt(PacketType.KEY_EXCHANGE_INIT.ordinal());
        out.writeInt(publicKeyBytes.length);
        out.write(publicKeyBytes);
        out.flush();
        
        in.readInt();
        int encAesLength = in.readInt();
        byte[] encryptedAesKey = new byte[encAesLength];
        in.readFully(encryptedAesKey);
        
        byte[] decryptedKeyBytes = RsaUtil.decryptWithPrivateKey(encryptedAesKey, serverKeyPair.getPrivate());
        this.sessionKey = new SecretKeySpec(decryptedKeyBytes, 0, decryptedKeyBytes.length, "AES");
        this.handshakeComplete = true;
    }

    private Packet readPacket() throws Exception {
        int typeOrdinal = in.readInt();
        int payloadLen  = in.readInt();
        byte[] rawPayload = new byte[payloadLen];
        if (payloadLen > 0) in.readFully(rawPayload);
        
        PacketType type = PacketType.values()[typeOrdinal];
        if (handshakeComplete
                && type != PacketType.KEY_EXCHANGE_INIT
                && type != PacketType.KEY_EXCHANGE_RESP) {
            String encryptedBase64 = new String(rawPayload, StandardCharsets.UTF_8);
            String decrypted = AesUtil.decrypt(encryptedBase64, sessionKey);
            return new Packet(type, decrypted.getBytes(StandardCharsets.UTF_8));
        }
        return new Packet(type, rawPayload);
    }

    private void dispatch(Packet packet) throws Exception {
        switch (packet.getType()) {
            case REGISTER_REQUEST       -> handleRegister(packet);
            case LOGIN_REQUEST          -> handleLogin(packet);
            case SEND_MSG               -> handleSendMessage(packet);
            case EDIT_MESSAGE_REQUEST   -> handleEditMessage(packet);
            case DELETE_MESSAGE_REQUEST -> handleDeleteMessage(packet);
            case GET_HISTORY            -> handleGetHistory(packet);
            case GET_CONTACTS           -> handleGetContacts(packet);
            case CREATE_CHAT            -> handleCreateChat(packet);
            case ADMIN_ACTION           -> handleAdminAction(packet);
            case PING                   -> sendPacket(new Packet(PacketType.PONG, new byte[0]));
            case DISCONNECT             -> cleanup();
            default                     -> sendError("Невідомий тип пакету");
        }
    }

    private void handleRegister(Packet packet) throws Exception {
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String login    = data.getOrDefault("login", "").trim();
        String password = data.getOrDefault("password", "");
        
        if (login.isBlank() || password.isBlank()) {
            sendPacket(new Packet(PacketType.REGISTER_FAIL,
                    new PayloadBuilder().add("reason", "Логін або пароль не можуть бути порожніми").build()));
            return;
        }
        if (userRepo.existsByLogin(login)) {
            sendPacket(new Packet(PacketType.REGISTER_FAIL,
                    new PayloadBuilder().add("reason", "Логін вже зайнятий").build()));
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
        String login    = data.getOrDefault("login", "").trim();
        String password = data.getOrDefault("password", "");
        
        if (login.isBlank()) {
            sendPacket(new Packet(PacketType.LOGIN_FAIL,
                    new PayloadBuilder().add("reason", "Логін не може бути порожнім").build()));
            return;
        }
        
        User user = userRepo.findByLogin(login);
        if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
            sendPacket(new Packet(PacketType.LOGIN_FAIL,
                    new PayloadBuilder().add("reason", "Невірний логін або пароль").build()));
            return;
        }
        if (user.getStatus() == User.Status.BANNED) {
            sendPacket(new Packet(PacketType.LOGIN_FAIL,
                    new PayloadBuilder().add("reason", "Ваш акаунт заблоковано").build()));
            return;
        }
        
        authenticatedLogin = login;
        registry.register(login, this);
        userRepo.setOnline(login, true);
        broadcastStatusUpdate(login, true);
        
        for (String onlineUser : registry.getAllOnlineLogins()) {
            if (!onlineUser.equals(login)) {
                try {
                    sendPacket(new Packet(PacketType.USER_STATUS_UPDATE,
                            new PayloadBuilder()
                                    .add("login", onlineUser)
                                    .add("online", "true")
                                    .build()));
                } catch (Exception ignored) {}
            }
        }
        
        sendPacket(new Packet(PacketType.LOGIN_SUCCESS,
                new PayloadBuilder()
                        .add("login", login)
                        .add("role", user.getRole().name())
                        .build()));
    }

    private void handleSendMessage(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String chatId  = data.getOrDefault("chatId", "").trim();
        String content = data.getOrDefault("content", "");
        
        if (chatId.isBlank()) {
            sendError("chatId не вказано");
            return;
        }
        if (content.isBlank() || content.length() > 4000) {
            sendError("Некоректна довжина повідомлення");
            return;
        }
        
        Message message = new Message(chatId, authenticatedLogin, content);
        messageRepo.save(message);
        Chat chat = chatRepo.findById(chatId);
        if (chat == null) {
            sendError("Чат не знайдено");
            return;
        }
        
        Packet forward = new Packet(PacketType.SEND_MSG,
                new PayloadBuilder()
                        .add("chatId", chatId)
                        .add("sender", authenticatedLogin)
                        .add("content", content)
                        .add("messageId", message.getId())
                        .build());
                        
        for (String member : chat.getMemberLogins()) {
            if (!member.equals(authenticatedLogin)) {
                ClientHandler handler = registry.getHandler(member);
                if (handler != null) handler.sendPacket(forward);
            }
        }
        sendPacket(new Packet(PacketType.MSG_DELIVERED,
                new PayloadBuilder().add("messageId", message.getId()).build()));
    }

    private void handleEditMessage(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String messageId = data.getOrDefault("messageId", "").trim();
        String newText   = data.getOrDefault("content", "");
        
        if (messageId.isBlank()) {
            sendError("messageId не вказано");
            return;
        }
        if (newText.isBlank() || newText.length() > 4000) {
            sendError("Некоректний вміст повідомлення");
            return;
        }
        
        Message msg = messageRepo.findById(messageId);
        if (msg == null) {
            sendError("Повідомлення не знайдено");
            return;
        }
        if (!msg.getSenderLogin().equals(authenticatedLogin)) {
            sendError("Недостатньо прав для редагування");
            return;
        }
        
        messageRepo.updateMessageText(messageId, newText);
        Chat chat = chatRepo.findById(msg.getChatId());
        if (chat != null && chat.getMemberLogins() != null) {
            Packet refreshPacket = new Packet(PacketType.ADMIN_ACTION_RESULT,
                    new PayloadBuilder()
                            .add("result", "REFRESH_HISTORY")
                            .add("chatId", msg.getChatId())
                            .build());
            for (String member : chat.getMemberLogins()) {
                ClientHandler handler = registry.getHandler(member);
                if (handler != null) handler.sendPacket(refreshPacket);
            }
        }
    }

    private void handleDeleteMessage(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String messageId = data.getOrDefault("messageId", packet.getPayload() != null
                ? new String(packet.getPayload(), StandardCharsets.UTF_8).trim() : "");
                
        if (messageId.isBlank()) {
            sendError("messageId не вказано");
            return;
        }
        
        Message msg = messageRepo.findById(messageId);
        if (msg == null) {
            sendError("Повідомлення не знайдено");
            return;
        }
        
        User actor = userRepo.findByLogin(authenticatedLogin);
        boolean isAdmin  = actor != null && actor.getRole() == User.Role.ADMIN;
        boolean isAuthor = msg.getSenderLogin().equals(authenticatedLogin);
        
        if (!isAdmin && !isAuthor) {
            sendError("Недостатньо прав для видалення");
            return;
        }
        
        messageRepo.markDeleted(messageId);
        Chat chat = chatRepo.findById(msg.getChatId());
        
        if (chat != null && chat.getMemberLogins() != null) {
            Packet refreshPacket = new Packet(PacketType.ADMIN_ACTION_RESULT,
                    new PayloadBuilder()
                            .add("result", "REFRESH_HISTORY")
                            .add("chatId", msg.getChatId())
                            .build());
            for (String member : chat.getMemberLogins()) {
                ClientHandler handler = registry.getHandler(member);
                if (handler != null) handler.sendPacket(refreshPacket);
            }
        }
    }

    private void handleGetHistory(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String chatId = data.getOrDefault("chatId", "").trim();
        
        if (chatId.isBlank()) {
            sendError("chatId не вказано");
            return;
        }
        
        Chat chat = chatRepo.findById(chatId);
        if (chat == null || !chat.getMemberLogins().contains(authenticatedLogin)) {
            sendError("Немає доступу до цього чату");
            return;
        }
        
        List<Message> messages = messageRepo.findByChatId(chatId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (i > 0) sb.append(",");
            Map<String, String> item = new LinkedHashMap<>();
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
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getName());
            item.put("type", c.getType().name());
            item.put("memberLogins", c.getMemberLogins() != null
                    ? String.join(";", c.getMemberLogins()) : "");
            sb.append(org.example.gateway.SimpleJson.toJson(item));
        }
        sb.append("]");
        
        sendPacket(new Packet(PacketType.CONTACTS_RESPONSE,
                new PayloadBuilder().add("chats", sb.toString()).build()));
    }

    private void handleCreateChat(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String typeStr = data.getOrDefault("chatType", data.getOrDefault("type", "PRIVATE")).trim();
        Chat.Type chatType = "GROUP".equalsIgnoreCase(typeStr) ? Chat.Type.GROUP : Chat.Type.PRIVATE;
        String name    = data.getOrDefault("name", "").trim();
        String members = data.getOrDefault("members", "").trim();
        
        if (members.isBlank()) {
            sendError("Список учасників не може бути порожнім");
            return;
        }
        if (name.isBlank()) {
            name = "Chat_" + System.currentTimeMillis();
        }
        
        name = name.replace(",", "_");
        List<String> memberList = new java.util.ArrayList<>();
        
        for (String m : members.split(",")) {
            String t = m.trim();
            if (!t.isEmpty()) memberList.add(t);
        }
        if (!memberList.contains(authenticatedLogin)) {
            memberList.add(0, authenticatedLogin);
        }
        if (memberList.size() < 2) {
            sendError("Неможливо створити чат без інших учасників");
            return;
        }
        
        if (chatType == Chat.Type.PRIVATE && memberList.size() == 2) {
            Chat existing = chatRepo.findPrivateChat(memberList.get(0), memberList.get(1));
            if (existing != null) {
                sendPacket(new Packet(PacketType.CHAT_CREATED,
                        new PayloadBuilder()
                                .add("chatId", existing.getId())
                                .add("name", existing.getName())
                                .build()));
                return;
            }
        }
        
        Chat chat = new Chat(chatType, name, memberList);
        chatRepo.save(chat);
        Packet createdPacket = new Packet(PacketType.CHAT_CREATED,
                new PayloadBuilder()
                        .add("chatId", chat.getId())
                        .add("name", name)
                        .build());
                        
        sendPacket(createdPacket);
        for (String member : memberList) {
            if (member.equals(authenticatedLogin)) continue;
            ClientHandler handler = registry.getHandler(member);
            if (handler != null) handler.sendPacket(createdPacket);
        }
    }

    private void handleAdminAction(Packet packet) throws Exception {
        requireAuth();
        Map<String, String> data = PayloadBuilder.parse(packet.getPayload());
        String action    = data.getOrDefault("action", "");
        String target    = data.getOrDefault("target", "");
        String messageId = data.getOrDefault("messageId", "");
        boolean isOwnerAction = "DELETE_MESSAGE".equals(action) || "EDIT_MESSAGE".equals(action);
        
        if (isOwnerAction) {
            if (messageId.isBlank()) {
                sendError("Не вказано messageId");
                return;
            }
            Message originalMsg = messageRepo.findById(messageId);
            if (originalMsg == null) {
                sendError("Повідомлення не знайдено");
                return;
            }
            
            User actor = userRepo.findByLogin(authenticatedLogin);
            boolean isAdmin  = actor != null && actor.getRole() == User.Role.ADMIN;
            boolean isAuthor = originalMsg.getSenderLogin().equals(authenticatedLogin);
            
            if (!isAdmin && !isAuthor) {
                sendError("Доступ заборонено: ви не є автором цього повідомлення");
                return;
            }
            
            if ("DELETE_MESSAGE".equals(action)) {
                messageRepo.markDeleted(messageId);
            } else {
                if (target.isBlank()) {
                    sendError("Вміст не може бути порожнім");
                    return;
                }
                messageRepo.updateMessageText(messageId, target);
            }
            
            Packet refreshPacket = new Packet(PacketType.ADMIN_ACTION_RESULT,
                    new PayloadBuilder()
                            .add("result", "REFRESH_HISTORY")
                            .add("chatId", originalMsg.getChatId())
                            .build());
            registry.broadcast(refreshPacket, null);
        } else {
            User actor = userRepo.findByLogin(authenticatedLogin);
            if (actor == null || actor.getRole() != User.Role.ADMIN) {
                sendError("Доступ заборонено: тільки для адміністраторів");
                return;
            }
            
            switch (action) {
                case "BAN_USER" -> {
                    userRepo.setStatus(target, User.Status.BANNED);
                    ClientHandler targetHandler = registry.getHandler(target);
                    if (targetHandler != null) {
                        targetHandler.sendPacket(new Packet(PacketType.LOGIN_FAIL,
                                new PayloadBuilder().add("reason", "Ваш акаунт заблоковано").build()));
                        targetHandler.disconnect();
                    }
                    sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                            new PayloadBuilder().add("result", "Користувача " + target + " заблоковано").build()));
                    broadcastStatusUpdate(target, false);
                }
                case "DELETE_USER" -> {
                    if (target.equals(authenticatedLogin)) {
                        sendError("Неможливо видалити власний акаунт");
                        return;
                    }
                    userRepo.delete(target);
                    ClientHandler targetHandler = registry.getHandler(target);
                    if (targetHandler != null) {
                        targetHandler.sendPacket(new Packet(PacketType.LOGIN_FAIL,
                                new PayloadBuilder().add("reason", "Ваш акаунт видалено").build()));
                        targetHandler.disconnect();
                    }
                    sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                            new PayloadBuilder().add("result", "Користувача " + target + " видалено").build()));
                }
                case "SERVER_STATS" -> {
                    int activeConnections = registry.getActiveCount();
                    sendPacket(new Packet(PacketType.ADMIN_ACTION_RESULT,
                            new PayloadBuilder()
                                    .add("result", "stats")
                                    .add("connections", String.valueOf(activeConnections))
                                    .build()));
                }
                default -> sendError("Невідома адмін-команда: " + action);
            }
        }
    }

    private void broadcastStatusUpdate(String login, boolean online) {
        Packet statusPacket = new Packet(PacketType.USER_STATUS_UPDATE,
                new PayloadBuilder()
                        .add("login", login)
                        .add("online", String.valueOf(online))
                        .build());
        registry.broadcast(statusPacket, "");
    }

    public synchronized void sendPacket(Packet packet) throws Exception {
        if (socket.isClosed()) return;
        
        byte[] rawPayload = packet.getPayload() != null ? packet.getPayload() : new byte[0];
        byte[] payloadToSend;
        
        if (handshakeComplete
                && packet.getType() != PacketType.KEY_EXCHANGE_INIT
                && packet.getType() != PacketType.KEY_EXCHANGE_RESP) {
            String plain = new String(rawPayload, StandardCharsets.UTF_8);
            String encrypted = AesUtil.encrypt(plain, sessionKey);
            payloadToSend = encrypted.getBytes(StandardCharsets.UTF_8);
        } else {
            payloadToSend = rawPayload;
        }
        
        out.writeInt(packet.getType().ordinal());
        out.writeInt(payloadToSend.length);
        if (payloadToSend.length > 0) out.write(payloadToSend);
        out.flush();
    }

    private void sendError(String reason) throws Exception {
        sendPacket(new Packet(PacketType.ERROR,
                new PayloadBuilder().add("reason", reason).build()));
    }

    private void requireAuth() {
        if (authenticatedLogin == null) {
            throw new IllegalStateException("Не авторизовано");
        }
    }

    public void disconnect() {
        try { socket.close(); } catch (Exception ignored) {}
    }

    private void cleanup() {
        if (!disconnected) {
            synchronized (this) {
                if (disconnected) return;
                disconnected = true;
            }
            if (authenticatedLogin != null) {
                registry.unregister(authenticatedLogin);
                userRepo.setOnline(authenticatedLogin, false);
                try { broadcastStatusUpdate(authenticatedLogin, false); } catch (Exception ignored) {}
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}