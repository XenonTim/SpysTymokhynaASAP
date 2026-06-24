package org.example;

import org.example.shared.security.AesUtil;
import org.example.shared.security.RsaUtil;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class HybridEncryptionTest {

    @Test
    void aesKeyWrappedWithRsaCanBeUnwrappedByOwner() throws Exception {
        KeyPair recipientKeyPair = RsaUtil.generateKeyPair();
        SecretKey chatKey = AesUtil.generateKey();

        byte[] wrapped = RsaUtil.encryptWithPublicKey(chatKey.getEncoded(), recipientKeyPair.getPublic());
        byte[] unwrapped = RsaUtil.decryptWithPrivateKey(wrapped, recipientKeyPair.getPrivate());

        assertArrayEquals(chatKey.getEncoded(), unwrapped);
    }

    @Test
    void messageEncryptedWithUnwrappedKeyDecryptsToOriginalText() throws Exception {
        KeyPair recipientKeyPair = RsaUtil.generateKeyPair();
        SecretKey chatKey = AesUtil.generateKey();

        byte[] wrapped = RsaUtil.encryptWithPublicKey(chatKey.getEncoded(), recipientKeyPair.getPublic());
        byte[] unwrapped = RsaUtil.decryptWithPrivateKey(wrapped, recipientKeyPair.getPrivate());
        SecretKey recoveredKey = AesUtil.keyFromBytes(unwrapped);

        String plainText = "Привіт! Це приватне повідомлення.";
        String onWire = AesUtil.encrypt(plainText, chatKey);
        String decrypted = AesUtil.decrypt(onWire, recoveredKey);

        assertEquals(plainText, decrypted);
    }

    @Test
    void serverWithoutPrivateKeyCannotRecoverChatKey() throws Exception {

        KeyPair recipientKeyPair = RsaUtil.generateKeyPair();
        SecretKey chatKey = AesUtil.generateKey();
        byte[] wrapped = RsaUtil.encryptWithPublicKey(chatKey.getEncoded(), recipientKeyPair.getPublic());

        KeyPair someUnrelatedKeyPair = RsaUtil.generateKeyPair();
        assertThrows(Exception.class, () ->
                RsaUtil.decryptWithPrivateKey(wrapped, someUnrelatedKeyPair.getPrivate()));
    }

    @Test
    void onWireCiphertextNeverContainsPlainText() throws Exception {
        SecretKey chatKey = AesUtil.generateKey();
        String plainText = "secret-marker-12345";
        String onWire = AesUtil.encrypt(plainText, chatKey);

        assertFalse(onWire.contains(plainText));
    }

    @Test
    void differentChatsUseIndependentKeys() throws Exception {
        SecretKey keyForChatA = AesUtil.generateKey();
        SecretKey keyForChatB = AesUtil.generateKey();
        String message = "same text in two different chats";

        String encryptedForA = AesUtil.encrypt(message, keyForChatA);

        assertThrows(Exception.class, () -> AesUtil.decrypt(encryptedForA, keyForChatB));
    }
}
