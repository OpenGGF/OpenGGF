package com.openggf.net.client;

import com.openggf.net.protocol.RoomPassword;
import java.util.Arrays;

/** Credentials scoped to one expected room instance and access epoch. */
@com.openggf.game.ModApi
public final class RoomJoinOptions implements AutoCloseable {
    private final RoomPassword password;
    private final String expectedInstanceId;
    private final long expectedAccessEpoch;

    public RoomJoinOptions(RoomPassword password, String expectedInstanceId, long expectedAccessEpoch) {
        if (expectedAccessEpoch < -1 || (expectedInstanceId == null) != (expectedAccessEpoch == -1)
                || expectedInstanceId != null && (expectedInstanceId.isBlank() || expectedInstanceId.length() > 128)
                || password != null && expectedInstanceId == null) {
            throw new IllegalArgumentException("invalid expected room access");
        }
        this.password = copy(password);
        this.expectedInstanceId = expectedInstanceId;
        this.expectedAccessEpoch = expectedAccessEpoch;
    }

    public static RoomJoinOptions publicRoom() { return new RoomJoinOptions(null, null, -1); }

    public String expectedInstanceId() { return expectedInstanceId; }

    public long expectedAccessEpoch() { return expectedAccessEpoch; }

    public boolean hasPassword() { return password != null; }

    RoomPassword passwordCopy() { return copy(password); }

    RoomJoinOptions copy() { return new RoomJoinOptions(password, expectedInstanceId, expectedAccessEpoch); }

    private static RoomPassword copy(RoomPassword password) {
        if (password == null) return null;
        char[] characters = password.copyCharacters();
        try { return new RoomPassword(characters); }
        finally { Arrays.fill(characters, '\0'); }
    }

    @Override
    public void close() { if (password != null) password.close(); }

    @Override
    public String toString() { return "RoomJoinOptions[redacted]"; }
}
