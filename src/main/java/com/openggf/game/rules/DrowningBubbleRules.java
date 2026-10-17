package com.openggf.game.rules;

/**
 * Per-game drowning-countdown behaviour.
 *
 * @param drownInstallsDedicatedPlayerRoutine
 *        whether running out of air installs a dedicated player routine whose
 *        dispatch skips the player's water check. S3K's AirCountdown_ReduceAir
 *        writes routine $C, and loc_125AC/loc_15810 never reach
 *        Sonic_Water/Tails_Water (docs/skdisasm/sonic3k.asm:33539-33567,
 *        24692-24707, 29314-29330). S1 and S2 keep the control routine, whose
 *        Sonic_Water still runs ("0A LZ Drowning Countdown.asm":266 with
 *        "01 Sonic.asm":82; s2.asm:42172 with s2.asm:36251).
 */
@com.openggf.game.ModApi
public record DrowningBubbleRules(
        int initialDrowningCountdownFrameTimer,
        int mouthBubbleTimerBias,
        boolean breathingBubbleDefersFirstObjectPass,
        int mouthBubbleRiseVelocity,
        boolean drownInstallsDedicatedPlayerRoutine) {

    /** Prior candidate clients retain the control-routine water check. */
    public DrowningBubbleRules(int initialDrowningCountdownFrameTimer,
            int mouthBubbleTimerBias, boolean breathingBubbleDefersFirstObjectPass,
            int mouthBubbleRiseVelocity) {
        this(initialDrowningCountdownFrameTimer, mouthBubbleTimerBias,
                breathingBubbleDefersFirstObjectPass, mouthBubbleRiseVelocity, false);
    }
}
