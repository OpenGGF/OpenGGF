package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sonic2SpecialStagePlayerInitializationTest {

    @Test
    void managerBootstrapLifecycleResetsAndReinitializesWithoutRom() {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        manager.reset();

        assertBootstrapPhase(manager,
                Sonic2SpecialStageManager.PlayerBootstrapPhase.WAIT_FIRST_DRAWING_WRAP);
        assertFalse(manager.observePlayerBootstrapDrawingWrap(false));
        assertFalse(manager.observePlayerBootstrapDrawingWrap(true));
        assertBootstrapPhase(manager,
                Sonic2SpecialStageManager.PlayerBootstrapPhase.WAIT_SECOND_DURATION_WRAP);
        assertTrue(manager.observePlayerBootstrapDrawingWrap(true));
        manager.completePlayerScalarInitializationBootstrap();
        assertBootstrapPhase(manager, Sonic2SpecialStageManager.PlayerBootstrapPhase.INITIALIZED);

        manager.reset();
        assertBootstrapPhase(manager,
                Sonic2SpecialStageManager.PlayerBootstrapPhase.WAIT_FIRST_DRAWING_WRAP);
        assertFalse(manager.observePlayerBootstrapDrawingWrap(true));
        assertTrue(manager.observePlayerBootstrapDrawingWrap(true));
        manager.completePlayerScalarInitializationBootstrap();
        assertBootstrapPhase(manager, Sonic2SpecialStageManager.PlayerBootstrapPhase.INITIALIZED);
        assertFalse(manager.observePlayerBootstrapDrawingWrap(true),
                "later wraps must not restart an initialized bootstrap");
    }

    @Test
    void newAndResetPlayerRemainZeroedInInitRoutine() {
        Sonic2SpecialStagePlayer player = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);

        assertZeroedInit(player);

        player.initializeScalarStateFromRomObjectRoutine();
        player.setSpawned(true);
        player.reset();

        assertZeroedInit(player);
        assertFalse(player.isSpawned(), "object-slot presence is reset independently from ROM routine init");
    }

    @Test
    void romObjectInitializerAppliesObj09AndObj10StateWithoutMovementStep() {
        Sonic2SpecialStagePlayer sonic = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        Sonic2SpecialStagePlayer tails = new Sonic2SpecialStagePlayer(
                Sonic2SpecialStagePlayer.PlayerType.TAILS, false);
        sonic.setSpawned(true);
        tails.setSpawned(true);

        sonic.initializeScalarStateFromRomObjectRoutine();
        tails.initializeScalarStateFromRomObjectRoutine();

        assertInitialized(sonic, 0x6E, 3);
        assertInitialized(tails, 0x80, 2);
        assertTrue(sonic.isSpawned(), "Obj09 init must not own object-slot presence");
        assertTrue(tails.isSpawned(), "Obj10 init must not own object-slot presence");
    }

    private static void assertZeroedInit(Sonic2SpecialStagePlayer player) {
        assertEquals(Sonic2SpecialStagePlayer.RoutineState.INIT, player.getRoutine());
        assertEquals(0, player.getSSXPos());
        assertEquals(0, player.getSSYPos());
        assertEquals(0, player.getSSZPos());
        assertEquals(0, player.getXPos());
        assertEquals(0, player.getYPos());
        assertEquals(0, player.getAngle());
    }

    private static void assertInitialized(
            Sonic2SpecialStagePlayer player, int expectedZ, int expectedPriority) {
        assertEquals(Sonic2SpecialStagePlayer.RoutineState.NORMAL, player.getRoutine());
        assertEquals(0, player.getSSXPos());
        assertEquals(0x80, player.getSSYPos());
        assertEquals(expectedZ, player.getSSZPos());
        assertEquals(0x80, player.getXPos());
        assertEquals(0xB6, player.getYPos());
        assertEquals(0x40, player.getAngle());
        assertEquals(expectedPriority, player.getPriority());
    }

    private static void assertBootstrapPhase(
            Sonic2SpecialStageManager manager,
            Sonic2SpecialStageManager.PlayerBootstrapPhase expected) {
        assertEquals(expected, manager.captureRewindSnapshot().playerBootstrapPhase);
    }
}
