package org.example.models;

public class User {

    public enum Role { USER, ADMIN }
    public enum Status { ACTIVE, BANNED }

    private String id;
    private String login;
    private String passwordHash;
    private Role role;
    private Status status;
    private boolean online;

    public User() {}

    public User(String login, String passwordHash, Role role) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = Status.ACTIVE;
        this.online = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public boolean getIsOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
