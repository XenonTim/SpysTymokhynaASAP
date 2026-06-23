package org.example.gateway;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Мінімальна реалізація сервера WebSocket (RFC 6455) "з нуля",
 * без зовнішніх бібліотек — достатньо для обміну текстовими (JSON) фреймами
 * між браузером і WebGateway.
 *
 * Підтримує лише те, що потрібно цьому проєкту:
 *  - один HTTP Upgrade handshake;
 *  - текстові фрейми (opcode 0x1) від клієнта до сервера (завжди маскуються браузером);
 *  - текстові фрейми від сервера до клієнта (без маски);
 *  - close-фрейм (opcode 0x8) для коректного завершення з'єднання.
 */
public class WebSocketConnection {

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private WebSocketConnection(Socket socket, InputStream in, OutputStream out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    /**
     * Виконує WebSocket handshake над уже прийнятим HTTP GET запитом.
     * requestHeaders — заголовки, які вже зчитав HTTP-шар (потрібен лише Sec-WebSocket-Key).
     */
    public static WebSocketConnection upgrade(Socket socket, String secWebSocketKey) throws IOException {
        OutputStream out = socket.getOutputStream();
        String acceptKey = computeAcceptKey(secWebSocketKey);

        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        return new WebSocketConnection(socket, socket.getInputStream(), out);
    }

    private static String computeAcceptKey(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 unavailable", e);
        }
    }

    /** Блокуюче читання фреймів у циклі. Кличе onText для кожного текстового повідомлення. */
    public void listen(Consumer<String> onText, Runnable onClose) {
        try {
            while (true) {
                Frame frame = readFrame();
                if (frame == null) break;

                if (frame.opcode == 0x8) {
                    break;
                } else if (frame.opcode == 0x1) {
                    onText.accept(new String(frame.payload, StandardCharsets.UTF_8));
                } else if (frame.opcode == 0x9) {
                    sendFrame(0xA, frame.payload);
                }
            }
        } catch (IOException ignored) {
            // з'єднання закрито клієнтом
        } finally {
            onClose.run();
            closeQuietly();
        }
    }

    public synchronized void sendText(String text) {
        try {
            sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            closeQuietly();
        }
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        out.write(0x80 | opcode);

        int len = payload.length;
        if (len <= 125) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((((long) len) >> (8 * i)) & 0xFF));
            }
        }
        out.write(payload);
        out.flush();
    }

    private Frame readFrame() throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        int b2 = in.read();
        if (b2 == -1) return null;

        boolean fin = (b1 & 0x80) != 0;
        int opcode = b1 & 0x0F;
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;

        if (len == 126) {
            len = (readByte() << 8) | readByte();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | readByte();
            }
        }

        byte[] mask = new byte[4];
        if (masked) {
            readFully(mask);
        }

        byte[] payload = new byte[(int) len];
        readFully(payload);

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % 4];
            }
        }

        // Цей проєкт не збирає фрагментовані фрейми (fin=false) — для коротких
        // JSON-повідомлень чату це не потрібно, тож fin тут навмисно не перевіряється.
        Frame frame = new Frame();
        frame.opcode = opcode;
        frame.payload = payload;
        return frame;
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b == -1) throw new EOFException("WebSocket stream closed");
        return b;
    }

    private void readFully(byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int read = in.read(buf, offset, buf.length - offset);
            if (read == -1) throw new EOFException("WebSocket stream closed");
            offset += read;
        }
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    private static class Frame {
        int opcode;
        byte[] payload;
    }
}
