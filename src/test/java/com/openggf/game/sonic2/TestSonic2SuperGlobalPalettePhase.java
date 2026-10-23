package com.openggf.game.sonic2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** PalCycle_Load is global level work; Sonic_Super belongs only to Obj01_Control. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SuperGlobalPalettePhase {
    @AfterEach
    void clearConfiguration() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private static HeadlessTestFixture transforming() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withFreshLevelStartLifecycle().build();
        GameServices.gameState().configureSpecialStageProgress(7, 7);
        for (int emerald = 0; emerald < 7; emerald++) GameServices.gameState().markEmeraldCollected(emerald);
        fixture.sprite().setRingCount(100);
        fixture.sprite().setAir(true);
        fixture.sprite().setJumping(true);
        fixture.sprite().setYSpeed((short) 0x28);
        fixture.sprite().getSuperStateController().checkTransformationBeforeMove();
        assertEquals(SuperState.TRANSFORMING, owner(fixture).getState(), "ordinary airborne gate");
        return fixture;
    }

    private static Sonic2SuperStateController owner(HeadlessTestFixture fixture) {
        return assertInstanceOf(Sonic2SuperStateController.class, fixture.sprite().getSuperStateController());
    }

    private static void activate(HeadlessTestFixture fixture) {
        for (int frame = 0; frame < 80 && owner(fixture).getState() != SuperState.SUPER; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertEquals(SuperState.SUPER, owner(fixture).getState());
    }

    private static long paletteWords() {
        var palette = GameServices.level().getCurrentLevel().getPalette(0);
        long packed = 0;
        for (int color = 2; color < 6; color++) {
            var value = palette.getColor(color);
            packed = (packed << 12) | ((value.r & 0xF0L) << 4) | (value.g & 0xF0L) | ((value.b & 0xFF) >>> 4);
        }
        return packed;
    }

    @Test
    void lastFadeTickAnimatesAndExecutesObjectsBeforeGlobalControlReleaseThenReplays() throws Exception {
        var fixture = transforming();
        var player = fixture.sprite();
        var owner = owner(fixture);
        for (int frame = 0; frame < 80; frame++) {
            var state = owner.captureRewindState();
            if (state.paletteFrame() == 0x28 && state.paletteTimer() == 0) break;
            fixture.stepFrame(false, false, false, false, false);
        }
        assertEquals(0x28, owner.getPaletteFrame());
        assertEquals(0, owner.captureRewindState().paletteTimer());
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(before);
            int[] observations = {0};
            var driver = new RecordingFrameDriver(player);
            driver.setStepWrapper((section, step) -> {
                step.run();
                if (section.equals("physics") || section.equals("objects")) {
                    assertEquals(SuperState.TRANSFORMING, owner.getState(), section);
                    assertEquals(0x1F, player.getForcedAnimationId(), section);
                    assertEquals(0x1F, player.getAnimationId(), section + " retains transformation animation");
                    assertTrue(player.isObjectControlled(), section);
                    assertEquals(0, owner.captureRewindState().paletteTimer(), section);
                    observations[0]++;
                }
            });
            driver.stepFrame(false, false, false, false, false);
            assertEquals(2, observations[0], "both production phases ran before the global palette owner");
            assertEquals(SuperState.SUPER, owner.getState());
            assertFalse(player.isObjectControlled());
            assertEquals(-1, player.getForcedAnimationId());
            assertEquals(3, owner.captureRewindState().paletteTimer(), "native final fade reload survives activation");
            assertEquals(0x30, owner.getPaletteFrame());
            for (int tick = 0; tick < 3; tick++) fixture.stepFrame(false, false, false, false, false);
            assertEquals(0x30, owner.getPaletteFrame(), "the next three global calls only count down");
            fixture.stepFrame(false, false, false, false, false);
            assertEquals(0x38, owner.getPaletteFrame(), "first cycle entry follows four global calls");
            assertEquals(7, owner.captureRewindState().paletteTimer());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void revertedPaletteKeepsFadingAfterRealAirDamageOrDeathAndRestore(boolean death) throws Exception {
        var fixture = transforming();
        activate(fixture);
        var player = fixture.sprite();
        var owner = owner(fixture);
        player.currentLevelState().pauseTimer();
        fixture.stepFrame(false, false, false, false, false); // real Sonic_Super reversion
        assertEquals(SuperState.NORMAL, owner.getState());
        assertEquals(1, player.getInvincibleFrames());
        fixture.stepFrame(false, false, false, false, false); // native one-frame display grace
        assertEquals(0, player.getInvincibleFrames());
        NativePositionOps.writeXPosResetSubpixel(player, 0x300);
        NativePositionOps.writeYPosResetSubpixel(player, 0x100);
        player.setAir(true);
        player.giveSpeedShoes(8);
        if (death) assertTrue(player.applyPitDeath());
        else assertTrue(player.applyHurt(player.getCentreX() - 16));
        assertEquals(death, player.getDead());
        assertEquals(!death, player.isHurt());
        assertEquals(SuperState.NORMAL, owner.getState());
        assertEquals(2, owner.getPaletteState());
        var initial = owner.captureRewindState();
        long initialPalette = paletteWords();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        Object expectedState = null;
        long expectedPalette = 0;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(before);
            for (int frame = 0; frame < 8; frame++) fixture.stepFrame(false, false, false, false, false);
            assertEquals(0xC00, com.openggf.sprites.playable.PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player),
                    "hurt/death preserve the last speed writer");
            assertEquals(0xC00, player.getMax());
            assertEquals(0x18, player.getRunAccel());
            assertEquals(8, player.captureRewindState().playerExtra().speedShoesRemainingTicks(),
                    "hurt/death omit the display expiry owner");
            assertEquals(initial.ringDrainCounter(), owner.captureRewindState().ringDrainCounter(),
                    "Hurt/Dead do not call Sonic_Super");
            assertEquals(initial.paletteFrame() - 16, owner.getPaletteFrame(),
                    "global PalCycle_SuperSonic still executes two fade entries");
            assertNotEquals(initialPalette, paletteWords(), "the global fade publishes actual palette cells");
            if (replay == 0) {
                expectedState = owner.captureRewindState();
                expectedPalette = paletteWords();
            } else {
                assertEquals(expectedState, owner.captureRewindState());
                assertEquals(expectedPalette, paletteWords(), "restored global palette replays exactly");
            }
        }
    }

    @Test
    void activeSuperDeathCyclesPaletteWithoutRunningControlTimerOrPausedTimerRevert() throws Exception {
        var fixture = transforming();
        activate(fixture);
        var player = fixture.sprite();
        var owner = owner(fixture);
        NativePositionOps.writeYPosResetSubpixel(player, 0x100);
        assertTrue(player.applyPitDeath());
        player.currentLevelState().pauseTimer();
        var before = owner.captureRewindState();
        int rings = player.getRingCount();
        for (int frame = 0; frame < 8; frame++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(SuperState.SUPER, owner.getState(), "dead routine omits the Sonic_Super paused-timer test");
        assertEquals(rings, player.getRingCount());
        assertEquals(before.ringDrainCounter(), owner.captureRewindState().ringDrainCounter());
        assertNotEquals(before.paletteFrame(), owner.getPaletteFrame(), "global cycle survives dead dispatch");
    }
}
