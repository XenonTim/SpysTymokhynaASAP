package org.example.client.network;

import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketType;
import org.example.shared.security.AesUtil;

import javax.crypto.SecretKey;
import java.io.DataInputStream;
import java.io.EOFException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * ВИПРАВЛЕНО:
 * 1. Читає пакети у int-code форматі (відповідає ClientHandler і PacketIO).
 * 2. Дешифрує payload через AES після handshake.
 * 3. Передає конструктору DataInputStream та AES-ключ (не відкриває stream заново).
 */
public class NetworkListener implements Runnable {

    private final Socket socket;
    private final DataInputStream in;
    private final SecretKey aesSessionKey;
    private final Consumer<Packet> onPacket;
    private final Runnable onDisconnect;

    public NetworkListener(Socket socket,
                           DataInputStream in,
                           SecretKey aesSessionKey,
                           Consumer<Packet> onPacket,
                           Runnable onDisconnect) {
        this.socket        = socket;
        this.in            = in;
        this.aesSessionKey = aesSessionKey;
        this.onPacket      = onPacket;
        this.onDisconnect  = onDisconnect;
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                int typeCode   = in.readInt();
                int payloadLen = in.readInt();

                byte[] rawPayload = new byte[payloadLen];
                if (payloadLen > 0) in.readFully(rawPayload);

                PacketType type = PacketType.fromCode(typeCode);

                // Дешифруємо payload через AES
                byte[] decryptedPayload;
                if (aesSessionKey != null
                        && type != PacketType.KEY_EXCHANGE_INIT
                        && type != PacketType.KEY_EXCHANGE_RESP) {
                    try {
                        String encrypted = new String(rawPayload, StandardCharsets.UTF_8);
                        String decrypted = AesUtil.decrypt(encrypted, aesSessionKey);
                        decryptedPayload = decrypted.getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        // Якщо дешифрування не вдалося — передаємо як є
                        decryptedPayload = rawPayload;
                    }
                } else {
                    decryptedPayload = rawPayload;
                }

                onPacket.accept(new Packet(type, decryptedPayload));
            }
        } catch (EOFException ignored) {
            // Сервер закрив з'єднання
        } catch (Exception e) {
            if (!socket.isClosed()) {
                System.err.println("[NetworkListener] Помилка: " + e.getMessage());
            }
        } finally {
            onDisconnect.run();
        }
    }
}