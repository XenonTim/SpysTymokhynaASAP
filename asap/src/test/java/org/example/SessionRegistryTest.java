package org.example;

import org.example.server.network.ClientHandler;
import org.example.server.network.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        registry.addSession("kate", fakeHandler());
        assertTrue(registry.isUserOnline("kate"));
    }

    @Test
    void unregisterRemovesUser() {
        registry.addSession("kate", fakeHandler());
        registry.removeSession("kate");
        assertFalse(registry.isUserOnline("kate"));
    }

    @Test
    void activeCountIsCorrect() {
        registry.addSession("kate", fakeHandler());
        registry.addSession("kseniia", fakeHandler());
        assertEquals(2, registry.getActiveCount());
        registry.removeSession("kate");
        assertEquals(1, registry.getActiveCount());
    }

    @Test
    void getHandlerReturnsRegisteredHandler() {
        ClientHandler handler = fakeHandler();
        registry.addSession("kate", handler);
        assertSame(handler, registry.getHandler("kate"));
    }

    @Test
    void getHandlerReturnsNullAfterUnregister() {
        registry.addSession("kate", fakeHandler());
        registry.removeSession("kate");
        assertNull(registry.getHandler("kate"));
    }

    @Test
    void unknownUserIsNotOnline() {
        assertFalse(registry.isUserOnline("nobody"));
    }

    @Test
    void doubleRegisterOverwritesHandler() {
        ClientHandler first  = fakeHandler();
        ClientHandler second = fakeHandler();
        registry.addSession("kate", first);
        registry.addSession("kate", second);
        assertSame(second, registry.getHandler("kate"));
        assertEquals(1, registry.getActiveCount());
    }

    // Оновлений фейковий обробник для тестів
    private ClientHandler fakeHandler() {
        try {
            ServerSocket ss = new ServerSocket(0);
            Socket socket = new Socket("localhost", ss.getLocalPort());
            // Передаємо null замість репозиторіїв БД, оскільки цей тест їх не використовує
            ClientHandler handler = new ClientHandler(socket, new SessionRegistry(), null, null, null);
            ss.close();
            return handler;
        } catch (Exception e) {
            throw new RuntimeException("Could not create fake ClientHandler", e);
        }
    }
}