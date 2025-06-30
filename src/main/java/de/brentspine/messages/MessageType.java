package de.brentspine.messages;

public enum MessageType {

    NOT_FOUND(-1),
    UNKNOWN(-1000),
    INIT(0),
    PING_IN(2),
    PING_OUT(3),
    AUTH(40),
    GAME(42), // Chat, Game, Results
    JOIN(420),
    ROOM_ENTRY(430),
    GET_CHATTER_PROFILES(421),
    RECEIVE_CHATTER_PROFILES(431);

    private final int code;
    MessageType(int code) {
        this.code = code;
    }
    public int getCode() {
        return code;
    }

    public static MessageType getMessageType(int code) {
        for (MessageType type : MessageType.values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        System.err.println("Unknown message type " + code);
        return UNKNOWN;
    }
}
