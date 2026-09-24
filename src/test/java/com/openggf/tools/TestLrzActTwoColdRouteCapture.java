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
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz2-sonic-tails-cold-pipe-passage-320.bk2"));
        // Opening pillars, stepped climb, lift, two turbine captures/releases,
        // upper spring/drop, descending platforms, orbiting balls, button/door,
        // chained platform and low pipe. No replay window crosses a level load.
        var spots = Set.of(31460, 31660, 31800, 32180, 32290, 32384, 32464, 32525,
                32598, 32700, 32858, 33023, 33085, 33152, 33333, 33580, 34180,
                34354, 34534, 34674, 35010, 35080, 35280, 35460, 35660, 35770,
                35838, 35930, 35980, 36020, 36090);
        var checked = new HashSet<Integer>();
        assertEquals(36204, movie.getFrameCount());
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
                if (!spots.contains(frame)) continue;
                assertEquals(1, GameServices.level().getCurrentAct());
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
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(6741, session.player().getCentreX());
            assertEquals(1484, session.player().getCentreY());
            assertEquals(1, session.player().getRingCount());
            assertFalse(session.player().isObjectControlled());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }
}
