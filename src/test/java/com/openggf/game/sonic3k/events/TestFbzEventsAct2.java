package com.openggf.game.sonic3k.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Locked-on oracle for FBZ2_ScreenInit/Event and FBZ2_BackgroundInit/Event. */
class TestFbzEventsAct2 {
    @Test
    void ordinaryScreenInitStartsAtNativePrebossStages() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct2Screen(0x2B2F);
        events.initializeAct2Background(0x2B2F);
        assertEquals(0, events.getAct2ForegroundStage());
        assertEquals(0, events.getBossBackgroundStage());
        assertFalse(events.isBackgroundOutdoor());
    }

    @Test
    void ordinaryForegroundRegionUsesInclusiveRectangleAndAf2B0eHysteresis() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct2Screen(0);
        events.updateAct2ScreenEvent(0xD80, 0xA00, false, 0x2B2F);
        assertEquals(4, events.getForegroundLayoutRegion());

        events.updateAct2ScreenEvent(0xD80, 0xB0F, false, 0x2B2F);
        assertTrue(events.isForegroundOutdoor());
        events.updateAct2ScreenEvent(0xD80, 0xAF3, false, 0x2B2F);
        assertTrue(events.isForegroundOutdoor(), "$AF3 remains outdoors");
        events.updateAct2ScreenEvent(0xD80, 0xAF2, false, 0x2B2F);
        assertFalse(events.isForegroundOutdoor(), "$AF2 returns indoors");

        events.updateAct2ScreenEvent(0x1301, 0xA40, false, 0x2B2F);
        assertEquals(0, events.getForegroundLayoutRegion(), "leaving the inclusive range resets Events_bg+$00");
    }

    @Test
    void foregroundCopyIsSuppressedAcrossE00Through1280AndUsesExactFourteenByFourWindow() {
        Sonic3kFBZEvents events = fresh();
        events.setForegroundLayoutRegion(4);
        assertTrue(events.updateAct2ScreenEvent(0xE00, 0xB80, false, 0x2B30).isEmpty());
        assertFalse(events.isForegroundOutdoor());
        assertEquals(0, events.getAct2ForegroundStage(),
                "the inclusive suppression return does not reach FBZ2_NoLayoutMod's $2B30 check");
        assertTrue(events.updateAct2ScreenEvent(0x1280, 0xB80, false, 0x2B30).isEmpty());
        assertFalse(events.isForegroundOutdoor());
        assertEquals(0, events.getAct2ForegroundStage());
        assertEquals(java.util.List.of(new Sonic3kFBZEvents.LayoutCopy(112, 4, 26, 20, 14, 4)),
                events.updateAct2ScreenEvent(0xD80, 0xB80, false, 0x2B30));
        assertEquals(new Sonic3kFBZEvents.LayoutCopy(112, 0, 26, 20, 14, 4),
                Sonic3kFBZEvents.act2LayoutCopy(false));
        assertEquals(0, events.getAct2ForegroundStage(),
                "Refresh_PlaneScreenDirect returns before the camera threshold");
    }

    @Test
    void foregroundDeathGateFreezesLayoutAndPrebossStage() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct2Screen(0);
        events.updateAct2ScreenEvent(0xE00, 0xA40, true, 0x2B30);
        assertEquals(0, events.getForegroundLayoutRegion());
        assertEquals(0, events.getAct2ForegroundStage());
    }

    @Test
    void crossing2B30AdvancesExactlyOnceToBossSetupStage() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct2Screen(0);
        events.updateAct2ScreenEvent(0, 0, false, 0x2B2F);
        assertEquals(0, events.getAct2ForegroundStage());
        events.updateAct2ScreenEvent(0, 0, false, 0x2B30);
        assertEquals(4, events.getAct2ForegroundStage());
        events.updateAct2ScreenEvent(0, 0, false, 0x2B31);
        assertEquals(4, events.getAct2ForegroundStage());
    }

    @Test
    void cameraThresholdIsReachableOnlyFromNoLayoutMod() {
        Sonic3kFBZEvents stateZero = fresh();
        stateZero.updateAct2ScreenEvent(0, 0, false, 0x2B30);
        assertEquals(4, stateZero.getAct2ForegroundStage());

        Sonic3kFBZEvents active = fresh();
        active.setForegroundLayoutRegion(4);
        active.updateAct2ScreenEvent(0x1000, 0xA40, false, 0x2B30);
        assertEquals(0, active.getAct2ForegroundStage(),
                "FBZ2_LayoutMod1 exits through DrawTilesAsYouMove");

        Sonic3kFBZEvents leaving = fresh();
        leaving.setForegroundLayoutRegion(4);
        leaving.updateAct2ScreenEvent(0x1301, 0xA40, false, 0x2B30);
        assertEquals(0, leaving.getForegroundLayoutRegion());
        assertEquals(0, leaving.getAct2ForegroundStage(),
                "FBZ1Screen_CheckInRange returns after clearing the active range");
    }

    @Test
    void ordinaryInitializationPublishesNativeEffectsInInstructionOrder() {
        Sonic3kFBZEvents events = fresh();
        RecordingInitializationEffects effects = new RecordingInitializationEffects(events);

        events.initializeAct2Screen(0x2B2F, effects);
        events.initializeAct2Background(0x2B2F, effects);

        assertEquals(List.of(
                "reset-actual", "refresh-full",
                "deform", "reset-effective",
                "refresh-full", "apply-deformation"), effects.operations);
    }

    @Test
    void lateBackgroundInitializationCopiesTopLineAfterSelectingBossStage() {
        Sonic3kFBZEvents events = fresh();
        RecordingInitializationEffects effects = new RecordingInitializationEffects(events);
        events.initializeAct2Background(0x2C40, effects);
        assertEquals(List.of("copy-bg-lines@16"), effects.operations);
    }

    @Test
    void firstBackgroundEventCopiesLinesBeforeAdvancingToNormalStage() {
        Sonic3kFBZEvents events = fresh();
        RecordingInitializationEffects effects = new RecordingInitializationEffects(events);
        events.updateAct2BackgroundEvent(0, 0, false, effects);
        assertEquals(List.of("copy-bg-lines@0"), effects.operations);
        assertEquals(4, events.getBossBackgroundStage());
    }

    @Test
    void normalBackgroundInitializesAndChangesAtExactA40Threshold() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct2Background(0);
        events.setForegroundLayoutRegion(4);
        events.updateAct2BackgroundEvent(0x1000, 0xA3F, false);
        assertEquals(4, events.getBossBackgroundStage());
        assertFalse(events.isBackgroundOutdoor());

        events.updateAct2BackgroundEvent(0x1000, 0xA40, false);
        assertEquals(12, events.getBossBackgroundStage());
        assertEquals(Sonic3kFBZEvents.RedrawDirection.BOTTOM_UP, events.getBackgroundRedrawDirection());
        assertTrue(events.isBackgroundOutdoor());
        assertEquals(1, events.getBackgroundRedrawProgress(), "first row is drawn on the trigger frame");
        for (int i = 1; i < 16; i++) events.updateAct2BackgroundEvent(0x1000, 0xA41, false);
        assertEquals(4, events.getBossBackgroundStage());
        assertEquals(Sonic3kFBZEvents.RedrawDirection.NONE, events.getBackgroundRedrawDirection());

        events.updateAct2BackgroundEvent(0x1000, 0xA40, false);
        assertEquals(8, events.getBossBackgroundStage());
        assertEquals(Sonic3kFBZEvents.RedrawDirection.TOP_DOWN, events.getBackgroundRedrawDirection());
        assertFalse(events.isBackgroundOutdoor());
    }

    @Test
    void normalBackgroundDeathGateFreezesChecksButActiveRedrawContinues() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct2Background(0);
        events.setForegroundLayoutRegion(4);
        events.updateAct2BackgroundEvent(0x1000, 0xA40, true);
        assertEquals(4, events.getBossBackgroundStage(), "init still falls through before the dying return");
        assertFalse(events.isBackgroundOutdoor());

        events.updateAct2BackgroundEvent(0x1000, 0xA41, false);
        assertEquals(1, events.getBackgroundRedrawProgress());
        events.updateAct2BackgroundEvent(0x1000, 0xA41, true);
        assertEquals(2, events.getBackgroundRedrawProgress());
    }

    @Test
    void deathCheckpointRestartClearsEventsAndReconstructsOnlyFromRestoredCameraX() {
        Sonic3kFBZEvents ordinary = fresh();
        ordinary.initializeAct2Screen(0x2C3F);
        ordinary.initializeAct2Background(0x2C3F);
        assertEquals(0, ordinary.getAct2ForegroundStage());
        assertEquals(0, ordinary.getBossBackgroundStage());
        assertEquals(0, ordinary.getForegroundLayoutRegion());

        Sonic3kFBZEvents late = fresh();
        late.initializeAct2Screen(0x2C40);
        late.initializeAct2Background(0x2C40);
        assertEquals(4, late.getAct2ForegroundStage());
        assertEquals(16, late.getBossBackgroundStage());
        assertEquals(0, late.getForegroundLayoutRegion());
        assertTrue(late.isScreenShakeActive());
    }

    @Test
    void rewindTraversalSnapshotRoundTripsActiveRedrawWords() {
        Sonic3kFBZEvents source = fresh();
        source.initializeAct2Screen(0x1800);
        source.initializeAct2Background(0x1800);
        source.setForegroundLayoutRegion(4);
        source.updateAct2BackgroundEvent(0xD80, 0xA40, false);

        Sonic3kFBZEvents restored = fresh();
        restored.restoreAct2TraversalState(source.captureAct2TraversalState());
        assertEquals(source.captureAct2TraversalState(), restored.captureAct2TraversalState());
    }

    private static Sonic3kFBZEvents fresh() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        return events;
    }

    private static final class RecordingInitializationEffects
            implements Sonic3kFBZEvents.Act2InitializationEffects {
        private final Sonic3kFBZEvents events;
        private final List<String> operations = new ArrayList<>();

        private RecordingInitializationEffects(Sonic3kFBZEvents events) {
            this.events = events;
        }

        @Override public void resetActualTileOffsets() { operations.add("reset-actual"); }
        @Override public void refreshPlaneFull() { operations.add("refresh-full"); }
        @Override public void copyTopBackgroundLines() {
            operations.add("copy-bg-lines@" + events.getBossBackgroundStage());
        }
        @Override public void deformNormalBackground() { operations.add("deform"); }
        @Override public void resetEffectiveTileOffsets() { operations.add("reset-effective"); }
        @Override public void applyDeformation() { operations.add("apply-deformation"); }
    }
}
