package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.RewindSnapshotDiff;
import static org.junit.jupiter.api.Assertions.*;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/** Holds the verified opening of the Tails route independently of later failures. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("trace-scope-r7")
class TestS3kTailsFullChainRunPrefix extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s3k", "runs",
            "s3k-tails-full-chain-all-emeralds");

    /** Includes all AIZ rows, production load camera state and the giant-ring handoff. */
    @Test
    void aiz1ThroughGiantRingIntoFirstSpecialStageRow() throws Exception {
        assertChainReplayThroughSegmentRow(RUN_DIR, 1, 1);
    }

    /** Defends the complete first return before entering the second special stage. */
    @Test
    void firstSpecialStageReturnThroughSecondEntry() throws Exception {
        assertChainReplayThroughSegmentRow(RUN_DIR, 3, 1);
    }

    /** A prefix must release its movie before another driver boots in this JVM. */
    @Test
    void openingPrefixReleasesInputBeforeColdCaptureRewind() throws Exception {
        assertChainReplayThroughSegmentRow(RUN_DIR, 1, 1);
        var playback = PlaybackDebugManager.getInstance();
        assertFalse(playback.hasActiveOrScheduledSession());
        var settings = new GameplayCaptureSession.Settings(
                320, "sonic", "", "off", null, null, null);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/soz2-cold-sonic.bk2"));
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),
                    8, 1, settings);
            assertTrue(
                    GameServices.level().consumePendingInitialProcessSpritesPass());
            // Keep input held across restore so no external edge-history reset is needed.
            var input = movie.getFrame(0);
            capture.step(input);
            var registry = SessionManager.getCurrentGameplayMode()
                    .getRewindRegistry();
            var saved = registry.capture();
            for (int frame = 0; frame < 45; frame++) capture.step(input);
            var expected = registry.capture();
            registry.restore(saved);
            for (int frame = 0; frame < 45; frame++) capture.step(input);
            var actual = registry.capture();
            assertEquals(expected.entries().keySet(), actual.entries().keySet());
            for (var key : expected.entries().keySet()) {
                var difference = RewindSnapshotDiff.diffKey(
                        key, expected.get(key), actual.get(key));
                assertTrue(difference.isEmpty(), key + ": " + difference);
            }
        }
    }
}
