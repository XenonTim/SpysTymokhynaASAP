package org.example.models;

import java.time.Instant;

public class Message {

    private String id;
    private String chatId;
    private String senderLogin;
    private String encryptedContent;
    private Instant sentAt;
    private boolean deleted;

    public Message() {}

    public Message(String chatId, String senderLogin, String encryptedContent) {
        this.chatId = chatId;
        this.senderLogin = senderLogin;
        this.encryptedContent = encryptedContent;
        this.sentAt = Instant.now();
        this.deleted = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getSenderLogin() { return senderLogin; }
    public void setSenderLogin(String senderLogin) { this.senderLogin = senderLogin; }

    public String getEncryptedContent() { return encryptedContent; }
    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
