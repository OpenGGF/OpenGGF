package com.openggf.game.soniccd.audio;

/**
 * Sonic CD FM driver state shared across effects and stage loads.
 * {@link CdFmSoundRequestService} captures it for rewind and boundary rollback.
 */
public final class CdFmDriverState {
    private int ringChannel;
    private int trackQueued;
    // jumpCondition (Sound Drivers/FM/_Variables.inc:96) is written as 0 or 1.
    private boolean jumpCondition;

    boolean jumpCondition() {
        return jumpCondition;
    }

    void setJumpCondition(boolean value) {
        jumpCondition = value;
    }

    /** A sound a track wrote to the first priority slot ({@code sndQueues}), 0 when none. */
    public int trackQueued() {
        return trackQueued;
    }

    public void setTrackQueued(int id) {
        trackQueued = id & 0xFF;
    }

    public int ringChannel() {
        return ringChannel;
    }

    public void setRingChannel(int value) {
        ringChannel = value & 0xFF;
    }
}
