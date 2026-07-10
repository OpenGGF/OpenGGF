package com.openggf.net.protocol;

/** Wire-protocol constants for multiplayer time attack. */
public final class Protocol {
    public static final int VERSION = 1;
    public static final int MAX_CONTROL_BYTES = 8192;
    public static final int MAX_BINARY_BYTES = 4096;
    public static final int MAX_CHAT_CHARS = 200;
    public static final long CHAT_MIN_INTERVAL_MILLIS = 2000;
    public static final int MAX_PLAYERS_DIRECT = 8;
    public static final int MAX_PLAYERS_RELAY = 256;
    public static final int MAX_MASTER_FRAME_BYTES = 2 * MAX_CONTROL_BYTES + 1024;

    private Protocol() {
    }
}
