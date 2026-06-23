package org.example.server.network;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 50;

    public static void main(String[] args) {
        SessionRegistry registry = new SessionRegistry();
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        System.out.println("[Server] ASAP Messenger started on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New connection: " + clientSocket.getInetAddress());
                pool.execute(new ClientHandler(clientSocket, registry));
            }
        } catch (Exception e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}
