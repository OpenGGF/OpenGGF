package com.openggf.sprites.playable;

/** Native S3K player primary-routine contract used by object/event gates. */
public enum NativePlayableRoutine {
    INIT(0x00), CONTROL(0x02), HURT(0x04), DEAD(0x06), RESTART(0x08),
    /** loc_12590: waits for H/V_scroll_amount to settle, then routine 2 (sonic3k.asm:24680-24689). */
    WAIT_FOR_SCROLL(0x0A),
    /** AirCountdown_ReduceAir writes $C; loc_125AC/loc_15810 run the drowned fall (sonic3k.asm:33539-33567). */
    DROWN(0x0C),
    /** DebugMode bypasses the native primary-routine dispatcher entirely. */
    BYPASSED_BY_DEBUG_MODE(-1);

    private final int byteValue;
    NativePlayableRoutine(int byteValue) { this.byteValue = byteValue; }
    public int byteValue() { return byteValue; }

    /** Resolves the semantic equivalent of S3K's primary {@code routine(a0)} byte. */
    public static NativePlayableRoutine resolve(AbstractPlayableSprite player) {
        if (player.isDebugMode()) return BYPASSED_BY_DEBUG_MODE;
        // loc_1857C replaces the drown routine with 6, so death wins once set.
        if (player.getDead()) return DEAD;
        if (player.isDrowningPreDeath()) return DROWN;
        if (player.isHurt()) return HURT;
        return CONTROL;
    }
}
