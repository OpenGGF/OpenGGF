package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameMode;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.graphics.FadeManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.timer.TimerManager;
import com.openggf.trace.timing.HardwareCompletionEdge;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.timing.HardwareServiceBoundary.VINT_SERVICE;
import static com.openggf.game.timing.HardwareServiceBoundary.POST_OBJECTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLevelIterationHardwareTimingAdmissionOrder {

    @Test
    void pausedAdmissionActivatesCurrentRowBeforeVintService() {
        Harness harness = harness(1);
        harness.context().getGameStateManager().setGamePaused(true);
        harness.port().beginRawFrame(0);
        var controller = new LevelIterationAdmissionController();
        var level = mock(com.openggf.level.LevelManager.class);

        LevelFrameResult admission = controller.admit(
                GameMode.LEVEL,
                () -> false,
                () -> LevelFrameResult.SETUP_ONLY,
                level,
                harness.context(),
                false,
                mock(UserRecordingRuntimeControls.class),
                () -> { },
                () -> harness.observer().beginRawFrame(1),
                harness.observer()::enterUnrepresentedGap);
        assertEquals(LevelFrameResult.PAUSED, admission);

        LevelFrameStep.serviceVBlankOnly(
                LevelFrameContext.from(harness.context()));

        assertTrue(harness.context().hardwareTiming().isReady(harness.handle()));
        assertEquals(1, harness.port().capture().rawFrameLatch());
    }

    @Test
    void seamlessAdmissionDeactivatesStaleRowBeforeTransitionVint() {
        Harness harness = harness(0);
        harness.port().beginRawFrame(0);
        var controller = new LevelIterationAdmissionController();
        var level = mock(com.openggf.level.LevelManager.class);
        var request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY)
                .build();
        when(level.consumeSeamlessTransitionRequest()).thenReturn(request);

        controller.admit(
                GameMode.LEVEL,
                () -> false,
                () -> LevelFrameResult.SETUP_ONLY,
                level,
                harness.context(),
                false,
                mock(UserRecordingRuntimeControls.class),
                () -> { },
                () -> harness.observer().beginRawFrame(1),
                harness.observer()::enterUnrepresentedGap);
        LevelFrameStep.serviceVBlankOnly(
                LevelFrameContext.from(harness.context()));

        assertFalse(harness.context().hardwareTiming().isReady(harness.handle()));
        assertEquals(null, harness.port().capture().rawFrameLatch());
    }

    @Test
    void lockedTitleCardDeactivatesStaleRowBeforeItsHardwareScan() {
        Harness harness = harness(0);
        harness.port().beginRawFrame(0);
        var controller = new LevelIterationAdmissionController();

        controller.admit(
                GameMode.TITLE_CARD,
                () -> {
                    LevelFrameStep.executeHardwareTimedObjectScan(
                            LevelFrameContext.from(harness.context()),
                            () -> { });
                    return false;
                },
                () -> LevelFrameResult.SETUP_ONLY,
                mock(com.openggf.level.LevelManager.class),
                harness.context(),
                false,
                mock(UserRecordingRuntimeControls.class),
                () -> { },
                () -> harness.observer().beginRawFrame(1),
                harness.observer()::enterUnrepresentedGap);

        assertFalse(harness.context().hardwareTiming().isReady(harness.handle()));
        assertEquals(null, harness.port().capture().rawFrameLatch());
    }

    private static Harness harness(int edgeRawFrame) {
        GameplayModeContext context = new GameplayModeContext(
                new WorldSession(new Sonic3kGameModule()),
                HardwareReadinessAdmissionPolicy.RECORDED);
        context.attachGameplayManagers(
                new Camera(),
                new TimerManager(),
                new GameStateManager(),
                new FadeManager(),
                new GameRng(GameRng.Flavour.S3K),
                new DefaultSolidExecutionRegistry());
        HardwareWorkHandle handle =
                context.hardwareTiming().submit(submission());
        context.hardwareTiming().service(POST_OBJECTS);
        HardwareCompletionEdge edge = new HardwareCompletionEdge(
                edgeRawFrame,
                VINT_SERVICE,
                handle.kind(),
                handle.ordinal(),
                handle.submissionFingerprint());
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(
                context.recordedCompletionAuthority());
        port.install(new HardwareTimingSchedule(List.of(edge)));
        TraceHardwareTimingBoundaryObserver observer =
                new TraceHardwareTimingBoundaryObserver(port);
        context.setHardwareTimingBoundaryObserver(observer);
        return new Harness(context, handle, port, observer);
    }

    private static HardwareWorkSubmission submission() {
        return new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                0x1000,
                0x20,
                0x4000,
                1,
                "KosM",
                1,
                false,
                new PreparedWork());
    }

    private record Harness(
            GameplayModeContext context,
            HardwareWorkHandle handle,
            HardwareTimingReplayPort port,
            TraceHardwareTimingBoundaryObserver observer) {
    }

    private record PreparationSnapshot()
            implements HardwareWorkPreparationSnapshot {
        @Override
        public HardwareWorkPreparation recreatePreparation() {
            return new PreparedWork();
        }
    }

    private static final class PreparedWork
            implements HardwareWorkPreparation {
        @Override
        public boolean stepOneWorkUnit() {
            return false;
        }

        @Override
        public boolean isPrepared() {
            return true;
        }

        @Override
        public byte[] preparedPayload() {
            return new byte[] {1};
        }

        @Override
        public HardwareWorkPreparationSnapshot snapshot() {
            return new PreparationSnapshot();
        }

        @Override
        public void restore(HardwareWorkPreparationSnapshot snapshot) {
        }
    }
}
