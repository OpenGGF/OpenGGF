package com.openggf.game.session;

import com.openggf.level.SeamlessLevelTransitionRequest.TransitionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGameplayRunRouting {
    private static final GameplayRunPolicy ISOLATED = GameplayRunPolicy.isolatedAct();

    @Test
    void stockPolicyReproducesTheCampaign() {
        GameplayRunPolicy stock = GameplayRunPolicy.stock();
        assertTrue(stock.specialStageEntry());
        assertTrue(stock.bonusStageEntry());
        assertTrue(stock.liveRewind());
        assertTrue(stock.editorEntry());
        assertFalse(stock.returnsToHostOnActCompletion());
        assertEquals(GameplayRunPolicy.ActCompletion.CONTINUE, stock.actCompletion());
    }

    @Test
    void isolatedActForbidsStagesRewindAndEditorAndReturnsToHost() {
        assertFalse(ISOLATED.specialStageEntry());
        assertFalse(ISOLATED.bonusStageEntry());
        assertFalse(ISOLATED.liveRewind());
        assertFalse(ISOLATED.editorEntry());
        assertTrue(ISOLATED.returnsToHostOnActCompletion());
    }

    @Test
    void seamlessTransitionNeverEndsAStockRun() {
        assertFalse(GameplayRunRouting.endsRunOnSeamlessTransition(GameplayRunPolicy.stock(),
                0, 0, TransitionType.RELOAD_TARGET_LEVEL, 0, 1));
    }

    @Test
    void seamlessTransitionRequiresReloadTargetLevel() {
        assertFalse(GameplayRunRouting.endsRunOnSeamlessTransition(ISOLATED,
                0, 0, TransitionType.MUTATE_ONLY, 0, 1));
        assertFalse(GameplayRunRouting.endsRunOnSeamlessTransition(ISOLATED,
                0, 0, TransitionType.RELOAD_SAME_LEVEL, 0, 1));
    }

    @Test
    void seamlessTransitionRequiresAStartedRun() {
        assertFalse(GameplayRunRouting.endsRunOnSeamlessTransition(ISOLATED,
                -1, -1, TransitionType.RELOAD_TARGET_LEVEL, 0, 1));
    }

    @Test
    void seamlessReloadOfTheRunsOwnActDoesNotEndIt() {
        // Mid-act sequences (e.g. the AIZ1 ship) issue RELOAD_TARGET_LEVEL requests
        // targeting the SAME zone/act; they are not a cross-act advance.
        assertFalse(GameplayRunRouting.endsRunOnSeamlessTransition(ISOLATED,
                0, 0, TransitionType.RELOAD_TARGET_LEVEL, 0, 0));
    }

    @Test
    void crossActSeamlessReloadEndsTheRun() {
        assertTrue(GameplayRunRouting.endsRunOnSeamlessTransition(ISOLATED,
                0, 0, TransitionType.RELOAD_TARGET_LEVEL, 0, 1));
        assertTrue(GameplayRunRouting.endsRunOnSeamlessTransition(ISOLATED,
                0, 0, TransitionType.RELOAD_TARGET_LEVEL, 1, 0));
    }
}
