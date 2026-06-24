package org.example.gateway;

import org.example.client.network.ServerConnection;
import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketType;
import org.example.shared.protocol.PayloadBuilder;

import java.util.Map;

/**
 * Місток "один браузерний клієнт ↔ одне TCP-з'єднання до ASAP Server".
 *
 * Кожне нове WebSocket-з'єднання від браузера створює власний
 * ServerConnection (так само, як це робить консольний TestClient) і
 * відкриває окреме TCP-з'єднання до org.example.server.network.Server.
 * Це навмисне рішення: gateway нічого не підмінює у протоколі — він лише
 * перекладає JSON (зручний для JS) у бінарні Packet/PacketType, якими вже
 * оперує сервер, і навпаки.
 */
public class ClientSessionBridge {

    private final WebSocketConnection ws;
    private final ServerConnection serverConnection = new ServerConnection();

    public ClientSessionBridge(WebSocketConnection ws) {
        this.ws = ws;
    }

    public void start() {
        try {
            serverConnection.connect(this::onPacketFromServer, this::onServerDisconnect);
        } catch (Exception e) {
            ws.sendText(SimpleJson.toJson(Map.of(
                    "type", "ERROR",
                    "reason", "Не вдалося з'єднатися з ASAP Server: " + e.getMessage()
            )));
            return;
        }

        ws.listen(this::onMessageFromBrowser, this::onBrowserDisconnect);
    }

    private void onMessageFromBrowser(String json) {
        try {
            Map<String, String> data = SimpleJson.parse(json);
            String typeName = data.get("type");
            if (typeName == null) return;

            PacketType type = PacketType.valueOf(typeName);

            PayloadBuilder builder = new PayloadBuilder();
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (!e.getKey().equals("type")) {
                    builder.add(e.getKey(), e.getValue());
                }
            }

            serverConnection.send(new Packet(type, builder.build()));
        } catch (Exception e) {
            ws.sendText(SimpleJson.toJson(Map.of(
                    "type", "ERROR",
                    "reason", "Некоректне повідомлення від клієнта: " + e.getMessage()
            )));
        }
    }

    private void onPacketFromServer(Packet packet) {
        Map<String, String> fields = PayloadBuilder.parse(packet.getPayload());
        Map<String, String> out = new java.util.LinkedHashMap<>(fields);
        out.put("type", packet.getType().name());
        ws.sendText(SimpleJson.toJson(out));
    }

    private void onServerDisconnect() {
        ws.sendText(SimpleJson.toJson(Map.of(
                "type", "ERROR",
                "reason", "Зв'язок з ASAP Server втрачено"
        )));
    }

    private void onBrowserDisconnect() {
        serverConnection.disconnect();
    }
}
