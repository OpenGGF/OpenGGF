package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.MhzMinibossInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold locked-on Knuckles routes; pad input drives the production GameLoop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzKnucklesColdRouteCapture {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void coldKnucklesCompletesActOneAndRestoresNativeInteractions() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz1-knuckles-cold-complete-320.bk2"));
        assertEquals(30000, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // The locked-on intro admission, ground loop, blower/wall climb, upper
        // bar/pulley, sticky-vine release, lower twisted-vine ascent, second
        // pulley and all six real boss hits each have independent replay windows.
        var spots = Set.of(800, 1000, 1260, 1440, 1580, 1920, 4200, 4350,
                6081, 8250, 9250, 9360, 9780, 9930, 12372, 13300, 15446,
                15590, 17228, 21113, 23813, 25959, 25979, 26029, 26089,
                26140, 26400, 26500, 26983, 27450, 27500, 28283,
                28453, 28485, 28517, 28549, 28581, 28613, 28800,
                28980, 29070, 29580, 29920);
        var checked = new HashSet<Integer>();
        var health = new HashSet<Integer>();
        int loadFrame = -1;
        boolean defeated = false;
        long outgoingHistory = 0;
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 28800) GameServices.configuration().setSessionOverride(
                        SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertEquals(CharacterKey.KNUCKLES, session.player().characterKey());
                assertEquals(320, GameServices.camera().getWidth());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                assertFalse(session.player().getDead(), "cold Knuckles death at " + frame);
                assertFalse(session.player().isSuperSonic());
                var level = GameServices.level();
                assertEquals(7, level.getCurrentZone());
                if (level.getCurrentAct() == 0 && frame >= 28800) {
                    outgoingHistory = Math.max(outgoingHistory,
                            SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                } else if (level.getCurrentAct() == 1 && loadFrame < 0) {
                    loadFrame = frame;
                    assertTrue(session.player().getCentreX() < 512, "native -$4200 rebase");
                }
                for (var boss : level.getObjectManager().activeObjectsOfType(MhzMinibossInstance.class)) {
                    health.add(boss.getState().hitCount);
                    defeated |= boss.isDefeated();
                }
                if (!spots.contains(frame)) continue;
                assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode());
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                var savedLevel = level.getCurrentLevel();
                var savedAct = level.getCurrentAct();
                int horizon = Math.min(45, movie.getFrameCount() - frame - 1);
                if (frame < 29044) horizon = Math.min(horizon, 29043 - frame);
                assertTrue(horizon > 0);
                for (int n = 1; n <= horizon; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                    assertSame(savedLevel, level.getCurrentLevel(), "same level replay at " + frame);
                    assertEquals(savedAct, level.getCurrentAct(), "same act replay at " + frame);
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore at " + frame + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= horizon; n++) {
                        session.step(movie.getFrame(frame + n));
                        drawing.draw(session);
                    }
                    same(forward, registry.capture(), "replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                checked.add(frame);
            }
            assertEquals(spots, checked);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6), health);
            assertTrue(defeated);
            assertEquals(29044, loadFrame);
            assertEquals(1, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
            assertFalse(session.player().isControlLocked());
            assertTrue(session.player().getCentreX() > 1000, "ordinary released MHZ2 movement");
            assertTrue(outgoingHistory > 10, "record real outgoing live history");
            assertTrue(SessionManager.getCurrentGameplayMode().getRewindController().earliestAvailableFrame()
                    > outgoingHistory, "seamless handoff retires outgoing history while retaining numbering");
            System.out.println("MHZ Knuckles Act1: " + checked.size() + " two-cycle whole-world windows");
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
