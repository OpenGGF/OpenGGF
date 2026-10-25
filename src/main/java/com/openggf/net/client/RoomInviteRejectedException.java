package com.openggf.net.client;

/** Generic invite failure with a server-provided cooldown, without the entered locator. */
@com.openggf.game.ModApi
public final class RoomInviteRejectedException extends RuntimeException {
    private final long retryAfterMillis;

    public RoomInviteRejectedException(long retryAfterMillis) {
        super("room invite unavailable");
        this.retryAfterMillis = Math.max(0, retryAfterMillis);
    }

    public long retryAfterMillis() { return retryAfterMillis; }
}
