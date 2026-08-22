package com.openggf.tests.trace.runs;

import com.openggf.GameLoop;
import com.openggf.TraceSessionLauncher;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameMode;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.ActiveSegmentPayload;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;
import com.openggf.tests.trace.TraceV5RunFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestVisualRunActivePayloadLifecycle {

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
        for (String route : List.of("visual", "complete-audio")) {
            Path runDir = TraceV5RunFixture.writeS3kBonusRun(
                    root.resolve(route));
            TraceV5RunFixture.writeMovie(runDir.resolve("synthetic.bk2"));
            List<TraceRunSegmentDescriptor> descriptors = descriptors(runDir);
            ActiveSegmentPayload local = TraceRunReplayWalker.openActiveSegment(
                    descriptors.getFirst(), 0);
            Bk2Movie movie = new com.openggf.debug.playback.Bk2MovieLoader()
                    .load(runDir.resolve("synthetic.bk2"));
            List<TraceRunSegmentDescriptor> wrongOwner =
                    new ArrayList<>(descriptors);
            wrongOwner.set(0, copyWithLag(descriptors.getFirst(),
                    descriptors.getFirst().laggedRows()));

            Exception failure;
            try {
                failure = assertThrows(Exception.class,
                        () -> VisualRunReplayHarness.newRunSession(
                                null, movie, wrongOwner, local), route);
            } finally {
                VisualRunReplayHarness.closeLocalRunPayload(local);
            }

            assertTrue(local.isClosed(), route);
            assertTrue(rootCause(failure) instanceof IllegalArgumentException,
                    () -> route + ": " + failure);
        }
    }

    @Test
    void visualAndCompleteAudioCloseThroughSessionAfterTransferFailure(
            @TempDir Path root) throws Exception {
        for (String route : List.of("visual", "complete-audio")) {
            Path runDir = TraceV5RunFixture.writeS3kBonusRun(
                    root.resolve(route));
            TraceV5RunFixture.writeMovie(runDir.resolve("synthetic.bk2"));
            List<TraceRunSegmentDescriptor> descriptors = descriptors(runDir);
            ActiveSegmentPayload local = TraceRunReplayWalker.openActiveSegment(
                    descriptors.getFirst(), 0);
            Bk2Movie movie = new com.openggf.debug.playback.Bk2MovieLoader()
                    .load(runDir.resolve("synthetic.bk2"));

            TraceSessionLauncher session = VisualRunReplayHarness.newRunSession(
                    null, movie, descriptors, local);

            assertSame(local, field(session, "activeRunPayload"), route);
            AssertionError primary = new AssertionError(
                    route + " post-transfer failure");
            Throwable returned =
                    VisualRunReplayHarness.closeTransferredRunSession(
                            session, primary);
            assertSame(primary, returned, route);
            assertTrue(local.isClosed(), route);
            assertNull(field(session, "activeRunPayload"), route);
        }
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

    private static Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

}
