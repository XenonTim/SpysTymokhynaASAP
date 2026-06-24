package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.github.cdimascio.dotenv.Dotenv;

public class MongoDBConnection {

    private static final String DEFAULT_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_DB_NAME = "asap_messenger";

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static MongoDatabase getDatabase() {
        if (mongoClient == null) {
            Dotenv.configure().ignoreIfMissing().systemProperties().load();
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
