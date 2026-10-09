package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.DezMinibossInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
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
        runColdRoute("sonic", Sonic.class, 23533, 22332, spots);
    }

    @Test
    void coldTailsAloneClearsMinibossAndReachesPlayableActTwo() throws Exception {
        // Lower opening return, flight, conveyor, upper pads, lower corridor,
        // turbine panels, both launcher chains, rising stair and both boss phases.
        var spots = Set.of(480, 700, 1000, 1156, 1400, 1757, 2100, 2357,
                2508, 2700, 3040, 3250, 3455, 3720, 3900, 4085, 4400, 4710,
                4936, 5250, 5500, 5856, 6100, 6500, 6740, 6900, 7100, 7220,
                7460, 7800, 8040, 8400, 8750, 9061, 9500, 10100, 11000,
                12361, 13200, 14500, 15600, 16861, 16962, 17362, 17482,
                17720, 18100, 18445, 18900, 19365, 19772, 20057, 20120,
                20348, 20603, 20748, 23000, 25705, 26543, 26591, 26700,
                26899, 27331, 27761, 27807, 27849, 27886, 27923, 27962,
                28100, 28200, 28360, 28600, 29100, 29450);
        runColdRoute("tails", Tails.class, 29521, 28320, spots);
    }

    @Test
    void coldWideSonicClearsBothBossPhasesAndReachesPlayableActTwo() throws Exception {
        // Independently reproduced 800px buttons: opening lifts, loop/conveyor,
        // all six turbine panels, moving staircase, both boss phases and release.
        // No replay window straddles the actual seamless load at 19889.
        var spots = Set.of(260, 470, 1730, 1895, 2050, 2320, 2590, 3290,
                3571, 3931, 4431, 5091, 5551, 5630, 5958, 6340, 6730, 6884,
                7010, 7395, 7795, 11205, 13020, 13174, 14450, 15150, 15190,
                15200, 15580, 16000, 16500, 16900, 17230, 17410, 17600,
                17900, 18200, 18520, 18760, 18850, 18940, 19100, 19390,
                19510, 19532, 19600, 19700, 19820, 19840, 19890, 19950, 20090, 20400, 20600, 20700, 20800, 20900, 21050);
        runColdRoute("sonic", Sonic.class, 21100, 19889, spots, 800,
                "dez1-sonic-cold-800.bk2", 2);
    }

    private void runColdRoute(String character, Class<?> playerType, int frames,
                              int expectedLoadFrame, Set<Integer> spots) throws Exception {
        runColdRoute(character, playerType, frames, expectedLoadFrame, spots, 320,
                "dez1-" + character + "-solo-cold-complete-320.bk2", 1);
    }

    private void runColdRoute(String character, Class<?> playerType, int frames,
                              int expectedLoadFrame, Set<Integer> spots, int width,
                              String movieName, int replayCycles) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/" + movieName));
        assertEquals(frames, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(width, character, "", "off", null, null, null);
        int historyStart = expectedLoadFrame - 72;
        var checked = new HashSet<Integer>();
        var hitField = DezMinibossInstance.class.getSuperclass().getDeclaredField("collisionProperty");
        hitField.setAccessible(true);
        int phases = 0, loadFrame = -1;
        boolean eightHits = false;
        long outgoingFrame = 0;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            assertInstanceOf(playerType, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == historyStart) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertEquals(width, GameServices.camera().getWidth());
                assertFalse(session.player().isSuperSonic());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (GameServices.level().getCurrentAct() == 0) {
                    if (frame >= historyStart) outgoingFrame = Math.max(outgoingFrame,
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                var forward = registry.capture();
                for (int cycle = 0; cycle < replayCycles; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore at " + frame + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                    same(forward, registry.capture(), "replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertEquals(2, phases, "both eight-hit phases must be defeated through player contact");
            assertEquals(expectedLoadFrame, loadFrame);
            assertEquals(11, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled(), "incoming transport must release control");
            assertTrue(outgoingFrame > 10);
            // DEZ1's ROM handoff is seamless: retain numbering but discard outgoing snapshots.
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(rewind.earliestAvailableFrame() > outgoingFrame);
            assertInstanceOf(playerType, session.player());
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
