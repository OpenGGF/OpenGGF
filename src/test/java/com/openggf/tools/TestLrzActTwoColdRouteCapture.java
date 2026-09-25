package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzDoorObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold Act1 arrival is part of the input, never a hydrated Act2 checkpoint. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzActTwoColdRouteCapture {
    @Test void coldTeamClimbsTurbinesAndOpensDoorEightWithRepeatableWorldState() throws Exception {
        // Opening pillars, stepped climb, lift, two turbine captures/releases,
        // upper spring/drop, descending platforms, orbiting balls, button/door,
        // chained platform and low pipe. No replay window crosses a level load.
        var spots = Set.of(31460, 31660, 31800, 32180, 32290, 32384, 32464, 32525,
                32598, 32700, 32858, 33023, 33085, 33152, 33333, 33580, 34180,
                34354, 34534, 34674, 35010, 35080, 35280, 35460, 35660, 35770,
                35838, 35930, 35980, 36020, 36090);
        runRoute("pipe-passage", spots, 36204, 9, 1, 6741, 1484, 1);
    }

    @Test void coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState() throws Exception {
        // Pipe exits, enemy corridor, turbine bank, springs/tunnel, floor and
        // side buttons, collapsing bridges, lift phases, inverted ceiling run,
        // final stairs, boulder phases, then independent post-load replay spots.
        var spots = Set.of(36240, 36300, 36450, 36540, 36690, 36720, 36734, 36780,
                36870, 37020, 37200, 37830, 37965, 38340, 38491, 38551, 38800,
                39031, 39180, 39426, 39486, 39577, 39830, 39910, 40140, 40365,
                40432, 40625, 40667, 40757, 40822, 40942, 41580, 41640, 41862,
                41982, 42100, 42190, 42360, 42540, 42750, 42870, 43020, 43150,
                43260, 43350, 43440, 43500, 43680, 43710);
        runRoute("boss-act-arrival", spots, 43761, 22, 0, 296, 1196, 6);
    }

    private void runRoute(String route, Set<Integer> spots, int frameCount,
                          int zone, int act, int x, int y, int rings) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz2-sonic-tails-cold-" + route + "-320.bk2"));
        var checked = new HashSet<Integer>();
        assertEquals(frameCount, movie.getFrameCount());
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame == 35980) {
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(LrzDoorObjectInstance.class).stream()
                            .anyMatch(door -> door.triggerIndex() == 8 && door.isFullyOpen()),
                            "the approach jump must press the real button and open door8");
                }
                // Door6 starts opening at39911; ROM travel takes64 updates.
                if (frame == 39577 || frame == 39980) {
                    int trigger = frame == 39577 ? 5 : 6;
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(LrzDoorObjectInstance.class).stream()
                            .anyMatch(door -> door.triggerIndex() == trigger && door.isFullyOpen()),
                            "ordinary button approach opens door" + trigger);
                }
                if (frame == 43560) {
                    assertEquals(9, GameServices.level().getCurrentZone());
                    assertEquals(1, GameServices.level().getCurrentAct());
                    assertTrue(session.player().isObjectControlled(), "the boulder owns the drop");
                    assertEquals(5, session.player().getRingCount());
                }
                if (frame == 43570) {
                    assertEquals(22, GameServices.level().getCurrentZone());
                    assertEquals(0, GameServices.level().getCurrentAct());
                    assertEquals(5, session.player().getRingCount(), "StartNewLevel retains carried rings");
                }
                if (!spots.contains(frame)) continue;
                assertEquals(frame < 43570 ? 9 : 22, GameServices.level().getCurrentZone());
                assertEquals(frame < 43570 ? 1 : 0, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var replay = registry.capture();
                assertEquals(forward.entries().keySet(), replay.entries().keySet());
                for (String key : forward.entries().keySet()) {
                    var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                    assertTrue(differences.isEmpty(), "input " + frame + " " + key + ": " + differences);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(zone, GameServices.level().getCurrentZone());
            assertEquals(act, GameServices.level().getCurrentAct());
            assertEquals(x, session.player().getCentreX());
            assertEquals(y, session.player().getCentreY());
            assertEquals(rings, session.player().getRingCount());
            assertFalse(session.player().isObjectControlled());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }
}
