package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold 800px Sonic+Tails inputs; no position, clock, ring or boss-state seeds. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszWidePairedColdRouteCapture {
    @Test
    void coldFirstReplicaAndTransportReplayWithRetiredColumnDebris() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-first-replica-800.bk2"));
        assertEquals(9914, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off",
                null, null, null);
        // Arrival, bridge, traversal, column release/retirement, preliminary and final
        // arena bounds, the fight, escape, raised pad and receiving platform.
        Set<Integer> spots = Set.of(30, 120, 400, 1000, 1300, 1390, 1500, 2000,
                2500, 3000, 3500, 4000, 4500, 5000, 5500, 5900, 6040, 6160,
                6212, 6257, 6300, 6500, 6800, 7100, 7400, 7500, 7600, 7750,
                7900, 7951, 8050, 8150, 8500, 8751, 9000, 9312, 9350, 9450,
                9550, 9750);
        var checked = new HashSet<Integer>();
        boolean seen = false;
        int health = 8, hits = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertInstanceOf(Sonic.class, session.player());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                for (var boss : GameServices.level().getObjectManager()
                        .activeObjectsOfType(SszGhzBossObjectInstance.class)) {
                    seen = true;
                    int next = boss.hitsRemainingForTest();
                    assertTrue(next <= health, "GHZ replica cannot regain health");
                    hits += health - next;
                    health = next;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore at " + frame + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {
                        session.step(movie.getFrame(frame + n)); session.render();
                    }
                    same(forward, registry.capture(), "replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertTrue(seen);
            assertEquals(8, hits);
            assertEquals(0, health);
            assertEquals(10, GameServices.level().getCurrentZone());
            assertEquals(512, session.player().getCentreX());
            assertEquals(1420, session.player().getCentreY(), "first receiving platform");
            assertEquals(46, session.player().getRingCount());
            assertFalse(session.player().isObjectControlled(), "pad released ordinary movement");
            System.out.println("SSZ wide cold first replica: " + checked.size()
                    + " two-cycle rewind windows");
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }
}
