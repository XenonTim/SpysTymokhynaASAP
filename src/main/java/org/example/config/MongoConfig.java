package org.example.config;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoConfig {
    private static MongoClient mongoClient = null;
    private static MongoDatabase database = null;

    public static MongoDatabase getDatabase() {
        if (database == null) {
            try {
                String connectionString = "mongodb+srv://admin:admin@cluster0.fvjkz2r.mongodb.net/?appName=Cluster0";
                mongoClient = MongoClients.create(connectionString);
                database = mongoClient.getDatabase("ASAP");
                System.out.println("Успішно підключено до хмарної MongoDB Atlas!");
            } catch (Exception e) {
                System.err.println("Помилка підключення до MongoDB Atlas: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return database;
    }
}