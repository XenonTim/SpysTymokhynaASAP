package org.example.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.example.models.User;

public class UserRepository {

    private final MongoCollection<Document> collection;

    public UserRepository() {
        MongoDatabase db = MongoDBConnection.getDatabase();
        this.collection = db.getCollection("users");
    }

    public boolean existsByLogin(String login) {
        return collection.find(Filters.eq("login", login)).first() != null;
    }

    public void save(User user) {
        Document doc = new Document()
                .append("login", user.getLogin())
                .append("passwordHash", user.getPasswordHash())
                .append("role", user.getRole().name())
                .append("status", user.getStatus().name())
                .append("online", false);
        collection.insertOne(doc);
        user.setId(doc.getObjectId("_id").toHexString());
    }

    public User findByLogin(String login) {
        Document doc = collection.find(Filters.eq("login", login)).first();
        return doc != null ? docToUser(doc) : null;
    }

    public void setOnline(String login, boolean online) {
        collection.updateOne(
                Filters.eq("login", login),
                Updates.set("online", online)
        );
    }

    public void setStatus(String login, User.Status status) {
        collection.updateOne(
                Filters.eq("login", login),
                Updates.set("status", status.name())
        );
    }

    public void delete(String login) {
        collection.deleteOne(Filters.eq("login", login));
    }

    private User docToUser(Document doc) {
        User user = new User();
        user.setId(doc.getObjectId("_id").toHexString());
        user.setLogin(doc.getString("login"));
        user.setPasswordHash(doc.getString("passwordHash"));
        user.setRole(User.Role.valueOf(doc.getString("role")));
        user.setStatus(User.Status.valueOf(doc.getString("status")));
        user.setOnline(Boolean.TRUE.equals(doc.getBoolean("online")));
        return user;
    }
}
