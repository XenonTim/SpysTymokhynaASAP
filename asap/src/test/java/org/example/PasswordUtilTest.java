package org.example;

import org.example.shared.security.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void hashIsNotPlainText() {
        String plain = "secret123";
        String hash = PasswordUtil.hash(plain);
        assertNotEquals(plain, hash);
    }

    @Test
    void verifyCorrectPassword() {
        String plain = "myPassword!";
        String hash = PasswordUtil.hash(plain);
        assertTrue(PasswordUtil.verify(plain, hash));
    }

    @Test
    void rejectWrongPassword() {
        String hash = PasswordUtil.hash("correct");
        assertFalse(PasswordUtil.verify("wrong", hash));
    }

    @Test
    void twoHashesDiffer() {
        String plain = "samePassword";
        assertNotEquals(PasswordUtil.hash(plain), PasswordUtil.hash(plain));
    }
}
