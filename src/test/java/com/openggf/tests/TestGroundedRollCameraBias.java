package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.rules.CameraRules;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retail S1 Sonic_AngledRollSpeed (_incObj/01 Sonic.asm:829-842) omits the
 * FixBugs-only camera reset. S2 Obj01_Roll_ResetScr (s2.asm:37074-37084) and
 * S3K loc_115C6 (sonic3k.asm:23038-23047) always step the bias toward $60.
 * S1 Sonic_RollJumpLock (_incObj/01 Sonic.asm:974-982) keeps its airborne reset.
 * These tests run the actual speed routines with production session services;
 * no level is loaded, so terrain cannot change the isolated camera contract.
 */
class TestGroundedRollCameraBias {
    private record Case(SonicGame game, int bias, int rollStep) { }
    private record Harness(Sonic player, Camera camera, PlayableSpriteMovement movement) { }
    private record Outcome(int bias, int xSpeed, int ySpeed, int groundSpeed,
                           boolean air, boolean rolling) { }

    @AfterEach
    void closeSession() {
        SessionManager.clear();
    }

    static Stream<Case> retailCases() {
        return Stream.of(
                new Case(SonicGame.SONIC_1, 0x50, 0),
                new Case(SonicGame.SONIC_1, 0x80, 0),
                new Case(SonicGame.SONIC_2, 0x50, 2),
                new Case(SonicGame.SONIC_2, 0x80, -2),
                new Case(SonicGame.SONIC_3K, 0x50, 2),
                new Case(SonicGame.SONIC_3K, 0x80, -2));
    }

    @Test
    void sixArgumentCameraRulesKeepTheirExistingRollingResetBehavior() {
        CameraRules rules = new CameraRules((short) 120, false, 16, false, false, true);

        assertTrue(rules.groundedRollRecentersCameraBias());
    }

    @ParameterizedTest
    @MethodSource("retailCases")
    void groundedRollUsesTheRetailCameraResetBranch(Case row) throws Exception {
        Harness harness = boot(row);

        for (int pass = 1; pass <= 3; pass++) {
            invoke(harness.movement(), "doRollSpeed");
            assertEquals(row.bias() + pass * row.rollStep(), harness.camera().getYPosBias());
            assertTrue(harness.player().getRolling());
            assertFalse(harness.player().getAir());
        }
    }

    @ParameterizedTest
    @MethodSource("retailCases")
    void rollStopKeepsTheCameraBranchOfTheGroundedRollDispatch(Case row) throws Exception {
        Harness harness = boot(row);
        harness.player().setGSpeed((short) 0);

        invoke(harness.movement(), "doRollSpeed");

        assertFalse(harness.player().getRolling(), "zero inertia leaves the rolling state");
        assertEquals(row.bias() + row.rollStep(), harness.camera().getYPosBias(),
                "unrolling does not enter the normal-ground camera reset in the same pass");
    }

    @ParameterizedTest
    @ValueSource(ints = {0x50, 0x80})
    void s1AirControlResumesRecenteringAfterTheRollingGroundPass(int bias) throws Exception {
        Harness harness = boot(new Case(SonicGame.SONIC_1, bias, 0));
        invoke(harness.movement(), "doRollSpeed");
        assertEquals(bias, harness.camera().getYPosBias());

        harness.player().setAir(true);
        int step = bias < 0x60 ? 2 : -2;
        for (int pass = 1; pass <= 3; pass++) {
            invoke(harness.movement(), "doChgJumpDir");
            assertEquals(bias + pass * step, harness.camera().getYPosBias(),
                    "Sonic_ChgJumpDir owns its ordinary airborne camera reset");
        }
    }

    @ParameterizedTest
    @MethodSource("retailCases")
    void destructiveRestoreReplaysTheGroundRollAndAirCameraBranches(Case row) throws Exception {
        Harness harness = boot(row);
        var heldCamera = harness.camera().capture();
        var heldPlayer = harness.player().captureRewindState();
        var heldMovement = harness.movement().captureRewindState();
        Outcome expected = rollThenAir(harness, row);

        // Destroy both the bias and the player dispatch state before restoring
        // the real snapshot owners; replay must select the same two branches.
        harness.camera().setYPosBias((short) 0x60);
        harness.player().setAir(false);
        harness.player().setRolling(false);
        harness.player().setGSpeed((short) -0x1000);
        harness.player().setXSpeed((short) -0x1000);
        harness.player().setYSpeed((short) 0x700);
        harness.camera().restore(heldCamera);
        harness.player().restoreRewindState(heldPlayer);
        harness.movement().restoreRewindState(heldMovement);

        assertEquals(row.bias(), harness.camera().getYPosBias());
        assertFalse(harness.player().getAir());
        assertTrue(harness.player().getRolling());
        assertEquals(expected, rollThenAir(harness, row));
    }

    private static Outcome rollThenAir(Harness harness, Case row) throws Exception {
        for (int pass = 1; pass <= 2; pass++) {
            invoke(harness.movement(), "doRollSpeed");
            assertEquals(row.bias() + pass * row.rollStep(), harness.camera().getYPosBias());
        }
        harness.player().setAir(true);
        invoke(harness.movement(), "doChgJumpDir");
        int airStep = row.bias() < 0x60 ? 2 : -2;
        assertEquals(row.bias() + 2 * row.rollStep() + airStep, harness.camera().getYPosBias());
        return new Outcome(harness.camera().getYPosBias(), harness.player().getXSpeed(),
                harness.player().getYSpeed(), harness.player().getGSpeed(),
                harness.player().getAir(), harness.player().getRolling());
    }

    private static Harness boot(Case row) {
        TestEnvironment.configureGameModuleFixture(row.game());
        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setGroundMode(GroundMode.GROUND);
        player.setAir(false);
        player.setRolling(true);
        player.setAngle((byte) 0);
        player.setGSpeed((short) 0x400);
        player.setXSpeed((short) 0x400);
        player.setYSpeed((short) 0);
        Camera camera = GameServices.camera();
        camera.setYPosBias((short) row.bias());
        assertFalse(player.isCpuControlled(), "the main player owns the camera bias");
        PlayableSpriteMovement movement = new PlayableSpriteMovement(
                player, GameServices.collision(), GameServices.gameState());
        return new Harness(player, camera, movement);
    }

    private static void invoke(PlayableSpriteMovement movement, String routine) throws Exception {
        Method method = PlayableSpriteMovement.class.getDeclaredMethod(routine);
        method.setAccessible(true);
        method.invoke(movement);
    }
}
