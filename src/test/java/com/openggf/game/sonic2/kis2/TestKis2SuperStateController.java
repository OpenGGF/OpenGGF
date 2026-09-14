package com.openggf.game.sonic2.kis2;

import com.openggf.level.Palette;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestKis2SuperStateController {
    @org.junit.jupiter.api.BeforeEach void openGameplay() {
        com.openggf.game.GameModuleRegistry.setCurrent(new com.openggf.game.sonic2.Sonic2GameModule());
        com.openggf.tests.TestEnvironment.activeGameplayMode();
        com.openggf.game.GameServices.level().resetLevelGamestate(
                com.openggf.game.GameModuleRegistry.getCurrent().createLevelState());
    }

    @Test
    void chipCycleWritesOnlyTheThreeRomOwnedColoursAndWrapsWithLongPause() {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var controller = new Kis2SuperStateController(new Knuckles("knuckles", (short) 0, (short) 0), dump);
        Palette palette = new Palette();
        palette.getColor(4).fromSegaFormat(new byte[]{0x00, (byte) 0x80}, 0);
        controller.writePaletteFrame(palette, 0, false);
        assertEquals(0x428, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(2)));
        assertEquals(0x64E, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(3)));
        assertEquals(0xA6E, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(5)));
        assertEquals(0x80, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(4)));
        controller.writePaletteFrame(palette, 0, true);
        assertEquals(0x206, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(2)));
        assertEquals(0x20C, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(3)));
        assertEquals(0x64E, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(5)));
    }

    @Test
    void superProfileKeepsNormalJumpAndAnimationThresholdAndSeedsRingTimer() {
        assertEquals(0x800, Kis2Physics.SUPER_KNUCKLES.max());
        assertEquals(0x18, Kis2Physics.SUPER_KNUCKLES.runAccel());
        assertEquals(0xC0, Kis2Physics.SUPER_KNUCKLES.runDecel());
        assertEquals(0x600, Kis2Physics.SUPER_KNUCKLES.jump());
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var controller = new Kis2SuperStateController(new Knuckles("knuckles", (short) 0, (short) 0), dump);
        assertFalse(controller.usesAutomaticJumpTrigger());
        assertTrue(controller.usesExplicitAirAbilityTrigger());
        assertEquals(60, controller.getInitialRingDrainCounter());
        assertEquals(0x600, controller.getSuperRunSpeedThreshold());
    }
    @Test
    void transformWaitsSixteenPassesThenCyclesAndRewindsItsTimer() {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var player = new Knuckles("knuckles", (short) 0, (short) 0);
        player.addRings(100);
        player.setSuperSonic(true);
        var controller = new Kis2SuperStateController(player, dump);
        controller.restoreRewindState(new com.openggf.sprites.playable.SuperStateController.RewindState(
                com.openggf.sprites.playable.SuperState.TRANSFORMING, 60, 1, 0, 15, 0, -1, -1L, -1L));
        for (int i = 0; i < 15; i++) controller.update();
        assertEquals(com.openggf.sprites.playable.SuperState.TRANSFORMING, controller.getState());
        controller.update();
        assertEquals(com.openggf.sprites.playable.SuperState.SUPER, controller.getState());
        assertEquals(3, controller.captureRewindState().paletteTimer());
        assertEquals(100, player.getRingCount(), "KiS2 seeds the drain counter at 60");
        var snapshot = controller.captureRewindState();
        for (int i = 0; i < 4; i++) controller.update();
        var advanced = controller.captureRewindState();
        assertEquals(6, advanced.paletteFrame());
        controller.restoreRewindState(snapshot);
        for (int i = 0; i < 4; i++) controller.update();
        assertEquals(advanced, controller.captureRewindState(), "replay restores cycle and ring timing");
        for (int i = 0; i < 27; i++) controller.update();
        assertEquals(0, controller.captureRewindState().paletteFrame());
        assertEquals(14, controller.captureRewindState().paletteTimer());
        controller.debugDeactivate();
        controller.update();
        assertFalse(controller.isSuper());
        assertEquals(0, controller.captureRewindState().paletteState());
        assertEquals(Kis2Physics.KNUCKLES, player.getPhysicsProfile());
    }
}
