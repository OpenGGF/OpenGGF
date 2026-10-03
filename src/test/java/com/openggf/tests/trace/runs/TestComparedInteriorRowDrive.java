package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.openggf.game.timing.HardwareServiceBoundary.VINT_SERVICE;
import static org.junit.jupiter.api.Assertions.*;

class TestComparedInteriorRowDrive {
    private final PlaybackDebugManager playback = PlaybackDebugManager.getInstance();
    private final List<Integer> prepared = new ArrayList<>();
    private final List<Integer> published = new ArrayList<>();

    @AfterEach
    void stopPlayback() {
        playback.endSession();
    }

    private HardwareTimingReplayPort start() {
        var service = new HardwareTimingService();
        var port = new HardwareTimingReplayPort(service.beginRecordedAdmission());
        port.install(HardwareTimingSchedule.empty());
        playback.startSession(new Bk2Movie(Path.of("interior.bk2"), "logkey", Map.of(),
                IntStream.range(0, 6).mapToObj(i ->
                        new Bk2FrameInput(i, 0, 0, false, "neutral")).toList(), 1), 0);
        playback.setFrameObserver(new PlaybackDebugManager.PlaybackFrameObserver() {
            @Override
            public void prepareFrame(Bk2FrameInput frame) {
                prepared.add(frame.frameIndex());
                port.beginRawFrame(frame.frameIndex());
            }

            @Override
            public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
                return false;
            }

            @Override
            public void afterFrameAdvanced(Bk2FrameInput frame, boolean skipped) {
                published.add(frame.frameIndex());
            }
        });
        return port;
    }

    @Test
    void gameplayThenTitleVintsUseDistinctRowsWithoutDoubleAdvancingGameplay() {
        var port = start();
        int[] iterations = {0};
        int[] closures = {0};
        var drive = new ComparedInteriorRowDrive(playback, () -> {
            port.apply(VINT_SERVICE);
            if (iterations[0]++ == 0) {
                playback.onLevelFrameAdvanced();
            } else {
                assertEquals(1, closures[0], "source must close before title production");
            }
        }, () -> !published.isEmpty(), () -> {
            assertEquals(List.of(0), published, "close follows source publication");
            closures[0]++;
        });

        drive.run();
        drive.run();
        drive.run();

        assertEquals(List.of(0, 1, 2), prepared);
        assertEquals(prepared, published);
        assertEquals(3, playback.getCursorFrame());
        assertEquals(1, closures[0]);
        assertTrue(drive.sourceClosed());
    }

    @Test
    void failedProductionDoesNotPublishOrCloseSource() {
        var port = start();
        var failure = new IllegalStateException("production failure");
        var drive = new ComparedInteriorRowDrive(playback, () -> {
            port.apply(VINT_SERVICE);
            throw failure;
        }, () -> true, () -> fail("failed production cannot close source"));

        assertSame(failure, assertThrows(IllegalStateException.class, drive::run));
        assertEquals(0, playback.getCursorFrame());
        assertTrue(published.isEmpty());
        assertFalse(drive.sourceClosed());
    }

    @Test
    void sourceWithRowsRemainingStaysOpenAcrossPhysicalIterations() {
        var port = start();
        var drive = new ComparedInteriorRowDrive(playback, () -> port.apply(VINT_SERVICE),
                () -> false, () -> fail("unexhausted source cannot close"));

        drive.run();
        drive.run();

        assertEquals(List.of(0, 1), published);
        assertFalse(drive.sourceClosed());
    }
}
