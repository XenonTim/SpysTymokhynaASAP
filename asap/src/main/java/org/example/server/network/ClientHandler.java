package org.example.server.network;

import org.example.shared.protocol.PacketType;
import org.example.shared.protocol.PayloadBuilder;
import org.example.db.MessageRepository;
import org.example.db.ChatRepository;
import org.example.db.UserRepository;
import org.example.models.Message;
import org.example.models.Chat;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.UserStatus;
import org.example.shared.security.AesUtil;
import org.example.shared.security.PasswordUtil;
import org.example.shared.security.RsaUtil;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final SessionRegistry sessionRegistry;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    private String currentUserLogin;
    private Role currentUserRole;

    private SecretKey aesSessionKey;
    private boolean isHandshakeComplete = false;

    private volatile boolean disconnected = false;

    public ClientHandler(Socket socket, SessionRegistry sessionRegistry,
                         MessageRepository messageRepository,
                         ChatRepository chatRepository,
                         UserRepository userRepository) {
        this.socket = socket;
        this.sessionRegistry = sessionRegistry;
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run() {
        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            executeKeyExchangeHandshake();

            while (!socket.isClosed()) {
                int packetTypeCode = in.readInt();
                int payloadLength  = in.readInt();

                byte[] rawPayload = new byte[payloadLength];
                if (payloadLength > 0) in.readFully(rawPayload);

                PacketType type = PacketType.fromCode(packetTypeCode);
                String decryptedPayload;

                if (isHandshakeComplete
                        && type != PacketType.DISCONNECT
                        && type != PacketType.KEY_EXCHANGE_INIT) {
                    String encryptedBase64 = new String(rawPayload, StandardCharsets.UTF_8);
                    decryptedPayload = AesUtil.decrypt(encryptedBase64, aesSessionKey);
                } else {
                    decryptedPayload = new String(rawPayload, StandardCharsets.UTF_8);
                }

                handlePacket(type, decryptedPayload);
            }

        } catch (Exception e) {
            System.err.println("Обрив з'єднання або помилка: " + e.getMessage());
        } finally {
            disconnectCleanly();
        }
    }

    private void executeKeyExchangeHandshake() throws Exception {
        var serverKeyPair = RsaUtil.generateKeyPair();
        byte[] publicKeyBytes = serverKeyPair.getPublic().getEncoded();

        out.writeInt(PacketType.KEY_EXCHANGE_INIT.getCode());
        out.writeInt(publicKeyBytes.length);
        out.write(publicKeyBytes);
        out.flush();

        in.readInt();
        int encAesLength = in.readInt();
        byte[] encryptedAesKey = new byte[encAesLength];
        in.readFully(encryptedAesKey);

        byte[] decryptedKeyBytes = RsaUtil.decryptWithPrivateKey(encryptedAesKey, serverKeyPair.getPrivate());
        this.aesSessionKey = new SecretKeySpec(decryptedKeyBytes, 0, decryptedKeyBytes.length, "AES");
        this.isHandshakeComplete = true;
    }

    private void handlePacket(PacketType type, String payload) {
        switch (type) {
            case REGISTER_REQUEST       -> processRegistration(payload);
            case LOGIN_REQUEST          -> processLogin(payload);
            case SEND_MSG               -> processSendMessage(payload);
            case EDIT_MESSAGE_REQUEST   -> processEditMessage(payload);
            case DELETE_MESSAGE_REQUEST -> processDeleteMessage(payload);
            case CREATE_CHAT            -> handleCreateChat(payload);
            case ADMIN_ACTION           -> processAdminAction(payload);
            case GET_HISTORY            -> processGetHistory(payload);
            case GET_CONTACTS           -> processGetContacts();
            case PING                   -> sendPacket(PacketType.PING, "PONG");
            case DISCONNECT             -> disconnectCleanly();
            default                     -> sendPacket(PacketType.ERROR, "Невідомий тип пакета");
        }
    }

    private void processRegistration(String payload) {
        Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
        String login    = fields.getOrDefault("login", "");
        String password = fields.getOrDefault("password", "");

        if (login.isBlank() || password.isBlank()) {
            sendPacket(PacketType.REGISTER_FAILED, "Логін або пароль порожні");
            return;
        }
        if (userRepository.exists(login)) {
            sendPacket(PacketType.REGISTER_FAILED, "Логін зайнятий");
        } else {
            String hashed = PasswordUtil.hash(password);
            User newUser = new User(login, hashed, Role.USER);
            userRepository.save(newUser);
            authorizeUser(newUser);
            sendPacket(PacketType.REGISTER_SUCCESS, "Успіх");
        }
    }

    private void processLogin(String payload) {
        Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
        String login    = fields.getOrDefault("login", "");
        String password = fields.getOrDefault("password", "");

        User user = userRepository.findByLogin(login);

        if (user != null && PasswordUtil.verify(password, user.getPasswordHash())) {
            if (user.getStatus() == UserStatus.BANNED) {
                sendPacket(PacketType.LOGIN_FAILED, "Акаунт заблоковано");
                return;
            }
            authorizeUser(user);
            sendPacket(PacketType.LOGIN_SUCCESS, "Успіх");
        } else {
            sendPacket(PacketType.LOGIN_FAILED, "Невірний логін або пароль");
        }
    }

    private void authorizeUser(User user) {
        this.currentUserLogin = user.getLogin();
        this.currentUserRole  = user.getRole();
        userRepository.updateOnlineStatus(currentUserLogin, true);
        sessionRegistry.addSession(currentUserLogin, this);
        sessionRegistry.broadcastAll(PacketType.USER_STATUS_UPDATE, currentUserLogin + ":ONLINE");
    }

    private void processSendMessage(String payload) {
        Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
        String chatId = fields.getOrDefault("chatId", "");
        String text   = fields.getOrDefault("content", "");

        if (chatId.isBlank()) {
            sendPacket(PacketType.ERROR, "chatId не вказано");
            return;
        }
        if (text.isBlank() || text.length() > 4000) {
            sendPacket(PacketType.ERROR, "Некоректна довжина повідомлення");
            return;
        }

        Message message = new Message();
        message.setChatId(chatId);
        message.setSenderLogin(currentUserLogin);
        message.setEncryptedContent(text);
        message.setSentAt(Instant.now());
        messageRepository.save(message);

        Chat chat = chatRepository.findById(chatId);
        if (chat != null && chat.getMemberLogins() != null) {
            String notifyPayload = chatId + ":" + currentUserLogin + ":" + text;
            for (String member : chat.getMemberLogins()) {
                if (member.equals(currentUserLogin)) continue;
                ClientHandler target = sessionRegistry.getHandler(member);
                if (target != null) {
                    target.sendPacket(PacketType.RECEIVE_MSG, notifyPayload);
                }
            }
        }
    }

    private void processEditMessage(String payload) {
        Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
        String messageId = fields.getOrDefault("messageId", "").trim();
        String newText   = fields.getOrDefault("content", "");

        if (messageId.isBlank()) {
            sendPacket(PacketType.ERROR, "messageId не вказано");
            return;
        }
        if (newText.isBlank() || newText.length() > 4000) {
            sendPacket(PacketType.ERROR, "Некоректний вміст повідомлення");
            return;
        }

        Message msg = messageRepository.findById(messageId);
        if (msg == null) {
            sendPacket(PacketType.ERROR, "Повідомлення не знайдено");
            return;
        }
        if (!msg.getSenderLogin().equals(currentUserLogin)) {
            sendPacket(PacketType.ERROR, "Недостатньо прав для редагування");
            return;
        }

        messageRepository.updateContent(messageId, newText);
        sendPacket(PacketType.MESSAGE_EDITED, messageId + ":" + newText);

        Chat chat = chatRepository.findById(msg.getChatId());
        if (chat != null && chat.getMemberLogins() != null) {
            for (String member : chat.getMemberLogins()) {
                if (member.equals(currentUserLogin)) continue;
                ClientHandler target = sessionRegistry.getHandler(member);
                if (target != null) {
                    target.sendPacket(PacketType.MESSAGE_EDITED, messageId + ":" + newText);
                }
            }
        }
    }

    private void processDeleteMessage(String payload) {
        String messageId = payload.trim();
        Message msg = messageRepository.findById(messageId);
        if (msg == null) {
            sendPacket(PacketType.ERROR, "Повідомлення не знайдено");
            return;
        }

        boolean isAuthor = msg.getSenderLogin().equals(currentUserLogin);
        boolean isAdmin  = currentUserRole == Role.ADMIN;

        if (isAuthor || isAdmin) {
            messageRepository.deleteById(messageId);

            Chat chat = chatRepository.findById(msg.getChatId());
            if (chat != null && chat.getMemberLogins() != null) {
                for (String member : chat.getMemberLogins()) {
                    ClientHandler target = sessionRegistry.getHandler(member);
                    if (target != null) target.sendPacket(PacketType.MESSAGE_DELETED, messageId);
                }
            }
        } else {
            sendPacket(PacketType.ERROR, "Недостатньо прав для видалення");
        }
    }

    private void handleCreateChat(String payload) {
        String chatName;
        List<String> userIds;
        String typeFromPayload = "GROUP";

        if (payload.trim().startsWith("{")) {
            Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
            chatName       = fields.getOrDefault("name", "Новий чат");
            typeFromPayload = fields.getOrDefault("type", "GROUP");
            String members = fields.getOrDefault("members", "");
            userIds = new ArrayList<>();
            for (String m : members.split(",")) {
                String t = m.trim();
                if (!t.isEmpty()) userIds.add(t);
            }
        } else {
            String[] parts = payload.split(";", 2);
            if (parts.length < 2) return;
            chatName = parts[0];
            userIds = new ArrayList<>();
            for (String m : parts[1].split(",")) {
                String t = m.trim();
                if (!t.isEmpty()) userIds.add(t);
            }
        }

        if (!userIds.contains(currentUserLogin)) {
            userIds.add(0, currentUserLogin);
        }

        if (userIds.size() < 2) {
            sendPacket(PacketType.ERROR, "Неможливо створити чат без інших учасників");
            return;
        }

        Chat.Type chatType = (userIds.size() == 2 || "PRIVATE".equalsIgnoreCase(typeFromPayload))
                ? Chat.Type.PRIVATE
                : Chat.Type.GROUP;

        String newChatId = chatRepository.createNewChat(chatName, userIds, chatType);
        sendPacket(PacketType.CHAT_CREATED, newChatId + ":" + chatName);

        for (String member : userIds) {
            if (member.equals(currentUserLogin)) continue;
            ClientHandler target = sessionRegistry.getHandler(member);
            if (target != null) {
                target.sendPacket(PacketType.CHAT_CREATED, newChatId + ":" + chatName);
            }
        }
    }

    private void processAdminAction(String payload) {
        if (currentUserRole != Role.ADMIN) {
            sendPacket(PacketType.ERROR, "Недостатньо прав");
            return;
        }

        Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
        String command        = fields.getOrDefault("action", "");
        String targetUsername = fields.getOrDefault("target", "");

        if ("BAN_USER".equals(command)) {
            userRepository.updateStatus(targetUsername, UserStatus.BANNED);

            ClientHandler target = sessionRegistry.getHandler(targetUsername);
            if (target != null) {
                target.sendPacket(PacketType.BANNED_NOTIFICATION, "Ваш акаунт заблоковано");
                sessionRegistry.kickUser(targetUsername);
            }
            sendPacket(PacketType.ADMIN_SUCCESS, "Користувача заблоковано");
            sessionRegistry.broadcastAll(PacketType.USER_LIST_UPDATED, targetUsername + ":BANNED");

        } else if ("DELETE_USER".equals(command)) {
            if (targetUsername.equals(currentUserLogin)) {
                sendPacket(PacketType.ERROR, "Неможливо видалити власний акаунт");
                return;
            }

            userRepository.deleteByLogin(targetUsername);
            chatRepository.removeMemberFromAllChats(targetUsername);

            ClientHandler target = sessionRegistry.getHandler(targetUsername);
            if (target != null) {
                target.sendPacket(PacketType.BANNED_NOTIFICATION, "Ваш акаунт видалено");
                sessionRegistry.kickUser(targetUsername);
            }
            sendPacket(PacketType.ADMIN_SUCCESS, "Користувача видалено");
            sessionRegistry.broadcastAll(PacketType.USER_LIST_UPDATED, targetUsername + ":DELETED");

        } else if ("SERVER_STATS".equals(command)) {
            int activeCount = sessionRegistry.getActiveCount();
            sendPacket(PacketType.ADMIN_SUCCESS, "Активних підключень: " + activeCount);

        } else {
            sendPacket(PacketType.ERROR, "Невідома адмін-команда: " + command);
        }
    }

    private void processGetHistory(String payload) {
        Map<String, String> fields = PayloadBuilder.parse(payload.getBytes(StandardCharsets.UTF_8));
        String chatId = fields.getOrDefault("chatId", "");

        if (chatId.isBlank()) {
            sendPacket(PacketType.ERROR, "chatId не вказано");
            return;
        }

        Chat chat = chatRepository.findById(chatId);
        if (chat == null || !chat.getMemberLogins().contains(currentUserLogin)) {
            sendPacket(PacketType.ERROR, "Немає доступу до цього чату");
            return;
        }

        List<Message> messages = messageRepository.findByChatId(chatId);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                    .append("\"id\":\"").append(m.getId()).append("\",")
                    .append("\"sender\":\"").append(m.getSenderLogin()).append("\",")
                    .append("\"text\":\"").append(escapeJson(m.getEncryptedContent())).append("\",")
                    .append("\"sentAt\":").append(m.getSentAt().toEpochMilli())
                    .append("}");
        }
        sb.append("]");

        sendPacket(PacketType.GET_HISTORY, chatId + ":" + sb);
    }

    private void processGetContacts() {
        if (currentUserLogin == null) {
            sendPacket(PacketType.ERROR, "Не авторизовано");
            return;
        }

        List<Chat> chats = chatRepository.findByMember(currentUserLogin);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < chats.size(); i++) {
            Chat c = chats.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                    .append("\"id\":\"").append(c.getId()).append("\",")
                    .append("\"name\":\"").append(escapeJson(c.getName())).append("\",")
                    .append("\"type\":\"").append(c.getType()).append("\"")
                    .append("}");
        }
        sb.append("]");

        sendPacket(PacketType.GET_CONTACTS, sb.toString());
    }

    public void disconnectCleanly() {
        if (!disconnected) {
            synchronized (this) {
                if (disconnected) return;
                disconnected = true;
            }
            try {
                if (currentUserLogin != null) {
                    userRepository.updateOnlineStatus(currentUserLogin, false);
                    sessionRegistry.removeSession(currentUserLogin);
                    sessionRegistry.broadcastAll(PacketType.USER_STATUS_UPDATE, currentUserLogin + ":OFFLINE");
                }
                if (in  != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("Помилка закриття: " + e.getMessage());
            }
        }
    }

    public synchronized void sendPacket(PacketType type, String plainPayload) {
        try {
            byte[] payloadToSend;

            if (isHandshakeComplete && type != PacketType.KEY_EXCHANGE_INIT) {
                String encryptedBase64 = AesUtil.encrypt(plainPayload, aesSessionKey);
                payloadToSend = encryptedBase64.getBytes(StandardCharsets.UTF_8);
            } else {
                payloadToSend = plainPayload.getBytes(StandardCharsets.UTF_8);
            }

            out.writeInt(type.getCode());
            out.writeInt(payloadToSend.length);
            out.write(payloadToSend);
            out.flush();
        } catch (Exception e) {
            System.err.println("Помилка відправки: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
