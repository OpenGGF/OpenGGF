package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Sonic2SpecialStagePreRollTest {

    private Rom rom;
    private Sonic2SpecialStageManager manager;

    @BeforeEach
    void bootSpecialStage() throws Exception {
        Path romPath = Path.of("s2.gen");
        assumeTrue(Files.isRegularFile(romPath), "s2.gen ROM required for pre-roll tests");

        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();

        rom = new Rom();
        assertTrue(rom.open(romPath.toAbsolutePath().toString()), "s2.gen should open");
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();

        GameServices.configuration()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration()
                .setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.initializeStage(0);
        provider.setLagCompensation(0);
        manager = provider.getManager();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
        if (rom != null) {
            rom.close();
        }
    }

    @Test
    void suppressesTrackSpeedDrawingAndBannerForRomPreRollWindow() {
        int initialBannerY = manager.getIntro().getBannerY();
        int preRollFrames = Sonic2SpecialStageIntro.PRE_ROLL_FRAMES;
        assertEquals(23, preRollFrames);

        for (int frame = 0; frame < preRollFrames; frame++) {
            Sonic2SpecialStageComparisonState before = manager.captureComparisonState();
            assertEquals(0, before.speedFactor(), "ROM current speed is zero at pre-roll frame " + frame);
            assertEquals(0, before.trackAnimFrame(), "track frame must stay zero before tick " + frame);
            assertEquals(0, before.trackFrameDelayCounter(), "track timer must stay zero before tick " + frame);
            assertEquals(0, before.drawingIndex(), "drawing index must stay zero before tick " + frame);
            assertTrue(manager.getIntro().isPreRollActive());

            manager.update();

            Sonic2SpecialStageComparisonState after = manager.captureComparisonState();
            assertEquals(0, after.speedFactor(), "pre-roll tick must not promote speed at frame " + frame);
            assertEquals(0, after.trackAnimFrame(), "pre-roll tick must not animate track at frame " + frame);
            assertEquals(0, after.trackFrameDelayCounter(), "pre-roll tick must not run track timer at frame " + frame);
            assertEquals(0, after.drawingIndex(), "pre-roll tick must not advance drawing index at frame " + frame);
            assertEquals(initialBannerY, manager.getIntro().getBannerY(),
                    "pre-roll tick must not start the DROP banner at frame " + frame);
        }

        assertFalse(manager.getIntro().isPreRollActive());
        assertEquals(Sonic2SpecialStageIntro.Phase.DROP, manager.getIntro().getCurrentPhase());

        manager.update();

        Sonic2SpecialStageComparisonState firstActive = manager.captureComparisonState();
        assertEquals(12, firstActive.speedFactor(), "first post-pre-roll tick promotes SS_New_Speed_Factor");
        assertEquals(1, firstActive.trackFrameDelayCounter(), "track timer starts at the phase boundary");
        assertEquals(1, firstActive.drawingIndex(), "drawing index starts at the phase boundary");
        assertEquals(initialBannerY + 1, manager.getIntro().getBannerY(), "DROP starts at the phase boundary");
    }

    @Test
    void rewindSnapshotRestoresPreRollPhaseAndTimer() {
        for (int frame = 0; frame < 7; frame++) {
            manager.update();
        }
        assertTrue(manager.getIntro().isPreRollActive());
        Sonic2SpecialStageSnapshot snapshot = manager.captureRewindSnapshot();

        for (int frame = 7; frame < Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();
        }
        assertFalse(manager.getIntro().isPreRollActive());

        manager.restoreRewindSnapshot(snapshot);

        assertTrue(manager.getIntro().isPreRollActive());
        assertEquals(0, manager.captureComparisonState().speedFactor());
        for (int frame = 7; frame < Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();
        }
        assertEquals(Sonic2SpecialStageIntro.Phase.DROP, manager.getIntro().getCurrentPhase(),
                "restored pre-roll timer should reach the same boundary");

        manager.update();

        assertEquals(12, manager.captureComparisonState().speedFactor(),
                "rewinding into PRE_ROLL must re-arm the one-shot initial speed promotion");
    }

    @Test
    void laterRomZeroSpeedIsNotRePromotedToInitialSpeed() throws Exception {
        for (int frame = 0; frame <= Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();
        }
        assertEquals(12, manager.captureComparisonState().speedFactor());

        // The ROM intentionally writes zero later in the stage (s2.asm:72418).
        manager.getTrackAnimator().setSpeedFactor(0);
        Sonic2SpecialStageSnapshot consumedPromotion = manager.captureRewindSnapshot();

        manager.update();

        assertEquals(0, manager.captureComparisonState().speedFactor(),
                "zero is a legitimate later ROM speed, not an initialization sentinel");

        manager.initialize(0);
        manager.restoreRewindSnapshot(consumedPromotion);
        manager.update();

        assertEquals(0, manager.captureComparisonState().speedFactor(),
                "rewind must restore that the initial promotion was already consumed");
    }
}
