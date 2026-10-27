package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzEndBossEggCapsule;
import com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.GameLoopRewindTestAccess.owner;

/** Cold widescreen traversal, placed shield, real mine fight, capsule and playable HPZ. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzWideBossColdRouteCapture {
    @Tag("exhaustive-audit")
    @Test
    void coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-boss-sonic-tails-cold-hpz-800.bk2"));
        assertEquals(50681, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var spots = new TreeSet<Integer>();
        for (int frame = 41411; frame <= 50211; frame += 200) spots.add(frame);
        spots.addAll(Set.of(41381, 42211, 42261, 42551, 42971, 43891, 43931,
                44031, 44091, 44611, 44681, 44851, 44871, 45251, 45351,
                45441, 49071, 49171, 49201, 49311, 49351, 49551, 50051,
                50351, 50431, 50491, 50572, 50622));
        var checked = new HashSet<Integer>();
        boolean sawBoss = false, sawCapsule = false, sawResults = false, sawDefeat = false;
        long outgoingHistory = 0;
        RewindController outgoingController = null, incomingController = null;
        int hpzLoad = -1;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 50111) GameServices.configuration().setSessionOverride(
                        SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                incomingController = observeIncomingHistory(session, outgoingController, incomingController);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame < 41381) continue;
                assertEquals(800, GameServices.camera().getWidth());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                assertEquals(22, GameServices.level().getCurrentZone());
                assertEquals(frame < 50560 ? 0 : 1, GameServices.level().getCurrentAct());
                var manager = GameServices.level().getObjectManager();
                var bosses = manager.activeObjectsOfType(LrzEndBossObjectInstance.class);
                sawBoss |= !bosses.isEmpty();
                sawCapsule |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.class).isEmpty();
                sawResults |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.Results.class).isEmpty();
                if (frame == 44864) assertEquals(14, bosses.getFirst().getCollisionProperty());
                if (frame == 49188) {
                    assertEquals(0, bosses.getFirst().getCollisionProperty());
                    sawDefeat = true;
                }
                if (frame == 44697) {
                    assertEquals(ShieldType.FIRE, session.player().getShieldType(), "placed monitor supplies shield");
                    assertEquals(30, session.player().getRingCount());
                }
                if (frame >= 44697 && frame < 50560) {
                    assertFalse(session.player().isHurt(), "hurt in encounter/exit at " + frame);
                    assertEquals(ShieldType.FIRE, session.player().getShieldType());
                }
                if (frame >= 50111 && frame < 50560) {
                    var installed = owner(session.loop()).getRewindController();
                    if (installed != null) outgoingController = installed;
                    assertNotNull(outgoingController, "the enabled boss route records before its exit");
                    outgoingHistory = Math.max(outgoingHistory, outgoingController.currentFrame());
                    if (installed == null) assertRetiredHistory(session, outgoingController);
                }
                if (GameServices.level().getCurrentAct() == 1 && hpzLoad < 0) {
                    hpzLoad = frame;
                    assertNotNull(outgoingController);
                    assertRetiredHistory(session, outgoingController);
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                    incomingController = observeIncomingHistory(session, outgoingController, incomingController);
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {
                        session.step(movie.getFrame(frame + n)); drawing.draw(session);
                        incomingController = observeIncomingHistory(session, outgoingController, incomingController);
                    }
                    var replay = registry.capture();
                    assertEquals(forward.entries().keySet(), replay.entries().keySet());
                    for (String key : forward.entries().keySet()) {
                        var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                        assertTrue(differences.isEmpty(), "input " + frame + " cycle " + cycle + " " + key + ": " + differences);
                    }
                }
                registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(sawBoss && sawDefeat && sawCapsule && sawResults, "real encounter publication chain");
            assertEquals(50560, hpzLoad);
            assertFalse(session.player().isObjectControlled());
            assertTrue(session.player().getCentreX() > 100, "ordinary movement after HPZ load");
            assertEquals(2796, session.player().getCentreY());
            assertTrue(outgoingHistory > 10);
            assertNotNull(incomingController, "the route reaches an admitted HPZ timeline");
            var rewind = owner(session.loop()).getRewindController();
            assertNotNull(rewind, "released HPZ gameplay requires production history");
            assertEquals(0, rewind.earliestAvailableFrame());
            assertTrue(rewind.currentFrame() < outgoingHistory,
                    "HPZ full load retires the outgoing boss timeline");
            System.out.println("LRZ wide cold boss clear: " + checked.size() + " two-cycle rewind windows");
        }
    }

    private static RewindController observeIncomingHistory(GameplayCaptureSession session,
            RewindController outgoing, RewindController first) {
        var context = owner(session.loop());
        var level = context.getLevelManager();
        if (first == null && level.getCurrentZone() == 22 && level.getCurrentAct() == 1
                && !level.hasPendingFreshLevelTransitionBoundary() && !level.isLevelInactiveForTransition()
                && !context.getFadeManager().hasPendingCompletion()) {
            var installed = context.getRewindController();
            if (installed != null) {
                assertNotNull(outgoing);
                assertNotSame(outgoing, installed);
                assertEquals(0, installed.earliestAvailableFrame());
                assertTrue(installed.currentFrame() <= 1, "the first admitted HPZ history starts at frame zero");
                return installed;
            }
        }
        return first;
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
        assertFalse(outgoing.stepBackward(), "retired boss history rejects replay");
        assertEquals(frame, outgoing.currentFrame());
        assertEquals(worldFrame, level.getFrameCounter());
        assertSame(objects, level.getObjectManager());
    }
}
