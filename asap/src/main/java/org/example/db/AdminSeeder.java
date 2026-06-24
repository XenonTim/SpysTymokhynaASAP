package org.example.db;

import org.example.models.Role; // Додано імпорт Role
import org.example.models.User;
import org.example.shared.security.PasswordUtil;

public class AdminSeeder {

    public static void main(String[] args) {
        String adminLogin    = "admin";
        String adminPassword = "Admin1234!";

        UserRepository userRepo = new UserRepository();

        // Виправлено назву методу на exists()
        if (userRepo.exists(adminLogin)) {
            System.out.println("[Seeder] Admin '" + adminLogin + "' already exists — skipping.");
            MongoDBConnection.close();
            return;
        }

        String hash = PasswordUtil.hash(adminPassword);

        // Виправлено User.Role.ADMIN на Role.ADMIN
        User admin = new User(adminLogin, hash, Role.ADMIN);
        userRepo.save(admin);

        System.out.println("[Seeder] Admin account created:");
        System.out.println("         login:    " + adminLogin);
        System.out.println("         password: " + adminPassword);
        System.out.println("         role:     ADMIN");

        // Тепер виклик getId() спрацює після оновлення моделі
        System.out.println("         id:       " + admin.getId());

        MongoDBConnection.close();
    }
}