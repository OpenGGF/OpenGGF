package com.openggf.tests.trace.s1;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.EngineStepper;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.PlaybackController;
import com.openggf.game.rewind.RewindSeekAwareEngineStepper;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.*;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Trace replay test for Sonic 1 Green Hill Zone Act 1.
 *
 * <p>Requires a Sonic 1 ROM and a BK2 recording in the trace directory.
 * Skipped when the ROM is unavailable or when the trace directory has no .bk2 file.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestS1Ghz1TraceReplay extends AbstractTraceReplayTest {

    @Override
    protected SonicGame game() { return SonicGame.SONIC_1; }

    @Override
    protected int zone() { return 0; }

    @Override
    protected int act() { return 0; }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s1/ghz1_fullrun");
    }

    @Test
    void rewindResumePreservesJumpReleaseAtFrame037d() throws Exception {
        Path traceDir = traceDirectory();
        Assumptions.assumeTrue(java.nio.file.Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);
        Path bk2Path = findBk2File(traceDir);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                .withZoneAndAct(zone(), act())
                .startPosition(trace.metadata().startX(), trace.metadata().startY())
                .startPositionIsCentre()
                .build();

        try {
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
            int traceBaseFrame = boot.replayStart().startingTraceIndex();
            int movieBaseFrame = TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace);
            PlaybackController playback = RuntimeManager.getCurrent().getGameplayModeContext()
                    .installPlaybackController(
                            new MovieInputSource(movie, movieBaseFrame),
                            new HeadlessTraceRewindStepper(fixture, movie, trace, movieBaseFrame, traceBaseFrame),
                            60);
            int releaseFrame = 0x037D;
            int postReleaseFrame = releaseFrame + 1;
            while (RuntimeManager.getCurrent().getGameplayModeContext().getRewindController().currentFrame()
                    < releaseFrame) {
                playback.tick();
            }
            var freshBeforeReleaseSnapshot = fixture.sprite().captureRewindState();
            playback.tick();
            assertEquals(trace.getFrame(releaseFrame).ySpeed(), fixture.sprite().getYSpeed(),
                    "fresh rewind-controller playback must match GHZ frame 0x037D before any rewind");
            while (RuntimeManager.getCurrent().getGameplayModeContext().getRewindController().currentFrame()
                    < postReleaseFrame + 12) {
                playback.tick();
            }
            while (RuntimeManager.getCurrent().getGameplayModeContext().getRewindController().currentFrame()
                    > releaseFrame) {
                playback.stepBackwardOnce();
            }

            AbstractPlayableSprite sprite = fixture.sprite();
            TraceFrame beforeRelease = trace.getFrame(releaseFrame - 1);
            assertEquals(beforeRelease.ySpeed(), sprite.getYSpeed(),
                    "rewind restore must land on the frame before GHZ 0x037D with y-speed intact");
            assertEquals(beforeRelease.x(), sprite.getCentreX(),
                    "rewind restore must land on the frame before GHZ 0x037D with x intact");
            assertEquals(beforeRelease.y(), sprite.getCentreY(),
                    "rewind restore must land on the frame before GHZ 0x037D with y intact");
            assertEquals(
                    freshBeforeReleaseSnapshot.playerExtra().movementState(),
                    sprite.captureRewindState().playerExtra().movementState(),
                    "rewind restore must preserve the playable movement latch state before GHZ 0x037D");

            playback.play();
            playback.tick();

            TraceFrame expected = trace.getFrame(releaseFrame);
            assertEquals(expected.ySpeed(), sprite.getYSpeed(),
                    "rewind resume must preserve jump-release y-speed at GHZ frame 0x037D");
            assertEquals(expected.x(), sprite.getCentreX(), "x should still match at GHZ frame 0x037D");
            assertEquals(expected.y(), sprite.getCentreY(), "y should still match at GHZ frame 0x037D");
        } finally {
            TestEnvironment.resetAll();
        }
    }

    private static Path findBk2File(Path dir) throws IOException {
        try (var files = java.nio.file.Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class MovieInputSource implements InputSource {
        private final Bk2Movie movie;
        private final int baseFrame;

        private MovieInputSource(Bk2Movie movie, int baseFrame) {
            this.movie = movie;
            this.baseFrame = Math.max(0, baseFrame);
        }

        @Override
        public int frameCount() {
            return Math.max(1, movie.getFrameCount() - baseFrame + 1);
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return movie.getFrame(baseFrame + Math.max(0, frame - 1));
        }
    }

    private static final class HeadlessTraceRewindStepper
            implements EngineStepper, RewindSeekAwareEngineStepper {
        private final HeadlessTestFixture fixture;
        private final Bk2Movie movie;
        private final TraceData trace;
        private final int movieBaseFrame;
        private final int traceBaseFrame;

        private HeadlessTraceRewindStepper(HeadlessTestFixture fixture, Bk2Movie movie,
                                           TraceData trace, int movieBaseFrame,
                                           int traceBaseFrame) {
            this.fixture = fixture;
            this.movie = movie;
            this.trace = trace;
            this.movieBaseFrame = movieBaseFrame;
            this.traceBaseFrame = traceBaseFrame;
        }

        @Override
        public void step(Bk2FrameInput input) {
            int traceIndex = traceBaseFrame + Math.max(0, input.frameIndex() - movieBaseFrame);
            if (isVblankOnly(traceIndex)) {
                GameServices.level().getObjectManager().advanceVblaCounter();
                return;
            }
            int mask = input.p1InputMask();
            int p2Mask = input.p2InputMask();
            fixture.runner().stepFrame(
                    (mask & AbstractPlayableSprite.INPUT_UP) != 0,
                    (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                    (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_JUMP) != 0,
                    p2Mask,
                    input.p2StartPressed());
        }

        @Override
        public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
            fixture.runner().primeInputState(inputAtFrame);
        }

        private boolean isVblankOnly(int traceIndex) {
            if (traceIndex < 0 || traceIndex >= trace.frameCount()) {
                return false;
            }
            TraceFrame current = trace.getFrame(traceIndex);
            TraceFrame previous = traceIndex > 0 ? trace.getFrame(traceIndex - 1) : null;
            return TraceReplayBootstrap.phaseForReplay(trace, previous, current)
                    == TraceExecutionPhase.VBLANK_ONLY;
        }
    }
}
