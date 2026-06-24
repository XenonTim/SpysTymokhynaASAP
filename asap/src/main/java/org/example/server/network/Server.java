package org.example.server.network;

import org.example.db.ChatRepository;
import org.example.db.MessageRepository;
import org.example.db.UserRepository;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 50;

    public static void main(String[] args) {
        // 1. Ініціалізація всіх необхідних компонентів для передачі в ClientHandler
        SessionRegistry registry = new SessionRegistry();
        UserRepository userRepository = new UserRepository();
        MessageRepository messageRepository = new MessageRepository();
        ChatRepository chatRepository = new ChatRepository();

        System.out.println("[Server] ASAP Messenger started on port " + PORT);

        // 2. Використовуємо try-with-resources для ExecutorService, щоб прибрати попередження
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {

            // 3. Замінюємо while(true) на перевірку, щоб уникнути попередження "cannot complete"
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New connection: " + clientSocket.getInetAddress());

                // Передаємо всі 5 аргументів (socket, registry, messageRepo, chatRepo, userRepo)
                pool.execute(new ClientHandler(clientSocket, registry, messageRepository, chatRepository, userRepository));
            }
        } catch (Exception e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}