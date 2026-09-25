package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance;
import com.openggf.game.sonic3k.objects.LrzDoorObjectInstance;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold solo Tails traversal, six real drill hits, results and playable LRZ2. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzTailsColdRouteCapture {
    @Test
    void coldTailsClearsActOneAndRestoresTraversalFightAndHandoff() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-act1-clear-320.bk2"));
        assertEquals(34128, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Flight/landing, buttons, corkscrew, elevators, crusher collapse, low
        // passages, arena entry, each drill hit and both sides of the act load.
        var spots = Set.of(200, 1500, 2200, 3056, 3390, 3900, 5600, 6500,
                7000, 7600, 8130, 8350, 8521, 9800, 10983, 11020, 11400,
                11595, 11715, 12930, 13460, 14145, 15568, 16068, 17418,
                18608, 18905, 19443, 20554, 21020, 21553, 21673, 21853,
                22238, 22626, 23336, 24560, 24767, 25257, 25520, 25660,
                26410, 28650, 30260, 31340, 32420, 32920, 33000, 33420,
                33500, 34050);
        var checked = new HashSet<Integer>();
        var healthSeen = new HashSet<Integer>();
        boolean defeated = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
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
                if (frame == 33477) {
                    assertEquals(1, GameServices.level().getCurrentAct(), "seamless act load");
                    assertTrue(session.player().getCentreX() < 320, "native -$2C00 rebase");
                    assertEquals(1, session.player().getRingCount(), "carry remains until the title reset");
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
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
        }
    }

    @Test
    void coldTailsRestoresActTwoTraversalToTheMiddleCorridor() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-act2-middle-320.bk2"));
        assertEquals(41922, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Moving platforms, both turbine captures/releases, flight, monitor alcove,
        // collision-path switches, spring return and the lower badnik passage.
        var spots = Set.of(34300, 34820, 35080, 35468, 35680, 35848, 35939,
                36261, 36361, 36461, 36736, 37181, 37370, 37421, 37586,
                37867, 38142, 38340, 39222, 39612, 39950, 41366, 41446, 41600, 41800);
        var checked = new HashSet<Integer>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(9, GameServices.level().getCurrentZone());
                assertEquals(1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(5497, session.player().getCentreX());
            assertEquals(1008, session.player().getCentreY());
            assertEquals(1, session.player().getRingCount());
        }
    }

    @Test
    void coldTailsCompletesActTwoAndRestoresTheBoulderHandoff() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-tails-cold-act2-clear-320.bk2"));
        assertEquals(59712, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Lower return, door8, Fireworm timing, both eastern turbines, breakable
        // walls and tunnel, doors5/6, flight/platform landings, stairs and boulder.
        // Separate post-load spots never replay across the act-load boundary.
        // Stop the last source replay before its callback-bearing fade begins;
        // GameLoop.isRewindBlocked deliberately excludes that host transition.
        var spots = Set.of(42100, 43200, 43700, 43826, 43982, 44500, 45192,
                45600, 46000, 46182, 46320, 46363, 46800, 47183, 47800,
                48393, 49000, 49603, 50113, 50153, 50251, 50633, 51283,
                51800, 52493, 52824, 53245, 53800, 54245, 54600, 55000,
                55345, 55375, 55460, 55500, 55620, 55740, 55830, 55920,
                56062, 56243, 56300, 56493, 56734, 57055, 57296, 57477,
                57649, 57800, 58259, 58700, 58754, 58950, 59050, 59200,
                59300, 59400, 59430, 59550, 59650);
        var checked = new HashSet<Integer>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Tails.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame == 46320 || frame == 55500 || frame == 55920) {
                    int trigger = frame == 46320 ? 8 : frame == 55500 ? 5 : 6;
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(LrzDoorObjectInstance.class).stream()
                            .anyMatch(door -> door.triggerIndex() == trigger && door.isFullyOpen()),
                            "ordinary controller approach opens door" + trigger);
                }
                if (frame == 59510) {
                    assertEquals(9, GameServices.level().getCurrentZone());
                    assertEquals(1, GameServices.level().getCurrentAct());
                    assertTrue(session.player().isObjectControlled(), "boulder owns the final drop");
                    assertEquals(0, session.player().getRingCount());
                }
                if (frame == 59511) {
                    assertEquals(22, GameServices.level().getCurrentZone());
                    assertEquals(0, GameServices.level().getCurrentAct());
                    assertEquals(0, session.player().getRingCount(), "act load retains the ordinary carry");
                }
                if (!spots.contains(frame)) continue;
                assertEquals(frame < 59511 ? 9 : 22, GameServices.level().getCurrentZone());
                assertEquals(frame < 59511 ? 1 : 0, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
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

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }
}
