package com.openggf.game;

import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGameStateRewindSnapshot {
    private GameStateManager gameState;

    @BeforeEach
    void setUp() {
        gameState = new GameStateManager();
    }

    @Test
    void testGameStateSnapshotRoundTrip() {
        // Configure and set up initial state
        gameState.configureSpecialStageProgress(7, 7);
        gameState.addScore(5000);
        gameState.addLife();
        gameState.addContinue();
        gameState.markEmeraldCollected(0);
        gameState.markEmeraldCollected(3);
        gameState.markSuperEmeraldCollected(1);
        gameState.setEmeraldsConverted(true);
        gameState.setCurrentBossId(42);
        gameState.setScreenShakeActive(true);
        gameState.setBackgroundCollisionFlag(true);
        gameState.setBigRingCollected(true);
        gameState.setWfzFireToggle(true);
        gameState.setItemBonus(10);
        gameState.setReverseGravityActive(true);
        gameState.markSpecialRingCollected(5);
        gameState.setEndOfLevelActive(true);
        gameState.setEndOfLevelFlag(true);

        // Capture snapshot
        GameStateSnapshot snapshot = gameState.capture();

        // Mutate state
        gameState.addScore(1000);
        gameState.loseLife();
        gameState.setCurrentBossId(0);
        gameState.setScreenShakeActive(false);
        gameState.setWfzFireToggle(false);
        gameState.setEmeraldsConverted(false);

        // Restore from snapshot
        gameState.restore(snapshot);

        // Verify all fields match captured state
        assertEquals(5000, gameState.getScore());
        assertEquals(4, gameState.getLives()); // 3 initial + 1 added
        assertEquals(1, gameState.getContinues());
        assertTrue(gameState.hasEmerald(0));
        assertTrue(gameState.hasEmerald(3));
        // ROM state 3 (Super Emerald) remains a nonzero collected emerald state.
        assertTrue(gameState.hasEmerald(1));
        assertTrue(gameState.hasSuperEmerald(1));
        assertTrue(gameState.isEmeraldsConverted());
        assertEquals(3, gameState.getEmeraldCount());
        assertEquals(42, gameState.getCurrentBossId());
        assertTrue(gameState.isScreenShakeActive());
        assertTrue(gameState.isBackgroundCollisionFlag());
        assertTrue(gameState.isBigRingCollected());
        assertTrue(gameState.isWfzFireToggle());
        assertEquals(10, gameState.getItemBonus());
        assertTrue(gameState.isReverseGravityActive());
        assertTrue(gameState.isSpecialRingCollected(5));
        assertTrue(gameState.isEndOfLevelActive());
        assertTrue(gameState.isEndOfLevelFlag());
    }

    @Test
    void testGameStateSnapshotKey() {
        assertEquals("gamestate", gameState.key());
    }

    @Test
    void testEmeraldArraysImmutable() {
        gameState.configureSpecialStageProgress(7, 7);
        gameState.markEmeraldCollected(2);
        GameStateSnapshot snapshot = gameState.capture();

        // Mutate captured arrays (should not affect state)
        boolean[] capturedEmeralds = snapshot.gotEmeralds();
        capturedEmeralds[2] = false;

        // Verify state is unchanged
        assertTrue(gameState.hasEmerald(2));
    }

    @Test
    void emeraldConversionRoundTripsWithoutAnySuperEmeralds() {
        gameState.setEmeraldsConverted(true);

        GameStateSnapshot snapshot = gameState.capture();
        gameState.setEmeraldsConverted(false);
        gameState.restore(snapshot);

        assertTrue(gameState.isEmeraldsConverted());
        assertTrue(gameState.getCollectedSuperEmeraldIndices().isEmpty());
    }

    @Test
    void mixedEmeraldStatesRoundTripDuringSanctuaryConversion() {
        S3kEmeraldProgression.restore(
                gameState, java.util.List.of(0, 2, 1, 3, 0, 1, 2), true);

        GameStateSnapshot snapshot = gameState.capture();
        S3kEmeraldProgression.restore(
                gameState, java.util.List.of(0, 0, 0, 0, 0, 0, 0), false);
        gameState.restore(snapshot);

        assertEquals(java.util.List.of(0, 2, 1, 3, 0, 1, 2),
                S3kEmeraldProgression.from(gameState).states());
        assertTrue(gameState.isEmeraldsConverted());
    }
}
