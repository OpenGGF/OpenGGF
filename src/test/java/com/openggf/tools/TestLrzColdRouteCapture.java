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

/** Ordinary cold LRZ1 approach, scripted corkscrew and lower westbound route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzColdRouteCapture {
    @Test void coldTeamTraversesCorkscrewAndRestoresItsHorizontalExit() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz1-sonic-tails-cold-corkscrew-320.bk2"));
        // Short earlier route: intro, rocks/door, platforms, button, capture,
        // scripted ride, native release, lower platform and westbound descent.
        var spots = Set.of(200, 600, 950, 1300, 1800, 2200, 2600, 2900,
                3100, 3140, 3250, 3370, 3410, 3470, 3530, 3555, 3600,
                3750, 4000, 4200, 4400);
        var checked = new java.util.HashSet<Integer>();
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame)); session.render();
                assertFalse(session.player().getDead(), "death at " + frame);
                if (frame == 3394) {
                    assertTrue(session.player().isObjectControlled());
                    assertEquals(0, session.player().getAngle(), "loc_422E6 clears approach angle");
                }
                if (frame == 3558) {
                    // Comparison-only native row3558, immediately after loc_42396:
                    // position $1235,$057C; velocity $F000,0; ground speed $F000.
                    assertFalse(session.player().isObjectControlled());
                    assertEquals(0x1235, session.player().getCentreX());
                    assertEquals(0x057C, session.player().getCentreY());
                    assertEquals(-0x1000, session.player().getXSpeed());
                    assertEquals(0, session.player().getYSpeed());
                    assertEquals(-0x1000, session.player().getGSpeed());
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
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
                previousInput.set(session, movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(2746, session.player().getCentreX());
            assertEquals(1186, session.player().getCentreY());
            assertEquals(93, session.player().getRingCount());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }
}
