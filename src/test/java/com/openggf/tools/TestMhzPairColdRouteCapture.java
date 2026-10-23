package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.GameLoopRewindTestAccess.owner;

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
        assertEquals(44004, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        // Authored input 41705 is the live capsule approach, before ship carry and
        // the actual FBZ load. The released FBZ tail is longer than
        // 480 inputs, so an end-relative activation would begin after that load.
        int beginOutgoingHistory = 41705;
        long outgoingHistory = 0;
        RewindController outgoingController = null, incomingController = null;
        boolean loadedFbz = false;
        boolean isolatedHistory = false;
        // Preserve full drawing: MHZ replay controls cannot isolate a drawing gap.
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // All independent registry replay spots are covered by the authored-route test.
                // Start recording shortly before the actual outgoing boundary, not in the title tail.
                if (frame == beginOutgoingHistory) {
                    GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
                }
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "cold route death at " + frame);
                assertEquals(CharacterKey.SONIC, session.player().characterKey());
                var followers = GameServices.sprites().getSidekicks();
                assertEquals(1, followers.size(), "native follower remains registered through the load");
                assertEquals(CharacterKey.TAILS, followers.getFirst().characterKey());
                if (frame < beginOutgoingHistory) continue;
                var rewind = owner(session.loop()).getRewindController();
                if (GameServices.level().getCurrentZone() == 7) {
                    if (rewind != null) outgoingController = rewind;
                    assertNotNull(outgoingController, "the enabled MHZ route records before its exit");
                    outgoingHistory = Math.max(outgoingHistory, outgoingController.currentFrame());
                    if (rewind == null) assertRetiredHistory(session, outgoingController);
                } else if (GameServices.level().getCurrentZone() == 4 && !loadedFbz) {
                    assertTrue(outgoingHistory > 10, "outgoing MHZ live history must exist");
                    assertNotNull(outgoingController);
                    assertRetiredHistory(session, outgoingController);
                    loadedFbz = true;
                }
                var level = GameServices.level();
                if (loadedFbz && !isolatedHistory && !level.hasPendingFreshLevelTransitionBoundary()
                        && !level.isLevelInactiveForTransition()
                        && !owner(session.loop()).getFadeManager().hasPendingCompletion() && rewind != null) {
                    incomingController = rewind;
                    assertNotSame(outgoingController, rewind);
                    assertEquals(0, rewind.earliestAvailableFrame());
                    assertTrue(rewind.currentFrame() <= 1, "the first admitted FBZ history starts at frame zero");
                    assertTrue(rewind.currentFrame() < outgoingHistory,
                            "the actual FBZ load isolates the outgoing timeline");
                    isolatedHistory = true;
                }
            }
            drawing.checkpoint(session);
            assertTrue(loadedFbz, "the route observes the actual FBZ load");
            assertNotNull(incomingController, "FBZ retains the production rewind controller");
            assertNotNull(owner(session.loop()).getRewindController());
            assertTrue(isolatedHistory, "the route must observe the actual history-reset boundary");
            assertEquals(4, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
            assertTrue(session.player().getCentreX() > 128, "ordinary controller movement in playable FBZ");
        }
    }

    private static void assertRetiredHistory(GameplayCaptureSession session, RewindController outgoing) {
        var context = owner(session.loop());
        assertNotSame(outgoing, context.getRewindController());
        int frame = outgoing.currentFrame();
        var level = context.getLevelManager();
        int worldFrame = level.getFrameCounter();
        var objects = level.getObjectManager();
        outgoing.step();
        outgoing.seekTo(0);
        assertFalse(outgoing.stepBackward(), "retired MHZ history rejects replay");
        assertEquals(frame, outgoing.currentFrame());
        assertEquals(worldFrame, level.getFrameCounter());
        assertSame(objects, level.getObjectManager());
    }
}
