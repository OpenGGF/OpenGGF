package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.game.timing.RomWorkBudgetScheduler;
import com.openggf.level.LevelManager;
import com.openggf.trace.timing.HardwareCompletionEdge;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLevelFrameHardwareTimingBoundaries {

    @Test
    void fullFrameServicesCanonicalBoundariesAndObservesAfterService() {
        List<String> events = new ArrayList<>();
        HardwareTimingService timing = new HardwareTimingService(
                new RomWorkBudgetScheduler(Map.of(
                        HardwareServiceBoundary.VINT_SERVICE, 1,
                        HardwareServiceBoundary.PRE_MAIN_LOOP, 1,
                        HardwareServiceBoundary.POST_OBJECTS, 1)));
        HardwareWorkHandle handle = timing.submit(submission(3));
        LevelFrameContext context = context(timing, boundary ->
                events.add(boundary + ":ready=" + timing.isReady(handle)));
        LevelManager level = mock(LevelManager.class);
        Camera camera = mock(Camera.class);

        LevelFrameResult result = LevelFrameStep.execute(
                context,
                level,
                camera,
                () -> events.add("physics"),
                (name, step) -> {
                    events.add(name + "-start");
                    step.run();
                    events.add(name + "-end");
                });

        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result);
        assertEquals(List.of(
                        "VINT_SERVICE:ready=false",
                        "PRE_MAIN_LOOP:ready=false",
                        "objects-start",
                        "objects-end",
                        "physics-start",
                        "physics"),
                events.subList(0, 6));
    }

    @Test
    void schemaTwoDirectReadinessIsVisibleToSameFrameObjectsAfterPreBoundary() {
        HardwareTimingService timing = new HardwareTimingService(
                RomWorkBudgetScheduler.oneWorkUnitAt(HardwareServiceBoundary.PRE_MAIN_LOOP));
        var authority = timing.beginRecordedAdmission(Map.of(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                com.openggf.game.timing.HardwareReadinessAdmissionPolicy.RECORDED,
                HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,
                com.openggf.game.timing.HardwareReadinessAdmissionPolicy.RECORDED));
        HardwareWorkSubmission submission = submission(
                HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 1);
        HardwareCompletionEdge edge = new HardwareCompletionEdge(
                0, HardwareServiceBoundary.PRE_MAIN_LOOP,
                HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 0,
                com.openggf.game.timing.HardwareSubmissionFingerprint.compute(submission));
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(authority);
        port.install(new HardwareTimingSchedule(2, List.of(edge)));
        HardwareWorkHandle direct = timing.submit(submission);
        port.beginRawFrame(0);
        LevelFrameContext context = context(timing,
                new TraceHardwareTimingBoundaryObserver(port));

        LevelFrameStep.execute(context, mock(LevelManager.class), mock(Camera.class), () -> { },
                (name, step) -> {
                    if ("objects".equals(name)) {
                        assertTrue(timing.isReady(direct));
                    }
                    step.run();
                });
    }

    @Test
    void vblankOnlyPathEmitsOnlyVintService() {
        List<HardwareServiceBoundary> boundaries = new ArrayList<>();
        HardwareTimingService timing = new HardwareTimingService();
        LevelFrameContext context = context(timing, boundaries::add);

        LevelFrameStep.serviceVBlankOnly(context);

        assertEquals(List.of(HardwareServiceBoundary.VINT_SERVICE), boundaries);
    }

    @Test
    void setupOnlyPassDoesNotInventHardwareBoundaries() {
        List<HardwareServiceBoundary> boundaries = new ArrayList<>();
        HardwareTimingService timing = new HardwareTimingService();
        LevelFrameContext context = context(timing, boundaries::add);
        LevelManager level = mock(LevelManager.class);
        when(level.consumePendingInitialProcessSpritesPass()).thenReturn(true);

        LevelFrameResult result = LevelFrameStep.execute(
                context, level, mock(Camera.class), () -> {
                });

        assertEquals(LevelFrameResult.SETUP_ONLY, result);
        assertTrue(boundaries.isEmpty());
    }

    @Test
    void pausedFrameServicesOnlyVintBoundary() {
        List<HardwareServiceBoundary> boundaries = new ArrayList<>();
        HardwareTimingService timing = new HardwareTimingService();
        GameStateManager gameState = mock(GameStateManager.class);
        when(gameState.applyPauseToggle(true)).thenReturn(true);
        LevelFrameContext context = context(timing, boundaries::add, gameState);

        LevelFrameResult result = LevelFrameStep.executeWithPause(
                context,
                mock(LevelManager.class),
                mock(Camera.class),
                () -> {
                },
                true,
                LevelFrameStep.DIRECT_WRAPPER);

        assertEquals(LevelFrameResult.PAUSED, result);
        assertEquals(List.of(HardwareServiceBoundary.VINT_SERVICE), boundaries);
    }

    private static LevelFrameContext context(
            HardwareTimingService timing,
            com.openggf.game.timing.HardwareTimingBoundaryObserver observer) {
        return context(timing, observer, null);
    }

    private static LevelFrameContext context(
            HardwareTimingService timing,
            com.openggf.game.timing.HardwareTimingBoundaryObserver observer,
            GameStateManager gameState) {
        return new LevelFrameContext(
                mock(GameModule.class),
                null,
                null,
                NoOpBonusStageProvider.INSTANCE,
                null,
                gameState,
                null,
                null,
                timing,
                observer,
                null,
                null);
    }

    private static HardwareWorkSubmission submission(int workUnits) {
        return submission(HardwareWorkKind.KOS_MODULE_QUEUE, workUnits);
    }

    private static HardwareWorkSubmission submission(HardwareWorkKind kind, int workUnits) {
        return new HardwareWorkSubmission(
                kind,
                0x1234,
                0x20,
                0x4000,
                1,
                "KosM",
                1,
                false,
                new TestPreparation(workUnits));
    }

    private record PreparationSnapshot(int remainingUnits)
            implements HardwareWorkPreparationSnapshot {
        @Override
        public HardwareWorkPreparation recreatePreparation() {
            return new TestPreparation(remainingUnits);
        }
    }

    private static final class TestPreparation implements HardwareWorkPreparation {
        private int remainingUnits;

        private TestPreparation(int remainingUnits) {
            this.remainingUnits = remainingUnits;
        }

        @Override
        public boolean stepOneWorkUnit() {
            remainingUnits--;
            return true;
        }

        @Override
        public boolean isPrepared() {
            return remainingUnits == 0;
        }

        @Override
        public byte[] preparedPayload() {
            return new byte[] {42};
        }

        @Override
        public HardwareWorkPreparationSnapshot snapshot() {
            return new PreparationSnapshot(remainingUnits);
        }

        @Override
        public void restore(HardwareWorkPreparationSnapshot snapshot) {
            remainingUnits = ((PreparationSnapshot) snapshot).remainingUnits();
        }
    }
}
