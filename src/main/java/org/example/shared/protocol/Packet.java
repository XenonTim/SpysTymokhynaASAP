package org.example.shared.protocol;

import java.io.Serializable;

public class Packet implements Serializable {

    private final PacketType type;
    private final byte[] payload;

    public Packet(PacketType type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public PacketType getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getLength() {
        return payload != null ? payload.length : 0;
    }
}
