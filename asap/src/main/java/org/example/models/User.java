package org.example.models;

public class User {
    private String id;
    private String login;
    private String passwordHash;
    private Role role;
    private UserStatus status;

    // Порожній конструктор
    public User() {
    }

    // Конструктор з 3 параметрами (який використовується в UserRepository та ClientHandler)
    public User(String login, String passwordHash, Role role) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE; // За замовчуванням користувач активний
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}