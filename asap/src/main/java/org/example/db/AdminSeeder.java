package org.example.db;

import org.example.models.User;
import org.example.shared.security.PasswordUtil;

public class AdminSeeder {

    public static void main(String[] args) {
        String adminLogin    = "admin";
        String adminPassword = "Admin1234!";

        UserRepository userRepo = new UserRepository();

        if (userRepo.existsByLogin(adminLogin)) {
            System.out.println("[Seeder] Admin '" + adminLogin + "' already exists — skipping.");
            MongoDBConnection.close();
            return;
        }

        String hash = PasswordUtil.hash(adminPassword);
        User admin = new User(adminLogin, hash, User.Role.ADMIN);
        userRepo.save(admin);

        System.out.println("[Seeder] Admin account created:");
        System.out.println("         login:    " + adminLogin);
        System.out.println("         password: " + adminPassword);
        System.out.println("         role:     ADMIN");
        System.out.println("         id:       " + admin.getId());

        MongoDBConnection.close();
    }
}
