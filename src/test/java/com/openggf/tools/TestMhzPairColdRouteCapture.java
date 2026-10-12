package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The production GameLoop owns live-history recording; the recording driver does not. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzPairColdRouteCapture {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz2-team-incoming-320.bk2"));
        assertEquals(40630, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        long outgoingHistory = 0;
        boolean isolatedHistory = false;
        // Preserve full drawing: MHZ replay controls cannot isolate a drawing gap.
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // All independent registry replay spots are covered by the authored-route test.
                // Start recording shortly before the actual outgoing boundary, not in the title tail.
                if (frame == movie.getFrameCount() - 480) {
                    GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
                }
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "cold route death at " + frame);
                assertEquals(CharacterKey.SONIC, session.player().characterKey());
                var followers = GameServices.sprites().getSidekicks();
                assertEquals(1, followers.size(), "native follower remains registered through the load");
                assertEquals(CharacterKey.TAILS, followers.getFirst().characterKey());
                if (frame < movie.getFrameCount() - 480) continue;
                var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                if (GameServices.level().getCurrentZone() == 7 && rewind != null) {
                    outgoingHistory = Math.max(outgoingHistory, rewind.currentFrame());
                } else if (GameServices.level().getCurrentZone() == 4 && !isolatedHistory) {
                    assertTrue(outgoingHistory > 10, "outgoing MHZ live history must exist");
                    assertNotNull(rewind, "FBZ retains the production rewind controller");
                    assertTrue(rewind.currentFrame() < outgoingHistory,
                            "the actual FBZ load isolates the outgoing timeline");
                    isolatedHistory = true;
                }
            }
            drawing.checkpoint(session);
            assertTrue(isolatedHistory, "the route must observe the actual history-reset boundary");
            assertEquals(4, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
            assertTrue(session.player().getCentreX() > 128, "ordinary controller movement in playable FBZ");
        }
    }
}
