package org.example;

import org.example.shared.security.AesUtil;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

class AesUtilTest {

    @Test
    void encryptDecryptRoundtrip() throws Exception {
        SecretKey key = AesUtil.generateKey();
        String original = "Hello, ASAP!";
        String encrypted = AesUtil.encrypt(original, key);
        String decrypted = AesUtil.decrypt(encrypted, key);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptedTextDiffersFromPlain() throws Exception {
        SecretKey key = AesUtil.generateKey();
        String original = "secret message";
        assertNotEquals(original, AesUtil.encrypt(original, key));
    }

    @Test
    void samePlaintextGivesDifferentCiphertext() throws Exception {
        SecretKey key = AesUtil.generateKey();
        String plain = "test";
        assertNotEquals(AesUtil.encrypt(plain, key), AesUtil.encrypt(plain, key));
    }

    @Test
    void wrongKeyCannotDecrypt() throws Exception {
        SecretKey key1 = AesUtil.generateKey();
        SecretKey key2 = AesUtil.generateKey();
        String encrypted = AesUtil.encrypt("message", key1);
        assertThrows(Exception.class, () -> AesUtil.decrypt(encrypted, key2));
    }
}
