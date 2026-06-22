package org.example.shared.protocol;

import java.io.*;

public class PacketIO {

    public static void write(DataOutputStream out, Packet packet) throws IOException {
        String typeName = packet.getType().name();
        byte[] typeBytes = typeName.getBytes("UTF-8");
        byte[] payload = packet.getPayload() != null ? packet.getPayload() : new byte[0];

        out.writeInt(typeBytes.length);
        out.write(typeBytes);
        out.writeInt(payload.length);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    public static Packet read(DataInputStream in) throws IOException {
        int typeLen = in.readInt();
        byte[] typeBytes = new byte[typeLen];
        in.readFully(typeBytes);
        PacketType type = PacketType.valueOf(new String(typeBytes, "UTF-8"));

        int payloadLen = in.readInt();
        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            in.readFully(payload);
        }

        return new Packet(type, payload);
    }
}
