package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold traversal to DEZ1's upper moving pad; not a complete act route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezColdRouteCapture {
    @Test void coldUpperRouteTraversesLiftsConveyorsAndTimedBridgesWithRewind() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez1-sonic-tails-cold-upper-320.bk2"));
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        // Intro/bridge, lift catches/releases, tube, conveyor lift, timed bridge,
        // spring ascent and the upper moving platform. Every spot restores the full registry.
        var spots = Set.of(650, 1260, 1700, 2100, 2182, 2400, 2500, 2640, 2700,
                3050, 3150, 3240, 3750, 4110, 4300, 4370, 4450, 4650, 4840, 4950, 5200);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (!spots.contains(frame)) continue;
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                previousInput.set(session, movie.getFrame(frame));
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
                // Capture-session held input belongs to the external input driver.
                previousInput.set(session, movie.getFrame(frame));
            }
            assertEquals(11, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(9589, session.player().getCentreX());
            assertEquals(640, session.player().getCentreY());
            assertEquals(32, session.player().getRingCount());
            assertTrue(session.player().isOnObject(), "upper moving pad carries the player");
        }
    }
}
