package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerLandingRules(
        boolean pinballLandingPreservesRoll,
        boolean pinballLandingPreservesPinballMode,
        boolean landingRollClearUsesCurrentYRadiusDelta,
        boolean objectSolidHurtLandingRetainsRoutine,
        /** S2/S3K HurtStop clears the native object-control byte; S1 does not. */
        boolean hurtLandingClearsObjectControl,
        /**
         * Whether this game's landing path skips its {@code anim = Walk} write while
         * the native spindash byte is live.
         *
         * <p>S3K gates it explicitly: {@code Player_TouchFloor_Check_Spindash} is
         * {@code tst.b spin_dash_flag(a0) / bne / move.b #0,anim(a0)}
         * (docs/skdisasm/sonic3k.asm:24365-24369), used by the landing exits
         * and mirrored for Tails at {@code Tails_TouchFloor_Check_Spindash}
         * (docs/skdisasm/sonic3k.asm:29163-29167).
         * Sonic 2 has the same shape through its spindash alias:
         * {@code Sonic_ResetOnFloor} opens {@code tst.b pinball_mode(a0) / bne.s
         * Sonic_ResetOnFloor_Part3}, skipping the Walk store
         * (docs/s2disasm/s2.asm:38122-38125).
         *
         * <p>Sonic 1 has no spindash and no such byte, so it is false there.
         *
         * <p>Without this, a character who charges a spindash in the air and then
         * lands has the charge animation replaced by Walk on the landing frame while
         * the ROM holds it until release — visible for as long as the charge is held.
         */
        boolean landingWalkWriteSkippedWhileSpindashing) {

    /** Compatibility constructor before the landing animation rule joined this group. */
    public PlayerLandingRules(boolean pinballLandingPreservesRoll,
                              boolean pinballLandingPreservesPinballMode,
                              boolean landingRollClearUsesCurrentYRadiusDelta,
                              boolean objectSolidHurtLandingRetainsRoutine,
                              boolean hurtLandingClearsObjectControl) {
        this(pinballLandingPreservesRoll, pinballLandingPreservesPinballMode,
                landingRollClearUsesCurrentYRadiusDelta, objectSolidHurtLandingRetainsRoutine,
                hurtLandingClearsObjectControl, false);
    }

    /** Retains the original control-preserving behavior for existing creator profiles. */
    public PlayerLandingRules(boolean pinballLandingPreservesRoll,
                              boolean pinballLandingPreservesPinballMode,
                              boolean landingRollClearUsesCurrentYRadiusDelta,
                              boolean objectSolidHurtLandingRetainsRoutine) {
        this(pinballLandingPreservesRoll, pinballLandingPreservesPinballMode,
                landingRollClearUsesCurrentYRadiusDelta, objectSolidHurtLandingRetainsRoutine, false);
    }
}
