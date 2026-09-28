package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzRockCrusherObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold widescreen traversal to the first crusher, independent of the longer native clear. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzWideColdRouteCapture {
    @Test
    void coldTeamReachesCrusherCollapseAndCameraReleaseWithRepeatableWorldState() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz1-sonic-tails-cold-crusher-800.bk2"));
        assertEquals(6853, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var spots = Set.of(200, 600, 1000, 1400, 1800, 2200, 2290, 2600, 3000, 3060,
                3310, 3500, 3660, 3880, 4170, 4300, 4390, 4510, 4600, 4900, 5000,
                5200, 5500, 5800, 5900, 6010, 6100, 6200, 6300, 6400, 6500, 6600, 6700, 6780);
        var checked = new HashSet<Integer>();
        boolean sawRumble = false, sawDrop = false, sawRelease = false;
        int previousPhase = -2;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertEquals(800, GameServices.camera().getWidth());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                var crusher = GameServices.level().getObjectManager()
                        .activeObjectsOfType(LrzRockCrusherObjectInstance.class).stream()
                        .filter(c -> c.getSpawn().subtype() == 0).findFirst().orElse(null);
                int phase = crusher == null ? -2 : crusher.initialised() ? crusher.routine() : -1;
                sawRumble |= phase == 2;
                sawDrop |= phase >= 4;
                sawRelease |= sawDrop && GameServices.camera().getMaxX() > 0xEA0;
                if (frame == 4600) assertEquals(ShieldType.FIRE, session.player().getShieldType(),
                        "the upper ledge's placed monitor supplies the shield");
                boolean phaseEdge = frame > 5900 && phase != previousPhase;
                previousPhase = phase;
                if ((!spots.contains(frame) && !phaseEdge) || frame + 45 >= movie.getFrameCount()) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {
                        session.step(movie.getFrame(frame + n)); session.render();
                    }
                    var replay = registry.capture();
                    assertEquals(forward.entries().keySet(), replay.entries().keySet());
                    var allDifferences = new java.util.ArrayList<String>();
                    for (String key : forward.entries().keySet()) {
                        var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                        for (String difference : differences) allDifferences.add(key + ": " + difference);
                    }
                    assertTrue(allDifferences.isEmpty(), "input " + frame + " cycle " + cycle + ": " + allDifferences);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertTrue(checked.containsAll(spots));
            assertTrue(sawRumble && sawDrop && sawRelease, "actual rumble, collapse and bounds release");
            assertTrue(session.player().getCentreX() > 0xFC8, "past the previously impassable wall");
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            System.out.println("LRZ wide cold crusher: " + checked.size() + " two-cycle rewind windows");
        }
    }
}
