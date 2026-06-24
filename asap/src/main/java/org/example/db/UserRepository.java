package org.example.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.UserStatus;

public class UserRepository {

    private final MongoCollection<Document> collection;

    // Правильний конструктор, який підключається до вашої бази
    public UserRepository() {
        MongoDatabase db = MongoDBConnection.getDatabase();
        this.collection = db.getCollection("users");
    }

    // --- ВАШІ БАЗОВІ МЕТОДИ ---

    public boolean exists(String login) {
        return collection.find(Filters.eq("login", login)).first() != null;
    }

    public void save(User user) {
        Document doc = new Document("login", user.getLogin())
                .append("passwordHash", user.getPasswordHash())
                .append("role", user.getRole().name())
                .append("status", user.getStatus() != null ? user.getStatus().name() : UserStatus.ACTIVE.name())
                .append("isOnline", false);
        collection.insertOne(doc);
    }

    public User findByLogin(String login) {
        Document doc = collection.find(Filters.eq("login", login)).first();
        if (doc == null) return null;

        User user = new User(
                doc.getString("login"),
                doc.getString("passwordHash"),
                Role.valueOf(doc.getString("role"))
        );

        if (doc.containsKey("status")) {
            user.setStatus(UserStatus.valueOf(doc.getString("status")));
        }
        return user;
    }

    // --- НОВІ МЕТОДИ ЗГІДНО З ТЗ ---

    // Переведення статусу користувача в OFFLINE/ONLINE у БД
    public void updateOnlineStatus(String login, boolean isOnline) {
        collection.updateOne(
                Filters.eq("login", login),
                Updates.set("isOnline", isOnline)
        );
    }

    // Оновлення статусу користувача (наприклад, для бану)
    public void updateStatus(String login, UserStatus status) {
        collection.updateOne(
                Filters.eq("login", login),
                Updates.set("status", status.name())
        );
    }

    // Повне видалення користувача з бази (для команди адміна DELETE_USER)
    public void deleteByLogin(String login) {
        collection.deleteOne(Filters.eq("login", login));
    }
}