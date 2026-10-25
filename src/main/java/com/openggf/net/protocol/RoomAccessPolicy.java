package com.openggf.net.protocol;

import java.util.Objects;

/** Non-secret room admission and invitation policy. */
@com.openggf.game.ModApi
public record RoomAccessPolicy(Mode mode, boolean membersCanInvite) {
    @com.openggf.game.ModApi
    public enum Mode { PUBLIC, PRIVATE }

    public static final RoomAccessPolicy PUBLIC = new RoomAccessPolicy(Mode.PUBLIC, true);

    public RoomAccessPolicy {
        Objects.requireNonNull(mode, "access mode");
    }

    public boolean passwordRequired() {
        return mode == Mode.PRIVATE;
    }
}
