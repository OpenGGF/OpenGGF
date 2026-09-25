package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance;
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

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }
}
