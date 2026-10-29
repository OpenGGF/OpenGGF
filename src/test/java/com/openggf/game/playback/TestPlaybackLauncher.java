package com.openggf.game.playback;

import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.recording.RecordingVersionWarning;
import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.game.recording.UserRecordingManifest;
import com.openggf.game.recording.UserRecordingPlaybackOptions;
import com.openggf.game.save.SelectedTeam;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestPlaybackLauncher {
    @Test
    void dispatchesTheExactRecordingOptionsAndTraceWithoutCreatingAnotherSession() throws Exception {
        var recording = recording(null);
        var options = new UserRecordingPlaybackOptions(12, true, true);
        var trace = trace();
        AtomicInteger launches = new AtomicInteger();
        var launcher = new PlaybackLauncher((entry, suppliedOptions) -> {
            assertSame(recording.recording(), entry);
            assertSame(options, suppliedOptions);
            launches.incrementAndGet();
        }, selected -> {
            assertSame(trace.trace(), selected);
            launches.incrementAndGet();
            return false;
        }, () -> false, () -> false);

        assertTrue(launcher.launch(new PlaybackRequest.Recording(recording, options)));
        assertFalse(launcher.launch(new PlaybackRequest.Trace(trace)));
        assertEquals(2, launches.get());
    }

    @Test
    void checksCurrentOwnershipAtDispatchRatherThanCachingBrowserAdmission() throws Exception {
        AtomicBoolean occupied = new AtomicBoolean(false);
        AtomicInteger launches = new AtomicInteger();
        var launcher = new PlaybackLauncher((entry, options) -> launches.incrementAndGet(),
                entry -> { launches.incrementAndGet(); return true; }, occupied::get, () -> false);
        var request = new PlaybackRequest.Recording(recording(null), new UserRecordingPlaybackOptions(0, false, false));

        assertTrue(launcher.launch(request));
        occupied.set(true);
        assertThrows(IllegalStateException.class, () -> launcher.launch(request));
        assertThrows(IllegalStateException.class, () -> launcher.launch(new PlaybackRequest.Trace(trace())));
        assertEquals(1, launches.get());
    }

    @Test
    void testModeCannotLaunchNativeRecordingButCanLaunchTrace() throws Exception {
        AtomicInteger recordingLaunches = new AtomicInteger();
        var launcher = new PlaybackLauncher((entry, options) -> recordingLaunches.incrementAndGet(),
                entry -> true, () -> false, () -> true);

        assertThrows(IllegalStateException.class, () -> launcher.launch(new PlaybackRequest.Recording(
                recording(null), new UserRecordingPlaybackOptions(0, false, false))));
        assertTrue(launcher.launch(new PlaybackRequest.Trace(trace())));
        assertEquals(0, recordingLaunches.get());
    }

    @Test
    void invalidRecordingNeverReachesItsLaunchOwner() {
        var launcher = new PlaybackLauncher((entry, options) -> fail("invalid recording reached launcher"),
                entry -> fail("unexpected trace launch"), () -> false, () -> false);

        assertThrows(IllegalStateException.class, () -> launcher.launch(new PlaybackRequest.Recording(
                recording("Invalid recording manifest"), new UserRecordingPlaybackOptions(0, false, false))));
    }

    private PlaybackEntry.Recording recording(String error) {
        var manifest = new UserRecordingManifest(1, "recording", null,
                new RecordingLaunchContext("s1", 0, 0, "sonic", List.of(), false, "current-act-fresh-start"),
                null, null, "A", 20, null, Instant.EPOCH);
        return new PlaybackEntry.Recording("s1", new UserRecordingEntry(Path.of("recording.bk2"),
                "recording", manifest, 20, Instant.EPOCH, RecordingVersionWarning.MISSING_METADATA, error));
    }

    private PlaybackEntry.Trace trace() {
        return new PlaybackEntry.Trace(new TraceEntry(Path.of("trace"), "s1", 0, 0, 20, 4, 0,
                new SelectedTeam("sonic", List.of()), Path.of("trace.bk2"), mock(TraceMetadata.class)));
    }
}
