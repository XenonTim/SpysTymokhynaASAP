package org.example.client;

import org.example.client.network.ServerConnection;
import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketType;
import org.example.shared.protocol.PayloadBuilder;

import java.util.Scanner;

public class TestClient {

    private static ServerConnection connection;
    private static String currentLogin;

    public static void main(String[] args) throws Exception {
        connection = new ServerConnection();
        connection.connect(TestClient::onPacket, TestClient::onDisconnect);
        System.out.println("[TestClient] Connected to server on localhost:8080");
        printHelp();

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            handleCommand(line);
        }
    }

    private static void handleCommand(String line) throws Exception {
        String[] parts = line.split(" ", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "register" -> {
                if (parts.length < 3) { System.out.println("Usage: register <login> <password>"); return; }
                Packet p = new Packet(PacketType.REGISTER_REQUEST,
                        new PayloadBuilder().add("login", parts[1]).add("password", parts[2]).build());
                connection.send(p);
            }
            case "login" -> {
                if (parts.length < 3) { System.out.println("Usage: login <login> <password>"); return; }
                currentLogin = parts[1];
                Packet p = new Packet(PacketType.LOGIN_REQUEST,
                        new PayloadBuilder().add("login", parts[1]).add("password", parts[2]).build());
                connection.send(p);
            }
            case "msg" -> {
                if (parts.length < 3) { System.out.println("Usage: msg <chatId> <text>"); return; }
                Packet p = new Packet(PacketType.SEND_MSG,
                        new PayloadBuilder().add("chatId", parts[1]).add("content", parts[2]).build());
                connection.send(p);
            }
            case "history" -> {
                if (parts.length < 2) { System.out.println("Usage: history <chatId>"); return; }
                Packet p = new Packet(PacketType.GET_HISTORY,
                        new PayloadBuilder().add("chatId", parts[1]).build());
                connection.send(p);
            }
            case "contacts" -> {
                Packet p = new Packet(PacketType.GET_CONTACTS, new PayloadBuilder().build());
                connection.send(p);
            }
            case "chat" -> {
                if (parts.length < 3) { System.out.println("Usage: chat <otherLogin> private|group"); return; }
                String type = parts[2].equalsIgnoreCase("group") ? "GROUP" : "PRIVATE";
                Packet p = new Packet(PacketType.CREATE_CHAT,
                        new PayloadBuilder()
                                .add("type", type)
                                .add("name", "Chat with " + parts[1])
                                .add("members", currentLogin + "," + parts[1])
                                .build());
                connection.send(p);
            }
            case "ban" -> {
                if (parts.length < 2) { System.out.println("Usage: ban <login>"); return; }
                Packet p = new Packet(PacketType.ADMIN_ACTION,
                        new PayloadBuilder().add("action", "BAN_USER").add("target", parts[1]).build());
                connection.send(p);
            }
            case "stats" -> {
                Packet p = new Packet(PacketType.ADMIN_ACTION,
                        new PayloadBuilder().add("action", "SERVER_STATS").add("target", "").build());
                connection.send(p);
            }
            case "ping" -> {
                connection.send(new Packet(PacketType.PING, new byte[0]));
            }
            case "help" -> printHelp();
            case "quit", "exit" -> {
                connection.disconnect();
                System.out.println("[TestClient] Disconnected.");
                System.exit(0);
            }
            default -> System.out.println("[TestClient] Unknown command. Type 'help' for list.");
        }
    }

    private static void onPacket(Packet packet) {
        System.out.println("\n[SERVER → " + packet.getType() + "]");
        if (packet.getPayload() != null && packet.getPayload().length > 0) {
            System.out.println("  " + new String(packet.getPayload()));
        }
        System.out.print("> ");
    }

    private static void onDisconnect() {
        System.out.println("[TestClient] Server disconnected.");
        System.exit(0);
    }

    private static void printHelp() {
        System.out.println("""

                ╔══════════════════════════════════════════╗
                ║         ASAP TestClient — команди        ║
                ╠══════════════════════════════════════════╣
                ║ register <login> <password>              ║
                ║ login <login> <password>                 ║
                ║ contacts                                 ║
                ║ chat <otherLogin> private|group          ║
                ║ msg <chatId> <text>                      ║
                ║ history <chatId>                         ║
                ║ ban <login>           (тільки адмін)     ║
                ║ stats                 (тільки адмін)     ║
                ║ ping                                     ║
                ║ quit                                     ║
                ╚══════════════════════════════════════════╝
                """);
        System.out.print("> ");
    }
}
