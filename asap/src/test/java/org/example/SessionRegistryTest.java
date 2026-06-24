package org.example;

import org.example.server.network.ClientHandler;
import org.example.server.network.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    @Test
    void registerAndFind() {
        registry.register("kate", fakeHandler());
        assertTrue(registry.isOnline("kate"));
    }

    @Test
    void unregisterRemovesUser() {
        registry.register("kate", fakeHandler());
        registry.unregister("kate");
        assertFalse(registry.isOnline("kate"));
    }

    @Test
    void activeCountIsCorrect() {
        registry.register("kate", fakeHandler());
        registry.register("kseniia", fakeHandler());
        assertEquals(2, registry.getActiveCount());
        registry.unregister("kate");
        assertEquals(1, registry.getActiveCount());
    }

    @Test
    void getHandlerReturnsRegisteredHandler() {
        ClientHandler handler = fakeHandler();
        registry.register("kate", handler);
        assertSame(handler, registry.getHandler("kate"));
    }

    @Test
    void getHandlerReturnsNullAfterUnregister() {
        registry.register("kate", fakeHandler());
        registry.unregister("kate");
        assertNull(registry.getHandler("kate"));
    }

    @Test
    void unknownUserIsNotOnline() {
        assertFalse(registry.isOnline("nobody"));
    }

    @Test
    void doubleRegisterOverwritesHandler() {
        ClientHandler first  = fakeHandler();
        ClientHandler second = fakeHandler();
        registry.register("kate", first);
        registry.register("kate", second);
        assertSame(second, registry.getHandler("kate"));
        assertEquals(1, registry.getActiveCount());
    }

    @Test
    void issuedSessionTokenIsValidForThatLoginOnly() {
        String token = registry.issueSessionToken("kate");
        assertTrue(registry.isValidSessionToken("kate", token));
        assertFalse(registry.isValidSessionToken("kseniia", token));
    }

    @Test
    void wrongOrEmptySessionTokenIsRejected() {
        registry.issueSessionToken("kate");
        assertFalse(registry.isValidSessionToken("kate", "totally-wrong-token"));
        assertFalse(registry.isValidSessionToken("kate", ""));
        assertFalse(registry.isValidSessionToken("kate", null));
    }

    @Test
    void reissuingTokenInvalidatesThePreviousOne() {
        String firstToken = registry.issueSessionToken("kate");
        String secondToken = registry.issueSessionToken("kate");
        assertFalse(registry.isValidSessionToken("kate", firstToken));
        assertTrue(registry.isValidSessionToken("kate", secondToken));
    }

    @Test
    void revokedSessionTokenIsNoLongerValid() {
        String token = registry.issueSessionToken("kate");
        registry.revokeSessionToken("kate");
        assertFalse(registry.isValidSessionToken("kate", token));
    }

    @Test
    void unknownLoginNeverHasAValidToken() {
        assertFalse(registry.isValidSessionToken("nobody", "anything"));
    }

    private ClientHandler fakeHandler() {
        try {
            ServerSocket ss = new ServerSocket(0);
            Socket socket = new Socket("localhost", ss.getLocalPort());
            ClientHandler handler = new ClientHandler(socket, new SessionRegistry());
            ss.close();
            return handler;
        } catch (Exception e) {
            throw new RuntimeException("Could not create fake ClientHandler", e);
        }
    }
}
