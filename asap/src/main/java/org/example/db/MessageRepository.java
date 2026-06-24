package org.example.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.example.models.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MessageRepository {

    private static final int HISTORY_LIMIT = 50;
    private final MongoCollection<Document> collection;

    public MessageRepository() {
        MongoDatabase db = MongoDBConnection.getDatabase();
        this.collection = db.getCollection("messages");
    }

    // --- ВАШІ ОРИГІНАЛЬНІ МЕТОДИ ---

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
        // Отримуємо останні 50 (сортуємо за спаданням)
        collection
                .find(Filters.and(
                        Filters.eq("chatId", chatId),
                        Filters.eq("deleted", false)
                ))
                .sort(Sorts.descending("sentAt"))
                .limit(HISTORY_LIMIT)
                .forEach(doc -> messages.add(docToMessage(doc)));
        // Реверсуємо, щоб історія йшла від старіших до новіших
        java.util.Collections.reverse(messages);
        return messages;
    }

    public void markDeleted(String messageId) {
        collection.updateOne(
                Filters.eq("_id", new ObjectId(messageId)),
                Updates.set("deleted", true)
        );
    }

    // --- НОВІ МЕТОДИ ЗГІДНО З ТЗ ---

    // Пошук повідомлення за його ID (використовує ваш існуючий мапінг)
    public Message findById(String id) {
        try {
            Document doc = collection.find(Filters.eq("_id", new ObjectId(id))).first();
            if (doc == null) return null;
            return docToMessage(doc); // Використовуємо ваш метод для правильного створення об'єкта
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Оновлення тексту повідомлення з додаванням прапорця edited
    public void updateContent(String messageId, String newText) {
        collection.updateOne(
                Filters.eq("_id", new ObjectId(messageId)),
                Updates.combine(
                        Updates.set("encryptedContent", newText), // Використовуємо ВАШУ назву поля
                        Updates.set("edited", true)
                )
        );
    }

    // Повне видалення повідомлення з бази даних
    public void deleteById(String messageId) {
        collection.deleteOne(Filters.eq("_id", new ObjectId(messageId)));
    }

    // --- ДОПОМІЖНІ МЕТОДИ ---

    private Message docToMessage(Document doc) {
        Message msg = new Message();
        msg.setId(doc.getObjectId("_id").toHexString());
        msg.setChatId(doc.getString("chatId"));
        msg.setSenderLogin(doc.getString("senderLogin"));
        msg.setEncryptedContent(doc.getString("encryptedContent"));
        msg.setSentAt(Instant.ofEpochMilli(doc.getLong("sentAt")));
        msg.setDeleted(Boolean.TRUE.equals(doc.getBoolean("deleted")));

        // Якщо у вашій моделі Message є поле edited та setter для нього,
        // можете розкоментувати наступні рядки:
        // if (doc.containsKey("edited")) {
        //     msg.setEdited(doc.getBoolean("edited"));
        // }

        return msg;
    }
}