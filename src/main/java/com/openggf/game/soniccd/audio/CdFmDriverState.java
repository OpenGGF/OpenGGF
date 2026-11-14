package com.openggf.game.soniccd.audio;

/**
 * Driver-resident Sonic CD FM bytes that outlive one effect: {@code ringChannel}
 * (FM/_Variables.inc:95), toggled by {@code TrkCmd_SwapRingChn} (FM/_Driver.asm:1741-1760).
 * The driver is installed once at boot, so the byte carries across stages. Rewind carries it
 * in {@link CdFmSoundRequestService}'s snapshot.
 */
public final class CdFmDriverState {
    private int ringChannel;
    private int trackQueued;

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
