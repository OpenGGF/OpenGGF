package com.openggf.recording;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestUserRecordingSessionLauncher {
    @TempDir
    Path tempDir;

    @Test
    void beginRecordingRejectsNonLevelModeBeforeRestarting() {
        Fixture fixture = new Fixture();
        fixture.mode = GameMode.TITLE_SCREEN;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                fixture.launcher()::beginRecordingFromCurrentLevel);

        assertTrue(thrown.getMessage().contains("LEVEL"));
        assertEquals(0, fixture.restarts.size());
    }

    @Test
    void beginRecordingRejectsTraceOrTestModeBeforeRestarting() {
        Fixture fixture = new Fixture();
        fixture.traceOrTestModeActive = true;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                fixture.launcher()::beginRecordingFromCurrentLevel);

        assertTrue(thrown.getMessage().contains("Trace"));
        assertEquals(0, fixture.restarts.size());
    }

    @Test
    void captureCurrentLaunchContextDelegatesToCurrentRuntimeContext() {
        Fixture fixture = new Fixture();
        fixture.liveContext = new RecordingLaunchContext(
                "s2", 3, 1, "tails", List.of("sonic", "knuckles"),
                false, "current-act-fresh-start");

        RecordingLaunchContext context = fixture.launcher().captureCurrentLaunchContext();

        assertEquals(fixture.liveContext, context);
    }

    @Test
    void beginRecordingRestartsFreshAndArmsFrameZeroSessionAtFormattedPath() {
        Fixture fixture = new Fixture();
        fixture.now = Instant.parse("2026-06-29T15:04:05Z");
        fixture.liveContext = new RecordingLaunchContext(
                "s3k", 0, 1, "sonic", List.of("tails"),
                true, "current-act-fresh-start");

        UserRecordingSession session = fixture.launcher().beginRecordingFromCurrentLevel();

        assertEquals(List.of(fixture.liveContext), fixture.restarts);
        assertEquals(0, session.currentMovieFrame());
        assertEquals(tempDir.resolve("recordings").resolve("s3k")
                .resolve("s3k-0-1-2026-06-29-150405.bk2"), session.outputBk2Path());
    }

    @Test
    void beginPlaybackUsesManifestLaunchContextNotCurrentLiveContext() throws Exception {
        Fixture fixture = new Fixture();
        fixture.liveContext = new RecordingLaunchContext(
                "s1", 0, 0, "sonic", List.of(),
                false, "current-act-fresh-start");
        RecordingLaunchContext manifestContext = new RecordingLaunchContext(
                "s3k", 5, 2, "knuckles", List.of("tails"),
                true, "current-act-fresh-start");
        Path bk2 = writeRecording("manifest-context.bk2", manifestContext, 2);
        UserRecordingEntry entry = new UserRecordingEntry(
                bk2, "manifest-context", null, 2, fixture.now,
                RecordingVersionWarning.NONE, "");
        UserRecordingPlaybackOptions options = new UserRecordingPlaybackOptions(1, true, false);

        UserRecordingPlaybackState state = fixture.launcher().beginPlayback(entry, options);

        assertEquals(UserRecordingPlaybackState.PLAYING, state);
        assertEquals(List.of(manifestContext), fixture.restarts);
        assertNotNull(fixture.startedMovie);
        assertEquals(2, fixture.startedMovie.getFrameCount());
        assertEquals(0, fixture.startedOffset);
        assertNotNull(fixture.observer);
        assertSame(options, fixture.launcher().activePlaybackOptions());
    }

    private Path writeRecording(String fileName, RecordingLaunchContext context, int frameCount) throws Exception {
        Path path = tempDir.resolve("fixtures").resolve(fileName);
        UserRecordingWriter.write(
                path,
                new UserRecordingManifest(
                        UserRecordingManifest.CURRENT_SCHEMA_VERSION,
                        fileName,
                        new BuildIdentity("0.6.prerelease", "task9", false),
                        context,
                        UserRecordingSidecarMetadata.everyFrame(),
                        new RecordingDeterminismMetadata(0, null),
                        "A",
                        frameCount,
                        UserRecordingStopReason.USER_STOPPED,
                        Instant.parse("2026-06-29T14:30:22Z")),
                java.util.stream.IntStream.range(0, frameCount)
                        .mapToObj(frame -> new RecordedFrameInput(
                                frame,
                                frame == 0 ? AbstractPlayableSprite.INPUT_RIGHT : 0,
                                0,
                                false,
                                0,
                                0,
                                false))
                        .toList(),
                java.util.stream.IntStream.range(0, frameCount)
                        .mapToObj(frame -> new DesyncLiteFrame(
                                frame, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0))
                        .toList());
        return path;
    }

    private final class Fixture implements UserRecordingSessionLauncher.GameLoopAdapter,
            UserRecordingSessionLauncher.LaunchContextSource,
            UserRecordingSessionLauncher.TraceModeGuard,
            UserRecordingSessionLauncher.PlaybackAdapter {
        GameMode mode = GameMode.LEVEL;
        boolean traceOrTestModeActive;
        Instant now = Instant.parse("2026-06-29T12:00:00Z");
        RecordingLaunchContext liveContext = new RecordingLaunchContext(
                "s2", 1, 0, "sonic", List.of("tails"),
                true, "current-act-fresh-start");
        final java.util.ArrayList<RecordingLaunchContext> restarts = new java.util.ArrayList<>();
        Bk2Movie startedMovie;
        int startedOffset = -1;
        PlaybackDebugManager.PlaybackFrameObserver observer;
        UserRecordingSessionLauncher launcher;

        UserRecordingSessionLauncher launcher() {
            if (launcher == null) {
                launcher = new UserRecordingSessionLauncher(
                        this,
                        this,
                        this,
                        this,
                        tempDir,
                        () -> now,
                        ZoneOffset.UTC);
            }
            return launcher;
        }

        @Override
        public GameMode currentGameMode() {
            return mode;
        }

        @Override
        public void restartFromRecordingLaunchContext(RecordingLaunchContext context) {
            restarts.add(context);
        }

        @Override
        public RecordingLaunchContext captureCurrentLaunchContext() {
            return liveContext;
        }

        @Override
        public boolean isTraceOrTestModeActive() {
            return traceOrTestModeActive;
        }

        @Override
        public void startSession(Bk2Movie movie, int startOffsetIndex) {
            startedMovie = movie;
            startedOffset = startOffsetIndex;
        }

        @Override
        public void setFrameObserver(PlaybackDebugManager.PlaybackFrameObserver observer) {
            this.observer = observer;
        }
    }
}
