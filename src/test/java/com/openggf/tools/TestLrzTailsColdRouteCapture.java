package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance;
import com.openggf.game.sonic3k.objects.LrzDoorObjectInstance;
import com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance;
import com.openggf.game.sonic3k.objects.LrzEndBossEggCapsule;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold solo Tails through both main acts, both bosses and playable Hidden Palace. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzTailsColdRouteCapture {
    @Tag("exhaustive-audit")
    @Test
    void coldTailsClearsActOneAndRestoresTraversalFightAndHandoff() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-act1-clear-320.bk2"));
        assertEquals(29157, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Flight/landing, buttons, corkscrew, elevators, crusher collapse, low
        // passages, arena entry, each drill hit and both sides of the act load.
        var spots = Set.of(200, 1500, 2200, 3056, 3390, 3900, 5600, 6500,
                7000, 7600, 8130, 8350, 8521, 9800, 10983, 11020, 11400,
                11595, 11715, 12930, 13460, 14145, 15568, 16068, 17418,
                18608, 18905, 19443, 20554, 21020, 21553, 21673, 21853,
                22238, 22626, 23336, 24560, 24767, 25257, 25520, 25660,
                25852, 25980, 26401, 26441, 26810, 27250, 27310, 27650,
                28016, 28110, 28160, 28300, 28450, 28530, 28925, 28980, 29100);
        var checked = new HashSet<Integer>();
        var healthSeen = new HashSet<Integer>();
        boolean defeated = false;
        long outgoingHistory = 0;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 28000) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                if (frame >= 28000 && GameServices.level().getCurrentAct() == 0) {
                    outgoingHistory = Math.max(outgoingHistory, SessionManager.getCurrentGameplayMode()
                            .getRewindController().currentFrame());
                }
                assertFalse(session.player().getDead(), "death at input " + frame);
                for (var boss : GameServices.level().getObjectManager()
                        .activeObjectsOfType(LrzMinibossInstance.class)) {
                    healthSeen.add(boss.getCollisionProperty());
                    defeated |= boss.getState().defeated;
                }
                if (frame == 25520) {
                    assertTrue(session.player().isHighPriority(), "placed lava path switch was crossed");
                    assertEquals(9, session.player().getRingCount(), "ordinary approach carry");
                }
                if (frame == 28507) {
                    assertEquals(1, GameServices.level().getCurrentAct(), "seamless act load");
                    assertTrue(session.player().getCentreX() < 320, "native -$2C00 rebase");
                    assertEquals(1, session.player().getRingCount(), "carry remains until the title reset");
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(defeated);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6), healthSeen);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled(), "Act2 movement released after results/title");
            assertEquals(427, session.player().getCentreX());
            assertEquals(1973, session.player().getCentreY());
            assertEquals(0, session.player().getRingCount(), "Act2 title has reset the act counters");
            assertTrue(outgoingHistory > 10, "record actual outgoing live history");
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(rewind.earliestAvailableFrame() > outgoingHistory,
                    "seamless handoff retires outgoing Act1 history before recording Act2");
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldTailsRestoresActTwoTraversalToTheMiddleCorridor() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-act2-middle-320.bk2"));
        assertEquals(36714, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Moving platforms, both turbine captures/releases, flight, monitor alcove,
        // collision-path switches, spring return and the lower badnik passage.
        var spots = Set.of(29329, 29849, 30109, 30500, 30709, 30880, 30971,
                31293, 31372, 31472, 31762, 32100, 32190, 32241, 32378,
                32659, 32934, 33132, 34014, 34404, 34742, 36158, 36238, 36392, 36592);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(9, GameServices.level().getCurrentZone());
                assertEquals(1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(5497, session.player().getCentreX());
            assertEquals(1008, session.player().getCentreY());
            assertEquals(2, session.player().getRingCount(), "ordinary braking preserves both earned rings");
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldTailsCompletesActTwoAndRestoresTheBoulderHandoff() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-act2-clear-320.bk2"));
        assertEquals(53270, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Lower return, door8, cloud escape and flame timing, both eastern turbines, breakable
        // walls and tunnel, doors5/6, flight/platform landings, stairs and boulder.
        // Separate post-load spots never replay across the act-load boundary.
        // Stop the last source replay before its callback-bearing fade begins;
        // GameLoop.isRewindBlocked deliberately excludes that host transition.
        var spots = Set.of(36892, 37992, 38300, 38426, 38582, 39100, 39792,
                40200, 40600, 40782, 40920, 40963, 41400, 41783,
                42000, 42196, 42472, 42592, 42770, 43090, 43145,
                43240, 43327, 43331, 43841, 43881, 43979, 44361,
                45011, 45528, 46221, 46552, 46973, 47528, 47973,
                48328, 48728, 49073, 49103, 49188, 49228, 49348,
                49468, 49558, 49648, 49790, 49971, 50028, 50221,
                50462, 50783, 51024, 51205, 51320, 51460, 51650,
                51760, 51960, 52020, 52050, 52220, 52400, 52510,
                52600, 52700, 52800, 52950, 52977, 53111, 53191,
                53211);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame == 40920 || frame == 49228 || frame == 49648) {
                    int trigger = frame == 40920 ? 8 : frame == 49228 ? 5 : 6;
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(LrzDoorObjectInstance.class).stream()
                            .anyMatch(door -> door.triggerIndex() == trigger && door.isFullyOpen()),
                            "ordinary controller approach opens door" + trigger);
                }
                if (frame == 53068) {
                    assertEquals(9, GameServices.level().getCurrentZone());
                    assertEquals(1, GameServices.level().getCurrentAct());
                    assertTrue(session.player().isObjectControlled(), "boulder owns the final drop");
                    assertEquals(0, session.player().getRingCount());
                }
                if (frame == 53069) {
                    assertEquals(22, GameServices.level().getCurrentZone());
                    assertEquals(0, GameServices.level().getCurrentAct());
                    assertEquals(0, session.player().getRingCount(), "act load retains the ordinary carry");
                }
                if (!spots.contains(frame)) continue;
                assertEquals(frame < 53069 ? 9 : 22, GameServices.level().getCurrentZone());
                assertEquals(frame < 53069 ? 1 : 0, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(22, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(296, session.player().getCentreX());
            assertEquals(1200, session.player().getCentreY());
            assertEquals(1, session.player().getRingCount());
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldTailsCompletesBossActAndReachesPlayableHiddenPalace() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-hpz-320.bk2"));
        assertEquals(62546, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Arrival flash, missiles, flight/landings, checkpoint, earned shield,
        // native left-bound arena entry, mine cycles, capsule/results and HPZ.
        // The outgoing callback-bearing load fade has separate destination spots.
        var spots = Set.of(53278, 53358, 53458, 53608, 53721, 53808, 53908,
                54058, 54176, 54308, 54458, 54621, 54802, 54923,
                55058, 55258, 55408, 55460, 55558, 55731, 55808,
                55925, 55958, 56058, 56258, 56458, 56608, 56708,
                56808, 56908, 57058, 57208, 57308, 57408, 57658,
                57958, 58158, 58308, 58408, 58658, 58958, 59108,
                59258, 59358, 59658, 59958, 60108, 60208, 60308,
                60558, 60858, 61008, 61108, 61208, 61358, 61558,
                61758, 61958, 62158, 62308, 62449, 62489);
        var checked = new HashSet<Integer>();
        var healthSeen = new HashSet<Integer>();
        boolean sawCapsule = false, sawResults = false;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame < 53270) continue;
                var manager = GameServices.level().getObjectManager();
                for (var boss : manager.activeObjectsOfType(LrzEndBossObjectInstance.class)) {
                    healthSeen.add(boss.getCollisionProperty());
                }
                sawCapsule |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.class).isEmpty();
                sawResults |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.Results.class).isEmpty();
                if (frame == 53270) assertFalse(session.player().hasShield(), "ordinary cold carry");
                if (frame == 55958) {
                    assertEquals(ShieldType.FIRE, session.player().getShieldType(), "placed monitor pickup");
                    assertEquals(12, session.player().getRingCount());
                }
                if (frame >= 55958) assertFalse(session.player().isHurt(), "encounter/exit hurt at " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(22, GameServices.level().getCurrentZone());
                assertEquals(frame < 62424 ? 0 : 1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "boss-act restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "boss-act replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), healthSeen);
            assertTrue(sawCapsule && sawResults, "real defeat/capsule/results publication");
            assertEquals(22, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(393, session.player().getCentreX());
            assertEquals(2800, session.player().getCentreY());
            assertEquals(3, session.player().getRingCount());
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }
}
