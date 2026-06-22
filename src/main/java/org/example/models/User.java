package org.example.models;

import org.bson.types.ObjectId;

public class User {
    private ObjectId id;
    private String username;
    private String email;
    private String passwordHash;
    private boolean isOnline;

    public User(ObjectId id, String username, String email, String passwordHash, boolean isOnline) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.isOnline = isOnline;
    }

    public ObjectId getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean getIsOnline() { return isOnline; }

    public ObjectId setId(ObjectId id) { this.id = id; return id; }
    public String setUsername(String username) { this.username = username; return username; }
    public String setEmail(String email) { this.email = email; return email; }
    public String setPasswordHash(String passwordHash) {this.passwordHash = passwordHash; return passwordHash; }
    public boolean setIsOnline(boolean isOnline) { this.isOnline = isOnline; return isOnline; }
}

