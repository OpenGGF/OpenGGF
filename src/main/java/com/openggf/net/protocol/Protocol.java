package com.openggf.net.protocol;

/** Wire-protocol constants for multiplayer time attack. */
public final class Protocol {
    public static final int VERSION = 2;
    // A full 256-player roster with bounded labels must fit in one control frame.
    public static final int MAX_CONTROL_BYTES = 64 * 1024;
    public static final int MAX_BINARY_BYTES = 4096;
    public static final int MAX_CHAT_CHARS = 200;
    public static final long CHAT_MIN_INTERVAL_MILLIS = 2000;
    public static final int MAX_PLAYERS_DIRECT = 8;
    public static final int MAX_PLAYERS_RELAY = 256;
    public static final int MAX_MASTER_FRAME_BYTES = 2 * MAX_CONTROL_BYTES + 1024;
    /** Reserved RoomListResult totalPages value for a rate-limited request. */
    public static final int ROOM_LIST_RATE_LIMITED = -1;

    private Protocol() {
    }
}
