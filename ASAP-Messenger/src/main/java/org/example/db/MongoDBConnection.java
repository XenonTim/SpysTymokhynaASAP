package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * Єдина точка підключення до MongoDB.
 *
 * URI бази береться (у порядку пріоритету):
 *   1. JVM system property -DMONGO_URI=...
 *   2. Змінна середовища MONGO_URI
 *   3. Локальний MongoDB за замовчуванням (mongodb://localhost:27017)
 *
 * Для MongoDB Atlas задайте рядок підключення з вашого кластера, наприклад:
 *   mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority
 *
 * Назву бази даних також можна перевизначити через MONGO_DB_NAME,
 * інакше використовується "asap_messenger".
 */
public class MongoDBConnection {

    private static final String DEFAULT_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_DB_NAME = "asap_messenger";

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static MongoDatabase getDatabase() {
        if (mongoClient == null) {
            String uri = resolveUri();
            String dbName = resolveDbName();
            mongoClient = MongoClients.create(uri);
            database = mongoClient.getDatabase(dbName);
            System.out.println("[Mongo] Підключено до бази: " + dbName);
        }
        return database;
    }

    private static String resolveUri() {
        String fromProp = System.getProperty("MONGO_URI");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;

        String fromEnv = System.getenv("MONGO_URI");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        return DEFAULT_URI;
    }

    private static String resolveDbName() {
        String fromProp = System.getProperty("MONGO_DB_NAME");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;

        String fromEnv = System.getenv("MONGO_DB_NAME");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        return DEFAULT_DB_NAME;
    }

    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            database = null;
        }
    }
}
