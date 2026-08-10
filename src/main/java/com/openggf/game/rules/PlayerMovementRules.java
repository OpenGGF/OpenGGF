package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerMovementRules(
        boolean fixedAnglePosThreshold,
        boolean inputAlwaysCapsGroundSpeed,
        boolean angleDiffCardinalSnap,
        short movingCrouchThreshold,
        boolean airSuperspeedPreserved,
        boolean slopeResistStartsFromRest,
        boolean slopeRepelChecksOnObject,
        boolean slopeRepelUsesS3kSlipKick,
        PlayerLandingRules landing,
        PlayerLevelBoundaryRules levelBoundary,
        boolean rollingJumpPinballGateRequiresSpindashFlag,
        boolean rollStopsBelowMinimumSpeed,
        boolean rollControlledDecelUsesEffectiveDecelQuarter,
        boolean controlLockLatchesLogicalInput,
        boolean hurtRoutineLatchesLogicalInput,
        boolean waterExitBoostSkipsFastUpwardVelocity,
        boolean slopeResistAppliesAtZeroInertia,
        /**
         * Whether this game's <em>Tails_RollSpeed</em> derives the controlled roll
         * deceleration from the running deceleration instead of the flat $20 that
         * Sonic_RollSpeed uses.
         *
         * <p>ASSEMBLY FLAG: {@code fixBugs} (docs/s2disasm/s2.asm:27), 0 in the shipped ROM.
         * THE ENGINE IMPLEMENTS THE SHIPPED (UN-FIXED) BRANCH. Shipped Tails_RollSpeed
         * (s2.asm:40037-40040) keeps the outdated Sonic-1 form
         * {@code move.w (Tails_deceleration).w,d4 / asr.w #2,d4}, so Tails' controlled roll
         * deceleration is decel&gt;&gt;2 - $20 on land ($80&gt;&gt;2) but only $10 underwater
         * ($40&gt;&gt;2), which the disassembly notes makes "Tails much worse at this than
         * Sonic when underwater". With fixBugs = 1 it becomes {@code move.w #$20,d4},
         * matching Sonic_RollSpeed. Sonic 2's Sonic_RollSpeed is unconditionally flat $20,
         * and S3K's Tails_RollSpeed is likewise flat $20 (sonic3k.asm:28178 loc_14D46),
         * so this is true for Sonic 2 only.
         */
        boolean tailsRollSpeedUsesEffectiveDecelQuarter,
        /**
         * Whether this game's water routine skips the entry velocity quarter/halve
         * and the exit velocity double while {@code object_control} is holding the
         * character (a tube, a launcher, a cutscene).
         *
         * <p>S3K only. {@code Sonic_Water} tests {@code object_control(a0)} before
         * {@code asr x_vel / asr y_vel / asr y_vel} (sonic3k.asm:22235-22239) and again
         * before {@code asl y_vel} on the way out (sonic3k.asm:22264-22270);
         * {@code Tails_Water} does the same but lets {@code Tails_CPU_routine == 4}
         * through regardless (sonic3k.asm:27448-27454).
         *
         * <p>Sonic 2 has no such test: {@code Obj01_InWater} runs
         * {@code asr.w x_vel / asr.w y_vel / asr.w y_vel} unconditionally
         * (s2.asm:36393-36395), and {@code Obj01_Control} reaches
         * {@code bsr.w Sonic_Water} outside the {@code btst #0,obj_control} skip
         * (s2.asm:36236-36251). Sonic 1 likewise
         * ("01 Sonic.asm":270-272). So a character riding the CPZ spin tube into the
         * water still gets quartered.
         */
        boolean waterVelocityChangeGatedByObjectControl) {

    public boolean objectSolidHurtLandingRetainsRoutine() {
        return landing.objectSolidHurtLandingRetainsRoutine();
    }

    public boolean levelBoundaryRightStrict() {
        return levelBoundary.rightStrict();
    }

    public boolean levelBoundaryUsesCentreY() {
        return levelBoundary.usesCentreY();
    }

    public boolean levelBoundaryLockUsesScreenLockFlag() {
        return levelBoundary.lockUsesScreenLockFlag();
    }

    public boolean levelBoundaryUsesPreEasedMaxXDuringBossLock() {
        return levelBoundary.usesPreEasedMaxXDuringBossLock();
    }

    public boolean hurtStopBottomKillUnsigned() {
        return levelBoundary.hurtStopBottomKillUnsigned();
    }
}
