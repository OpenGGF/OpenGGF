package com.openggf.game.timeattack;

import com.openggf.level.SeamlessLevelTransitionRequest;

/**
 * Pure decision helpers for whether a level-end transition (next act, next
 * zone, credits, or an S3K seamless cross-act reload) should be diverted to
 * the time attack menu instead of proceeding as normal play. Extracted out of
 * {@code GameLoop} so the branching logic is unit-testable without booting
 * the engine.
 */
public final class TimeAttackLevelEndRouting {
    private TimeAttackLevelEndRouting() {
    }

    /**
     * The act/zone/credits consume sites all divert on the same condition: a
     * finished or abandoned time attack attempt never auto-advances into the
     * next act/zone/credits sequence like normal play does.
     */
    public static boolean returnsToMenuOnLevelEnd(boolean timeAttackActive) {
        return timeAttackActive;
    }

    /**
     * The S3K seamless-transition consume site is narrower: only a
     * {@code RELOAD_TARGET_LEVEL} request whose target differs from the armed
     * launch's zone/act represents a cross-act advance. Mid-act sequences
     * (MUTATE_ONLY / RELOAD_SAME_LEVEL, or a RELOAD_TARGET_LEVEL that targets
     * the same zone/act) must be left untouched even during an active attempt.
     */
    public static boolean shouldSuppressSeamlessTransitionForTimeAttack(
            boolean timeAttackActive,
            SeamlessLevelTransitionRequest.TransitionType type,
            TimeAttackLaunchRequest armedLaunch,
            int targetZone,
            int targetAct) {
        if (!timeAttackActive
                || type != SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL
                || armedLaunch == null) {
            return false;
        }
        return targetZone != armedLaunch.zone() || targetAct != armedLaunch.act();
    }
}
