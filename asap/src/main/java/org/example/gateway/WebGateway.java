package org.example.gateway;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebGateway {

    private static final int HTTP_PORT = 8081;
    private static final String WEBAPP_DIR = "webapp";

    public static void main(String[] args) throws IOException {
        int port = HTTP_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[WebGateway] Запущено на http://localhost:" + port);
            System.out.println("[WebGateway] Очікую підключення ASAP Server на localhost:8080 ...");

            while (true) {
                Socket socket = serverSocket.accept();
                pool.execute(() -> handleConnection(socket));
            }
        } finally {
            pool.shutdown();
        }
    }

    private static void handleConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                socket.close();
                return;
            }

            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts.length > 1 ? requestParts[1] : "/";

            String webSocketKey = null;
            boolean isUpgrade = false;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("upgrade:") && lower.contains("websocket")) {
                    isUpgrade = true;
                } else if (lower.startsWith("sec-websocket-key:")) {
                    webSocketKey = line.substring(line.indexOf(':') + 1).trim();
                }
            }

            if (isUpgrade && webSocketKey != null && path.equals("/ws")) {
                WebSocketConnection ws = WebSocketConnection.upgrade(socket, webSocketKey);
                new ClientSessionBridge(ws).start();
                return;
            }

            if ("GET".equals(method)) {
                serveStaticFile(socket, path);
            } else {
                writeSimpleResponse(socket, 405, "text/plain", "Method Not Allowed");
            }
        } catch (Exception e) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static void serveStaticFile(Socket socket, String requestPath) throws IOException {
        String decodedPath = URLDecoder.decode(requestPath, StandardCharsets.UTF_8);
        if (decodedPath.equals("/")) decodedPath = "/login.html";

        Path base = Path.of(WEBAPP_DIR).toAbsolutePath().normalize();
        Path file = base.resolve(decodedPath.substring(1)).normalize();

        if (!file.startsWith(base) || !Files.exists(file) || Files.isDirectory(file)) {
            writeSimpleResponse(socket, 404, "text/plain", "Not found: " + decodedPath);
            return;
        }

        byte[] content = Files.readAllBytes(file);
        String contentType = guessContentType(file.toString());

        OutputStream out = socket.getOutputStream();
        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.flush();
        socket.close();
    }

    private static String guessContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html; charset=utf-8";
        if (fileName.endsWith(".css")) return "text/css; charset=utf-8";
        if (fileName.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void writeSimpleResponse(Socket socket, int code, String contentType, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 " + code + " " + statusText(code) + "\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.flush();
            socket.close();
        } catch (IOException ignored) {}
    }

    private static String statusText(int code) {
        return switch (code) {
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Error";
        };
    }
}
