package org.example.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.example.models.Chat;

import java.util.ArrayList;
import java.util.List;

public class ChatRepository {

    private final MongoCollection<Document> collection;

    public ChatRepository() {
        MongoDatabase db = MongoDBConnection.getDatabase();
        this.collection = db.getCollection("chats");
    }

    public void save(Chat chat) {
        Document doc = new Document()
                .append("type", chat.getType().name())
                .append("name", chat.getName())
                .append("memberLogins", chat.getMemberLogins());
        collection.insertOne(doc);
        chat.setId(doc.getObjectId("_id").toHexString());
    }

    public Chat findById(String chatId) {
        Document doc = collection.find(
                Filters.eq("_id", new org.bson.types.ObjectId(chatId))
        ).first();
        return doc != null ? docToChat(doc) : null;
    }

    public List<Chat> findByMember(String login) {
        List<Chat> chats = new ArrayList<>();
        collection
                .find(Filters.in("memberLogins", login))
                .forEach(doc -> chats.add(docToChat(doc)));
        return chats;
    }

    public Chat findPrivateChat(String loginA, String loginB) {
        return collection
                .find(Filters.and(
                        Filters.eq("type", Chat.Type.PRIVATE.name()),
                        Filters.all("memberLogins", loginA, loginB)
                ))
                .map(this::docToChat)
                .first();
    }

    private Chat docToChat(Document doc) {
        Chat chat = new Chat();
        chat.setId(doc.getObjectId("_id").toHexString());
        chat.setType(Chat.Type.valueOf(doc.getString("type")));
        chat.setName(doc.getString("name"));
        chat.setMemberLogins(doc.getList("memberLogins", String.class));
        return chat;
    }
}
