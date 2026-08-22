package com.openggf.tests.trace.runs;

import com.openggf.GameLoop;
import com.openggf.TraceSessionLauncher;
import com.openggf.game.GameMode;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.ActiveSegmentPayload;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;
import com.openggf.tests.trace.TraceV5RunFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestVisualRunActivePayloadLifecycle {

    @AfterEach
    void tearDown() {
        VisualRunReplayHarness.tearDown();
    }

    @Test
    void descriptorFrameViewAnswersSegmentLagGapAndTailWithoutOpeningPayload(
            @TempDir Path root) throws Exception {
        Path runDir = TraceV5RunFixture.writeS3kBonusRun(root.resolve("s3k"));
        List<TraceRunSegmentDescriptor> planned = descriptors(runDir);
        BitSet lagged = planned.getFirst().laggedRows();
        lagged.set(1);
        TraceRunSegmentDescriptor first = copyWithLag(planned.getFirst(), lagged);
        List<TraceRunSegmentDescriptor> descriptors = List.of(
                first, planned.get(1), planned.get(2));
        GameLoop loop = mock(GameLoop.class);
        when(loop.getCurrentGameMode()).thenReturn(GameMode.LEVEL);

        VisualRunReplayHarness.FrameView active =
                VisualRunReplayHarness.frameView(501, 4, loop, descriptors, true);
        VisualRunReplayHarness.FrameView gap =
                VisualRunReplayHarness.frameView(700, 5, loop, descriptors, true);
        VisualRunReplayHarness.FrameView tail =
                VisualRunReplayHarness.frameView(2999, 6, loop, descriptors, true);

        assertEquals(0, active.segmentIndex());
        assertEquals(1, active.segmentRow());
        assertTrue(active.lag());
        assertNull(gap.segment());
        assertFalse(gap.lag());
        assertNull(tail.segment());
    }

    @Test
    void visualAndCompleteAudioCloseLocallyOnPreTransferConstructorFailure(
            @TempDir Path root) throws Exception {
        Path visual = preparedRun(root.resolve("visual"));
        AtomicReference<ActiveSegmentPayload> visualPayload =
                new AtomicReference<>();
        Exception visualFailure = assertThrows(Exception.class,
                () -> VisualRunReplayHarness.replay(
                        visual, new VisualRunReplayHarness.Stop(1, -1),
                        VisualRunReplayHarness.FrameObserver.NONE, false,
                        failingConstructor(visualPayload), minimalBootstrap(false)));
        assertEquals("injected constructor failure", visualFailure.getMessage());
        assertTrue(visualPayload.get().isClosed(), "visual");
        VisualRunReplayHarness.tearDown();

        Path completeAudio = preparedRun(root.resolve("complete-audio"));
        AtomicReference<ActiveSegmentPayload> audioPayload =
                new AtomicReference<>();
        Exception audioFailure = assertThrows(Exception.class,
                () -> VisualRunReplayHarness.replayCompleteAudio(
                        completeAudio,
                        new VisualRunReplayHarness.CompleteAudioStop(500, 3000, 0),
                        VisualRunReplayHarness.FrameObserver.NONE,
                        failingConstructor(audioPayload), minimalBootstrap(false)));
        assertEquals("injected constructor failure", audioFailure.getMessage());
        assertTrue(audioPayload.get().isClosed(), "complete-audio");
    }

    @Test
    void visualAndCompleteAudioCloseThroughSessionAfterTransferFailure(
            @TempDir Path root) throws Exception {
        Path visual = preparedRun(root.resolve("visual"));
        AtomicReference<ActiveSegmentPayload> visualPayload =
                new AtomicReference<>();
        AtomicReference<TraceSessionLauncher> visualSession =
                new AtomicReference<>();
        AssertionError visualFailure = assertThrows(AssertionError.class,
                () -> VisualRunReplayHarness.replay(
                        visual, new VisualRunReplayHarness.Stop(1, -1),
                        VisualRunReplayHarness.FrameObserver.NONE, false,
                        recordingConstructor(visualPayload, visualSession),
                        minimalBootstrap(true)));
        assertEquals("injected post-transfer bootstrap failure",
                visualFailure.getMessage());
        assertTrue(visualPayload.get().isClosed(), "visual");
        assertNull(field(visualSession.get(), "activeRunPayload"), "visual");
        VisualRunReplayHarness.tearDown();

        Path completeAudio = preparedRun(root.resolve("complete-audio"));
        AtomicReference<ActiveSegmentPayload> audioPayload =
                new AtomicReference<>();
        AtomicReference<TraceSessionLauncher> audioSession =
                new AtomicReference<>();
        AssertionError audioFailure = assertThrows(AssertionError.class,
                () -> VisualRunReplayHarness.replayCompleteAudio(
                        completeAudio,
                        new VisualRunReplayHarness.CompleteAudioStop(500, 3000, 0),
                        VisualRunReplayHarness.FrameObserver.NONE,
                        recordingConstructor(audioPayload, audioSession),
                        minimalBootstrap(true)));
        assertEquals("injected post-transfer bootstrap failure",
                audioFailure.getMessage());
        assertTrue(audioPayload.get().isClosed(), "complete-audio");
        assertNull(field(audioSession.get(), "activeRunPayload"),
                "complete-audio");
    }

    private static Path preparedRun(Path root) throws Exception {
        Path runDir = TraceV5RunFixture.writeS3kBonusRun(root);
        TraceV5RunFixture.writeMovie(runDir.resolve("synthetic.bk2"));
        return runDir;
    }

    private static VisualRunReplayHarness.RunSessionConstructor
            failingConstructor(
                    AtomicReference<ActiveSegmentPayload> payload) {
        return (entry, movie, descriptors, activePayload) -> {
            payload.set(activePayload);
            throw new Exception("injected constructor failure");
        };
    }

    private static VisualRunReplayHarness.RunSessionConstructor
            recordingConstructor(
                    AtomicReference<ActiveSegmentPayload> payload,
                    AtomicReference<TraceSessionLauncher> session) {
        return (entry, movie, descriptors, activePayload) -> {
            payload.set(activePayload);
            TraceSessionLauncher created = VisualRunReplayHarness.newRunSession(
                    entry, movie, descriptors, activePayload);
            session.set(created);
            return created;
        };
    }

    private static VisualRunReplayHarness.RunBootstrap minimalBootstrap(
            boolean failAfterTransfer) {
        return new VisualRunReplayHarness.RunBootstrap() {
            @Override
            public GameLoop beforeTransfer(
                    com.openggf.trace.catalog.TraceEntry entry,
                    com.openggf.trace.TraceData firstTrace,
                    VisualRunReplayHarness.FrameObserver observer) {
                return mock(GameLoop.class);
            }

            @Override
            public void afterTransfer(
                    TraceSessionLauncher session, GameLoop loop) {
                if (failAfterTransfer) {
                    throw new AssertionError(
                            "injected post-transfer bootstrap failure");
                }
            }
        };
    }

    private static List<TraceRunSegmentDescriptor> descriptors(Path runDir)
            throws Exception {
        return TraceRunReplayWalker.planDescriptors(TraceRunManifest.load(
                runDir.resolve("run_manifest.json")), runDir);
    }

    private static TraceRunSegmentDescriptor copyWithLag(
            TraceRunSegmentDescriptor descriptor, BitSet lagged) {
        return new TraceRunSegmentDescriptor(
                descriptor.segment(), descriptor.segmentDirectory(),
                descriptor.metadata(), descriptor.rowCount(),
                descriptor.openingFrame(), descriptor.rawFrames(), lagged,
                descriptor.hardwareTimingSchedule(),
                descriptor.terminalDynamicArtLedger(), descriptor.entryBoundary(),
                descriptor.exitBoundary(), descriptor.levelLoopRowCount(),
                descriptor.executionPolicy());
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

}
