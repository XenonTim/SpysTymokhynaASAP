package org.example.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.example.models.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MessageRepository {

    private static final int HISTORY_LIMIT = 50;

    private final MongoCollection<Document> collection;

    public MessageRepository() {
        MongoDatabase db = MongoDBConnection.getDatabase();
        this.collection = db.getCollection("messages");
    }

    public void save(Message message) {
        Document doc = new Document()
                .append("chatId", message.getChatId())
                .append("senderLogin", message.getSenderLogin())
                .append("encryptedContent", message.getEncryptedContent())
                .append("sentAt", message.getSentAt().toEpochMilli())
                .append("deleted", false);
        collection.insertOne(doc);
        message.setId(doc.getObjectId("_id").toHexString());
    }

    public List<Message> findByChatId(String chatId) {
        List<Message> messages = new ArrayList<>();
        collection
                .find(Filters.and(
                        Filters.eq("chatId", chatId),
                        Filters.eq("deleted", false)
                ))
                .sort(Sorts.ascending("sentAt"))
                .limit(HISTORY_LIMIT)
                .forEach(doc -> messages.add(docToMessage(doc)));
        return messages;
    }

    public void markDeleted(String messageId) {
        collection.updateOne(
                Filters.eq("_id", new org.bson.types.ObjectId(messageId)),
                Updates.set("deleted", true)
        );
    }

    public void updateMessageText(String messageId, String newContent) {
        collection.updateOne(
                Filters.eq("_id", new org.bson.types.ObjectId(messageId)),
                Updates.set("encryptedContent", newContent)
        );
    }

    public Message findById(String messageId) {
        Document doc = collection.find(Filters.eq("_id", new org.bson.types.ObjectId(messageId))).first();
        if (doc == null) return null;

        Message msg = new Message();
        msg.setId(doc.getObjectId("_id").toHexString());
        msg.setChatId(doc.getString("chatId"));
        msg.setSenderLogin(doc.getString("senderLogin"));
        msg.setEncryptedContent(doc.getString("encryptedContent"));
        Long btime = doc.getLong("sentAt");
        if (btime != null) {
            msg.setSentAt(Instant.ofEpochMilli(btime));
        } else msg.setSentAt(Instant.now());
        return msg;
    }

    private Message docToMessage(Document doc) {
        Message msg = new Message();
        msg.setId(doc.getObjectId("_id").toHexString());
        msg.setChatId(doc.getString("chatId"));
        msg.setSenderLogin(doc.getString("senderLogin"));
        msg.setEncryptedContent(doc.getString("encryptedContent"));
        msg.setSentAt(Instant.ofEpochMilli(doc.getLong("sentAt")));
        msg.setDeleted(Boolean.TRUE.equals(doc.getBoolean("deleted")));
        return msg;
    }
}
