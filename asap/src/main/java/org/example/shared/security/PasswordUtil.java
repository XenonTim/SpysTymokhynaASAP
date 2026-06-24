package org.example.shared.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {

    private static final int WORK_FACTOR = 12;

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
