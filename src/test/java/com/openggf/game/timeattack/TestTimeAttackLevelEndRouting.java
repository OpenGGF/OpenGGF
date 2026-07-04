package com.openggf.game.timeattack;

import com.openggf.level.SeamlessLevelTransitionRequest.TransitionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTimeAttackLevelEndRouting {

    @Test
    void levelEndReturnsToMenuOnlyWhenTimeAttackActive() {
        assertTrue(TimeAttackLevelEndRouting.returnsToMenuOnLevelEnd(true));
        assertFalse(TimeAttackLevelEndRouting.returnsToMenuOnLevelEnd(false));
    }

    @Test
    void seamlessSuppressionRequiresActiveAttack() {
        TimeAttackLaunchRequest launch = new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", List.of());
        assertFalse(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                false, TransitionType.RELOAD_TARGET_LEVEL, launch, 0, 1));
    }

    @Test
    void seamlessSuppressionRequiresReloadTargetLevel() {
        TimeAttackLaunchRequest launch = new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", List.of());
        assertFalse(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                true, TransitionType.MUTATE_ONLY, launch, 0, 1));
        assertFalse(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                true, TransitionType.RELOAD_SAME_LEVEL, launch, 0, 1));
    }

    @Test
    void seamlessSuppressionRequiresArmedLaunch() {
        assertFalse(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                true, TransitionType.RELOAD_TARGET_LEVEL, null, 0, 1));
    }

    @Test
    void seamlessSuppressionAllowsSameZoneActReload() {
        // Mid-act sequences (e.g. the AIZ1 ship) issue RELOAD_TARGET_LEVEL requests
        // targeting the SAME zone/act; these must not be treated as a cross-act
        // advance and must not end the attempt.
        TimeAttackLaunchRequest launch = new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", List.of());
        assertFalse(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                true, TransitionType.RELOAD_TARGET_LEVEL, launch, 0, 0));
    }

    @Test
    void seamlessSuppressionFiresOnCrossActReload() {
        TimeAttackLaunchRequest launch = new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", List.of());
        assertTrue(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                true, TransitionType.RELOAD_TARGET_LEVEL, launch, 0, 1));
        assertTrue(TimeAttackLevelEndRouting.shouldSuppressSeamlessTransitionForTimeAttack(
                true, TransitionType.RELOAD_TARGET_LEVEL, launch, 1, 0));
    }
}
