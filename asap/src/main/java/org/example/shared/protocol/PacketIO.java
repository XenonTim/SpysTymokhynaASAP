package org.example.shared.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PacketIO {

    // Записує пакет у int-code форматі
    public static void write(DataOutputStream out, Packet packet) throws IOException {
        byte[] payload = packet.getPayload() != null ? packet.getPayload() : new byte[0];
        out.writeInt(packet.getType().getCode());
        out.writeInt(payload.length);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    // Читає пакет у int-code форматі
    public static Packet read(DataInputStream in) throws IOException {
        int typeCode = in.readInt();
        PacketType type = PacketType.fromCode(typeCode);
        int payloadLen = in.readInt();
        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            in.readFully(payload);
        }
        return new Packet(type, payload);
    }
}