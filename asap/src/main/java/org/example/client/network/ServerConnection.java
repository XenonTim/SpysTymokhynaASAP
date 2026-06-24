package org.example.client.network;

import org.example.shared.protocol.Packet;
import org.example.shared.protocol.PacketType;
import org.example.shared.security.AesUtil;
import org.example.shared.security.RsaUtil;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.function.Consumer;

/**
 * ВИПРАВЛЕНО:
 * 1. Протокол читання/запису приведений до int-code формату (збігається з ClientHandler).
 * 2. Виконується RSA/AES handshake при підключенні — клієнт генерує AES-ключ,
 *    шифрує його RSA публічним ключем сервера і відправляє назад.
 * 3. Всі наступні пакети шифруються/дешифруються через AES.
 */
public class ServerConnection {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private SecretKey aesSessionKey;
    private boolean handshakeComplete = false;

    public void connect(Consumer<Packet> onPacket, Runnable onDisconnect) throws Exception {
        socket = new Socket(HOST, PORT);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        performHandshake();

        NetworkListener listener = new NetworkListener(socket, in, aesSessionKey, onPacket, onDisconnect);
        Thread listenerThread = new Thread(listener, "network-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Крок 1: Читаємо RSA публічний ключ від сервера.
     * Крок 2: Генеруємо AES-ключ, шифруємо RSA, надсилаємо серверу.
     */
    private void performHandshake() throws Exception {
        // Читаємо KEY_EXCHANGE_INIT
        int typeCode = in.readInt(); // тип пакета (ми його не використовуємо)
        int keyLen = in.readInt();
        byte[] publicKeyBytes = new byte[keyLen];
        in.readFully(publicKeyBytes);

        PublicKey serverPublicKey = RsaUtil.publicKeyFromBytes(publicKeyBytes);

        // Генеруємо AES-ключ
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        aesSessionKey = keyGen.generateKey();
        byte[] aesKeyBytes = aesSessionKey.getEncoded();

        // Шифруємо AES-ключ RSA публічним ключем сервера
        byte[] encryptedAesKey = RsaUtil.encryptWithPublicKey(aesKeyBytes, serverPublicKey);

        // Відправляємо KEY_EXCHANGE_RESP
        out.writeInt(PacketType.KEY_EXCHANGE_RESP.getCode());
        out.writeInt(encryptedAesKey.length);
        out.write(encryptedAesKey);
        out.flush();

        handshakeComplete = true;
    }

    /**
     * Надсилає пакет з AES-шифруванням payload (int-code протокол).
     */
    public synchronized void send(Packet packet) throws Exception {
        byte[] rawPayload = packet.getPayload() != null ? packet.getPayload() : new byte[0];

        byte[] payloadToSend;
        if (handshakeComplete
                && packet.getType() != PacketType.KEY_EXCHANGE_INIT
                && packet.getType() != PacketType.KEY_EXCHANGE_RESP) {
            String plain = new String(rawPayload, StandardCharsets.UTF_8);
            String encrypted = AesUtil.encrypt(plain, aesSessionKey);
            payloadToSend = encrypted.getBytes(StandardCharsets.UTF_8);
        } else {
            payloadToSend = rawPayload;
        }

        out.writeInt(packet.getType().getCode());
        out.writeInt(payloadToSend.length);
        if (payloadToSend.length > 0) out.write(payloadToSend);
        out.flush();
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unused")
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }
}