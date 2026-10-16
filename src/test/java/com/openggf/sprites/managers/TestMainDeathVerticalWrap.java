package com.openggf.sprites.managers;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Main routine-6 motion is raw: S1 Sonic_Death/ObjectFall, S2 Obj01_Dead and
 * Obj02_Dead/ObjectMoveAndFall, S3K loc_123AA/MoveSprite_TestGravity.
 * The control-path mask is exercised separately as a positive fixture control.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestMainDeathVerticalWrap {
    private record Case(String game, String main, int restartRow, boolean controlMasks) { }
    private GameModule previousModule;

    @BeforeEach void rememberModule() { previousModule = GameModuleRegistry.getCurrent(); }
    @AfterEach void reset() {
        SessionManager.clear();
        if (previousModule == null) GameModuleRegistry.reset();
        else GameModuleRegistry.setCurrent(previousModule);
    }

    static Stream<Case> shippedMainPaths() {
        // S1/S2 death owners read the maximum bottom word; S3K reads physical
        // Camera_Y_pos. All add $100 on the upright branch before comparing.
        return Stream.of(new Case("s1", "sonic", 4096 + 0x100, false),
                new Case("s2", "sonic", 4096 + 0x100, true),
                new Case("s2", "tails", 4096 + 0x100, true),
                new Case("s3k", "sonic", 3918 + 0x100, true),
                new Case("s3k", "tails", 3918 + 0x100, true),
                new Case("s3k", "knuckles", 3918 + 0x100, true));
    }

    @ParameterizedTest(name = "raw main death and restart row {0}")
    @MethodSource("shippedMainPaths")
    void dyingMainCrossesTheNativeRowWithoutTheControlWrapMask(Case row) {
        checkRawDeathMotion(row, false);
    }

    @ParameterizedTest(name = "full roster dispatch preserves raw main death {0}")
    @MethodSource("shippedMainPaths")
    void rosterDispatchAlsoPreservesTheNativeDeathRowCrossing(Case row) {
        checkRawDeathMotion(row, true);
    }

    private void checkRawDeathMotion(Case row, boolean rosterDispatch) {
        var player = boot(row);
        if (rosterDispatch) GameServices.sprites().addSprite(player);
        player.setCentreX((short) 192);
        player.setCentreY((short) 4046);
        int lives = GameServices.gameState().getLives();
        assertTrue(player.applyPitDeath(), "actual semantic kill owner");
        var movement = new PlayableSpriteMovement(player, null, GameServices.gameState());
        int iterations = 0;
        while (!player.isInDeathRestartRoutine() && iterations++ < 160) {
            if (rosterDispatch) GameServices.sprites().updateWithoutInput();
            else movement.handleMovement(false, false, false, false, false, false, false, false);
        }
        assertTrue(player.isInDeathRestartRoutine(), "routine-6 raw motion reaches restart row: " + row
                + " y=" + player.getCentreY() + " yvel=" + player.getYSpeed());
        assertTrue(player.getCentreY() > row.restartRow(), "literal native row was crossed");
        assertTrue(player.getCentreY() > 0xFFF, "corpse must leave the ordinary wrapping domain");
        assertEquals(lives - 1, GameServices.gameState().getLives(), "one life on actual row crossing");
        assertEquals(60, player.getDeathCountdown(), "native routine8 countdown is armed once");
    }

    @ParameterizedTest(name = "ordinary live control mask {0}")
    @MethodSource("shippedMainPaths")
    void liveControlMaskRemainsOwnedByItsExistingCameraRules(Case row) {
        var player = boot(row);
        player.setCentreY((short) 0x1005);
        assertFalse(player.getDead());
        assertEquals(row.controlMasks(), GameServices.camera().applyScreenYWrapValue(player));
        assertEquals(row.controlMasks() ? 5 : 0x1005, player.getCentreY());
    }

    private AbstractPlayableSprite boot(Case row) {
        GameModule module = switch (row.game()) {
            case "s1" -> new Sonic1GameModule();
            case "s2" -> new Sonic2GameModule();
            case "s3k" -> new Sonic3kGameModule();
            default -> throw new AssertionError(row.game());
        };
        GameModuleRegistry.setCurrent(module);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, row.main());
        var player = switch (row.main()) {
            case "sonic" -> new Sonic("sonic", (short) 0, (short) 0);
            case "tails" -> new Tails("tails", (short) 0, (short) 0);
            case "knuckles" -> new Knuckles("knuckles", (short) 0, (short) 0);
            default -> throw new AssertionError(row.main());
        };
        assertFalse(player.isCpuControlled(), "native main dispatch");
        var camera = GameServices.camera();
        camera.setMinY((short) -0x100);
        camera.setMaxY((short) 4096);
        camera.setY((short) 3918);
        camera.setVerticalWrapEnabled(true, 4096);
        return player;
    }
}
