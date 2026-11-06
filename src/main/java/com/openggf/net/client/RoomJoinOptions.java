package com.openggf.net.client;

import com.openggf.net.protocol.RoomPassword;
import java.util.Arrays;

/** Credentials scoped to one expected room instance and access epoch. */
@com.openggf.game.ModApi
public final class RoomJoinOptions implements AutoCloseable {
    private final RoomPassword password;
    private final String expectedInstanceId;
    private final long expectedAccessEpoch;
    private final String grantReference;
    private final long routeGeneration;

    public RoomJoinOptions(RoomPassword password, String expectedInstanceId, long expectedAccessEpoch) {
        this(password, expectedInstanceId, expectedAccessEpoch, null, 0);
    }

    private RoomJoinOptions(RoomPassword password, String expectedInstanceId,
                            long expectedAccessEpoch, String grantReference,
                            long routeGeneration) {
        if (expectedAccessEpoch < -1 || (expectedInstanceId == null) != (expectedAccessEpoch == -1)
                || expectedInstanceId != null && (expectedInstanceId.isBlank() || expectedInstanceId.length() > 128)
                || password != null && expectedInstanceId == null
                || (grantReference == null) != (routeGeneration == 0)
                || grantReference != null && (grantReference.isBlank()
                || grantReference.length() > 128 || routeGeneration <= 0)) {
            throw new IllegalArgumentException("invalid expected room access");
        }
        this.password = copy(password);
        this.expectedInstanceId = expectedInstanceId;
        this.expectedAccessEpoch = expectedAccessEpoch;
        this.grantReference = grantReference;
        this.routeGeneration = routeGeneration;
    }

    public static RoomJoinOptions publicRoom() { return new RoomJoinOptions(null, null, -1); }

    public String expectedInstanceId() { return expectedInstanceId; }

    public long expectedAccessEpoch() { return expectedAccessEpoch; }

    public boolean hasPassword() { return password != null; }

    String grantReference() { return grantReference; }

    long routeGeneration() { return routeGeneration; }

    RoomPassword passwordCopy() { return copy(password); }

    RoomJoinOptions copy() { return new RoomJoinOptions(password, expectedInstanceId,
            expectedAccessEpoch, grantReference, routeGeneration); }

    RoomJoinOptions withRoute(String reference, long generation) {
        return new RoomJoinOptions(password, expectedInstanceId, expectedAccessEpoch,
                reference, generation);
    }

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
