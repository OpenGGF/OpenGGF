package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.DezMinibossInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary solo inputs from cold DEZ1 entry through both boss phases and the seamless handoff. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezSoloColdRouteCapture {
    @Test
    void coldSonicAloneClearsMinibossAndReachesPlayableActTwo() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez1-sonic-solo-cold-complete-320.bk2"));
        assertEquals(23533, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        // Lifts, bridges, conveyor departure, turbine panels/door, launcher chains,
        // rising staircase, both eight-hit phases, defeat and incoming transport.
        // Source replay windows end before the actual seamless load at 22332.
        var spots = Set.of(650, 1260, 1700, 2182, 2640, 3150, 3750, 4300, 4840,
                5200, 5540, 5960, 6410, 6760, 7290, 7560, 7750, 8100, 8470,
                9015, 9200, 9600, 10000, 10500, 11000, 11400, 11640, 11988,
                12100, 12470, 12540, 12620, 13015, 13300, 13615, 14100,
                14395, 14802, 14952, 15013, 15295, 15430, 15478, 18000,
                20493, 20541, 20650, 20850, 20899, 21436, 21472, 21510,
                21546, 21931, 21974, 22100, 22200, 22360, 22700, 23200, 23450);
        var checked = new HashSet<Integer>();
        var hitField = DezMinibossInstance.class.getSuperclass().getDeclaredField("collisionProperty");
        hitField.setAccessible(true);
        int phases = 0, loadFrame = -1;
        boolean eightHits = false;
        long outgoingFrame = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            assertInstanceOf(Sonic.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 22260) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (GameServices.level().getCurrentAct() == 0) {
                    if (frame >= 22260) outgoingFrame = Math.max(outgoingFrame,
                            SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                } else if (loadFrame < 0) loadFrame = frame;
                var boss = GameServices.level().getObjectManager().activeObjectsOfType(DezMinibossInstance.class)
                        .stream().findFirst().orElse(null);
                boolean nowEight = boss != null && hitField.getInt(boss) == 8;
                if (nowEight && !eightHits) phases++;
                eightHits = nowEight;
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(2, phases, "both eight-hit phases must be defeated through player contact");
            assertEquals(22332, loadFrame);
            assertEquals(11, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled(), "incoming transport must release control");
            assertTrue(outgoingFrame > 10);
            // DEZ1's ROM handoff is seamless: retain numbering but discard outgoing snapshots.
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(rewind.earliestAvailableFrame() > outgoingFrame);
            assertInstanceOf(Sonic.class, session.player());
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
