package com.openggf.net.client;

import com.openggf.net.protocol.RoomAccessPolicy;

/** A completed private-room probe. Human input happens after its connection closes. */
@com.openggf.game.ModApi
public final class RoomAccessRequiredException extends RuntimeException {
    private final String instanceId;
    private final long accessEpoch;
    private final RoomAccessPolicy accessPolicy;

    public RoomAccessRequiredException(String instanceId, long accessEpoch, RoomAccessPolicy accessPolicy) {
        super("room password required");
        this.instanceId = instanceId;
        this.accessEpoch = accessEpoch;
        this.accessPolicy = accessPolicy;
    }

    public String instanceId() { return instanceId; }

    public long accessEpoch() { return accessEpoch; }

    public RoomAccessPolicy accessPolicy() { return accessPolicy; }
}
