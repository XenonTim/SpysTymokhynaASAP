package org.example.shared.protocol;

public enum PacketType {
    REGISTER_REQUEST(1), REGISTER_SUCCESS(2), REGISTER_FAILED(3),
    LOGIN_REQUEST(4), LOGIN_SUCCESS(5), LOGIN_FAILED(6),
    SEND_MSG(7), RECEIVE_MSG(8),
    EDIT_MESSAGE_REQUEST(9), MESSAGE_EDITED(10),
    DELETE_MESSAGE_REQUEST(11), MESSAGE_DELETED(12),
    CREATE_CHAT(13), CHAT_CREATED(14),
    USER_STATUS_UPDATE(15), USER_LIST_UPDATED(16),
    KEY_EXCHANGE_INIT(17), KEY_EXCHANGE_RESP(18),
    ADMIN_ACTION(19), ADMIN_SUCCESS(20), ERROR(21),
    DISCONNECT(22), BANNED_NOTIFICATION(23),

    // ДОДАНІ НОВІ ПАКЕТИ ДЛЯ КЛІЄНТА:
    GET_HISTORY(24), GET_CONTACTS(25), PING(26);

    private final int code;

    PacketType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PacketType fromCode(int code) {
        for (PacketType type : values()) {
            if (type.code == code) return type;
        }
        return ERROR;
    }
}