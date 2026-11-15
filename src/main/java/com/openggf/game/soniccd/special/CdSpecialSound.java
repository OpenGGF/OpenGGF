package com.openggf.game.soniccd.special;

/**
 * FM sound requests the special stage makes, by native FM driver ID
 * (Sound Drivers/FM/_Sound IDs.inc, {@code rsset $90} at line 8). The sub-CPU queues them
 * ({@code PlayFMSound}, scddisasm/src/Special Stage/Sub.asm:2275-2293) and the main CPU forwards them
 * ({@code PlaySubFMSounds}, scddisasm/src/Special Stage/Main.asm:2686-2696). They are emitted as typed requests and are not
 * connected to an audio owner by this lane.
 */
public enum CdSpecialSound {
    JUMP(0x92), RING_LOSS(0x94), RING(0x95), SPRING(0x98), KACHING(0x9A), SPLASH(0xA2), EXPLOSION(0xA3), BUMPER(0xB5), FAN(0xB8), TIME_UFO(0xBC), TALLY(0xBD),
    SMALL_BOOSTER(0xC3), WARP(0xC8), BIG_BOOSTER(0xCE), DUST(0xD6), TIME_STONE(0xD9), TIME_WARNING(0xDF);

    private final int fmId;

    CdSpecialSound(int fmId) { this.fmId = fmId; }

    public int fmId() { return fmId; }
}
