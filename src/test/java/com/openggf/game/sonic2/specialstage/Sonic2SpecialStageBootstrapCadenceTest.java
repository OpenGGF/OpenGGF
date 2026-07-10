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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Sonic2SpecialStageBootstrapCadenceTest {

    private static final int STARTUP_WAIT_UPDATES = 10;
    private Rom rom;
    private Sonic2SpecialStageManager manager;

    @BeforeEach
    void bootSpecialStage() throws Exception {
        Path romPath = Path.of("s2.gen");
        assumeTrue(Files.isRegularFile(romPath), "s2.gen ROM required for bootstrap cadence tests");

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
    void fadeFromWhiteFreezesRuntimeThenRecurringMainPassPublishesOneObservationLater() {
        int[] activeObjectUpdates = {0};
        manager.getObjectManager().getActiveObjects().add(new Sonic2SpecialStageRing() {
            @Override
            public void update(
                    int currentTrackFrame,
                    boolean trackFlipped,
                    int speedFactor,
                    boolean drawingIndex4) {
                activeObjectUpdates[0]++;
            }
        });

        advanceThroughStartupRunObjects();

        assertEquals(22, Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES);
        assertEquals(Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE,
                manager.getIntro().getCurrentPhase());
        assertEquals(1, activeObjectUpdates[0],
                "the startup RunObjects pass executes the later-slot probe exactly once");

        Sonic2SpecialStageComparisonState frozen = manager.captureComparisonState();
        int frozenBannerY = manager.getIntro().getBannerY();
        int frozenSkydomeScroll = manager.getSkydomeScrollXForTest();
        int frozenVScroll = manager.getVScrollBGForTest();
        assertEquals(2, frozen.trackAnimFrame());
        assertEquals(4, frozen.trackFrameDelayCounter(),
                "f136 leaves the ROM duration timer at one, or elapsed four");

        for (int fadeUpdate = 1;
             fadeUpdate <= Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES;
             fadeUpdate++) {
            manager.update();

            Sonic2SpecialStageComparisonState after = manager.captureComparisonState();
            assertEquals(frozen.trackAnimFrame(), after.trackAnimFrame(),
                    "fade must freeze track frame at update " + fadeUpdate);
            assertEquals(frozen.trackFrameDelayCounter(), after.trackFrameDelayCounter(),
                    "fade must freeze track duration at update " + fadeUpdate);
            assertEquals(frozen.drawingIndex(), after.drawingIndex(),
                    "fade must freeze drawing index at update " + fadeUpdate);
            assertEquals(frozen.sonic(), after.sonic(),
                    "fade must freeze initialized Sonic at update " + fadeUpdate);
            assertEquals(frozen.tails(), after.tails(),
                    "fade must freeze initialized Tails at update " + fadeUpdate);
            assertEquals(frozenBannerY, manager.getIntro().getBannerY(),
                    "fade must not start the banner drop at update " + fadeUpdate);
            assertEquals(frozenSkydomeScroll, manager.getSkydomeScrollXForTest(),
                    "fade must freeze skydome scroll at update " + fadeUpdate);
            assertEquals(frozenVScroll, manager.getVScrollBGForTest(),
                    "fade must freeze vertical scroll at update " + fadeUpdate);
            assertEquals(1, activeObjectUpdates[0],
                    "fade must freeze active objects at update " + fadeUpdate);
        }

        assertEquals(Sonic2SpecialStageIntro.Phase.DROP, manager.getIntro().getCurrentPhase(),
                "the final fade wait transitions to DROP for the next logical update");

        manager.update();

        Sonic2SpecialStageComparisonState firstDropVint = manager.captureComparisonState();
        assertEquals(1, firstDropVint.drawingIndex());
        assertEquals(0, firstDropVint.trackFrameDelayCounter());
        assertEquals(2, firstDropVint.trackAnimFrame());
        assertEquals(frozen.sonic(), firstDropVint.sonic(),
                "the f160 VInt observation precedes its recurring RunObjects pass");
        assertEquals(frozen.tails(), firstDropVint.tails());
        assertEquals(frozenBannerY, manager.getIntro().getBannerY(),
                "the f160 VInt observation precedes the banner object pass");
        assertEquals(1, activeObjectUpdates[0],
                "the f160 VInt observation precedes later-slot active objects");

        manager.update();

        Sonic2SpecialStageComparisonState publishedPriorMainPass = manager.captureComparisonState();
        assertEquals(2, publishedPriorMainPass.drawingIndex());
        assertEquals(1, publishedPriorMainPass.trackFrameDelayCounter());
        assertNotEquals(frozen.sonic(), publishedPriorMainPass.sonic(),
                "the next executed update publishes the prior VInt's RunObjects result");
        assertEquals(frozenBannerY + 1, manager.getIntro().getBannerY());
        assertEquals(2, activeObjectUpdates[0]);

        manager.update(); // drawing index 3
        manager.update(); // drawing index 4
        manager.update(); // drawing index 0: current SSTrack_Draw advances
        Sonic2SpecialStageComparisonState currentDrawWrap = manager.captureComparisonState();
        assertEquals(0, currentDrawWrap.drawingIndex());
        assertEquals(3, currentDrawWrap.trackAnimFrame(),
                "current index-zero SSTrack_Draw is synchronous even while RunObjects is pending");
    }

    @Test
    void jumpPressWhollyInsideFadeDoesNotLeakIntoFirstRecurringMainPass() {
        advanceThroughStartupRunObjects();

        manager.handleInput(0x10, 0x10);
        manager.update();
        manager.handleInput(0, 0);
        for (int update = 1; update < Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES; update++) {
            manager.update();
        }
        assertEquals(Sonic2SpecialStageIntro.Phase.DROP, manager.getIntro().getCurrentPhase());

        manager.update();
        manager.update();

        assertEquals(Sonic2SpecialStagePlayer.RoutineState.NORMAL,
                manager.getSonicPlayer().getRoutine(),
                "a fade-only press edge must be discarded before DROP schedules input");
        assertFalse(manager.getSonicPlayer().isJumping());
    }

    @Test
    void lagSkippedFadePressReleasedBeforeNextVintDoesNotLeakIntoDrop() {
        advanceThroughStartupRunObjects();
        for (int update = 0; update < 20; update++) {
            manager.handleInput(0, 0);
            manager.update();
        }
        assertEquals(20, manager.captureRewindSnapshot().intro.phaseTimer());

        manager.setLagCompensation(0.5);
        manager.handleInput(0, 0);
        manager.update(); // executes fade update 21 and primes the lag accumulator

        manager.handleInput(0x10, 0x10);
        manager.update(); // skipped: no VInt/control copy occurred

        Sonic2SpecialStageSnapshot skipped = manager.captureRewindSnapshot();
        assertEquals(0, skipped.pressedButtons,
                "a skipped physical press edge must not survive to an executed VInt");
        assertEquals(0, skipped.previousPhysicalPressedButtons,
                "lag skip preserves the last executed VInt's sampled input");
        assertEquals(0, skipped.pendingMainPressedButtons);

        manager.handleInput(0, 0);
        manager.update(); // release is sampled by final fade update
        assertEquals(Sonic2SpecialStageIntro.Phase.DROP, manager.getIntro().getCurrentPhase());

        manager.setLagCompensation(0);
        manager.update(); // first DROP VInt
        manager.update(); // publish its pending RunObjects pass

        assertEquals(Sonic2SpecialStagePlayer.RoutineState.NORMAL,
                manager.getSonicPlayer().getRoutine());
        assertFalse(manager.getSonicPlayer().isJumping());
    }

    @Test
    void lagSkippedFadePressHeldThroughNextVintProducesExactlyOneLogicalEdge() {
        advanceThroughStartupRunObjects();
        for (int update = 0; update < 20; update++) {
            manager.handleInput(0, 0);
            manager.update();
        }

        manager.setLagCompensation(0.5);
        manager.handleInput(0, 0);
        manager.update(); // executes fade update 21 and primes the lag accumulator

        manager.handleInput(0x10, 0x10);
        manager.update(); // skipped physical edge; held state remains current

        manager.handleInput(0x10, 0);
        manager.update(); // next executed VInt must synthesize the held transition
        Sonic2SpecialStageSnapshot sampledHeld = manager.captureRewindSnapshot();
        assertEquals(0x10, sampledHeld.previousPhysicalPressedButtons);
        assertEquals(0x10, sampledHeld.previousPhysicalHeldButtons);

        manager.setLagCompensation(0);
        manager.handleInput(0x10, 0);
        manager.update(); // schedules the synthesized edge, then observes no repeat
        Sonic2SpecialStageSnapshot scheduledEdge = manager.captureRewindSnapshot();
        assertEquals(0x10, scheduledEdge.pendingMainPressedButtons);
        assertEquals(0, scheduledEdge.previousPhysicalPressedButtons,
                "unchanged held input must not synthesize a second press");

        manager.handleInput(0x10, 0);
        manager.update(); // publishes the one scheduled jump edge
        Sonic2SpecialStageSnapshot afterJump = manager.captureRewindSnapshot();
        assertEquals(Sonic2SpecialStagePlayer.RoutineState.JUMPING,
                manager.getSonicPlayer().getRoutine());
        assertEquals(0, afterJump.pendingMainPressedButtons,
                "the following pending pass must not repeat the held jump edge");
    }

    @Test
    void currentVintRightInputReachesPlayersAndTailsHistoryOnFollowingCopyWaitCycle() {
        advanceThroughStartupRunObjects();
        for (int update = 0; update < Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES; update++) {
            manager.handleInput(0, 0);
            manager.update();
        }

        manager.handleInput(0x08, 0);
        manager.update(); // f160 VInt: schedules the previously sampled neutral word
        assertEquals(0, manager.getSonicPlayer().getInertia());

        manager.handleInput(0x08, 0);
        manager.update(); // publishes f160's neutral logical word

        assertEquals(0, manager.getSonicPlayer().getInertia(),
                "current VInt input must not reach the prior pending RunObjects pass");
        assertEquals(0, manager.captureRewindSnapshot().tailsCtrlRecordBuf[0],
                "Tails history must receive the same prior logical word as Sonic");

        manager.handleInput(0x08, 0);
        manager.update(); // publishes the RIGHT word copied by the prior cycle

        assertTrue(manager.getSonicPlayer().getInertia() < 0);
        assertEquals(0x08, manager.captureRewindSnapshot().tailsCtrlRecordBuf[0]);
    }

    @Test
    void pendingRunObjectsUsesPlayerThenBannerThenDynamicSlotOrder() {
        advanceThroughStartupRunObjects();
        for (int update = 0; update < Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES; update++) {
            manager.update();
        }

        int initialBannerY = manager.getIntro().getBannerY();
        List<String> dynamicObservations = new ArrayList<>();
        manager.getObjectManager().getActiveObjects().add(new Sonic2SpecialStageRing() {
            @Override
            public void update(
                    int currentTrackFrame,
                    boolean trackFlipped,
                    int speedFactor,
                    boolean drawingIndex4) {
                dynamicObservations.add(manager.getSonicPlayer().getSSYPos()
                        + ":" + manager.getIntro().getBannerY());
            }
        });

        manager.update(); // schedules first recurring RunObjects pass
        manager.update(); // players, fixed banner, then later dynamic slot

        assertEquals(List.of("110:" + (initialBannerY + 1)), dynamicObservations);
    }

    @Test
    void rewindDuringFadeRestoresTimerAndReplaysTheBoundaryDeterministically() {
        advanceThroughStartupRunObjects();
        Sonic2SpecialStageComparisonState frozen = manager.captureComparisonState();
        int frozenBannerY = manager.getIntro().getBannerY();

        for (int update = 0; update < 7; update++) {
            manager.update();
        }
        Sonic2SpecialStageSnapshot rewindPoint = manager.captureRewindSnapshot();
        assertEquals(Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE,
                rewindPoint.intro.currentPhase());
        assertEquals(7, rewindPoint.intro.phaseTimer());

        for (int update = 7; update < Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES; update++) {
            manager.update();
        }
        assertEquals(Sonic2SpecialStageIntro.Phase.DROP, manager.getIntro().getCurrentPhase());
        assertEquals(frozen, manager.captureComparisonState());
        assertEquals(frozenBannerY, manager.getIntro().getBannerY());

        manager.update();
        Sonic2SpecialStageComparisonState firstBoundary = manager.captureComparisonState();
        int firstBoundaryBannerY = manager.getIntro().getBannerY();

        manager.restoreRewindSnapshot(rewindPoint);

        Sonic2SpecialStageSnapshot restored = manager.captureRewindSnapshot();
        assertEquals(Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE,
                manager.getIntro().getCurrentPhase());
        assertEquals(7, restored.intro.phaseTimer());

        for (int update = 7; update < Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES; update++) {
            manager.update();
        }
        assertEquals(frozen, manager.captureComparisonState());
        assertEquals(frozenBannerY, manager.getIntro().getBannerY());

        manager.update();

        assertEquals(firstBoundary, manager.captureComparisonState());
        assertEquals(firstBoundaryBannerY, manager.getIntro().getBannerY());
    }

    @Test
    void rewindRestoresScheduledMainPassInputsAndDrawSlice() {
        advanceThroughStartupRunObjects();
        for (int update = 0; update < Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES; update++) {
            manager.handleInput(
                    update == Sonic2SpecialStageIntro.FADE_FROM_WHITE_FRAMES - 1 ? 0x08 : 0,
                    0);
            manager.update();
        }

        manager.handleInput(0, 0);
        manager.update();
        Sonic2SpecialStageSnapshot scheduled = manager.captureRewindSnapshot();
        assertTrue(scheduled.recurringMainPassPending);
        assertEquals(0x08, scheduled.pendingMainHeldButtons);
        assertEquals(0x08, scheduled.pendingMainPressedButtons,
                "held transition at the last executed fade VInt synthesizes one edge");
        assertFalse(scheduled.pendingMainCheckpointStep);
        assertEquals(0, scheduled.previousPhysicalHeldButtons);
        assertEquals(0, scheduled.pressedButtons,
                "scheduling consumes the live press edge immediately");

        manager.handleInput(0, 0);
        manager.update();
        Sonic2SpecialStageComparisonState firstReplay = manager.captureComparisonState();
        int firstReplayBannerY = manager.getIntro().getBannerY();

        manager.restoreRewindSnapshot(scheduled);

        assertTrue(manager.captureRewindSnapshot().recurringMainPassPending);
        manager.handleInput(0, 0);
        manager.update();
        assertEquals(firstReplay, manager.captureComparisonState());
        assertEquals(firstReplayBannerY, manager.getIntro().getBannerY());
    }

    @Test
    void reinitializeRestoresPreRollAndClearsFadeProgress() throws Exception {
        advanceThroughStartupRunObjects();
        for (int update = 0; update < 5; update++) {
            manager.update();
        }
        assertEquals(Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE,
                manager.getIntro().getCurrentPhase());

        manager.initialize(0);

        Sonic2SpecialStageSnapshot reset = manager.captureRewindSnapshot();
        assertEquals(Sonic2SpecialStageIntro.Phase.PRE_ROLL, reset.intro.currentPhase());
        assertEquals(0, reset.intro.phaseTimer());
        assertEquals(0, reset.drawingIndex);
        assertEquals(0, reset.trackAnimator.currentFrameInSegment());
        assertFalse(reset.trackAnimator.speedChangePending());
        assertFalse(reset.recurringMainPassPending);
        assertEquals(0, reset.pendingMainHeldButtons);
        assertEquals(0, reset.pendingMainPressedButtons);
        assertFalse(reset.pendingMainCheckpointStep);
        assertEquals(0, reset.previousPhysicalHeldButtons);
        assertEquals(0, reset.previousPhysicalPressedButtons);
        assertEquals(0, reset.previousPhysicalP2HeldButtons);
        assertEquals(0, reset.previousPhysicalP2LogicalButtons);
        assertTrue(manager.getPlayers().stream().noneMatch(Sonic2SpecialStagePlayer::isSpawned));
    }

    private void advanceThroughStartupRunObjects() {
        int updates = Sonic2SpecialStageIntro.PRE_ROLL_FRAMES + STARTUP_WAIT_UPDATES;
        for (int update = 0; update < updates; update++) {
            manager.update();
        }
        assertEquals(Sonic2SpecialStageManager.PlayerBootstrapPhase.INITIALIZED,
                manager.captureRewindSnapshot().playerBootstrapPhase);
        assertEquals(Sonic2SpecialStagePlayer.RoutineState.NORMAL,
                manager.getSonicPlayer().getRoutine());
    }
}
