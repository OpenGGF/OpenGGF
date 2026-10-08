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

    @Test
    void coldTeamClearsActOneAndReleasesPlayableActTwoWithIsolatedHistory() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz1-sonic-tails-cold-clear-800.bk2"));
        assertEquals(25650, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        // Periodic coverage plus the ordinary route's button, platform, cloud, boss and release edges.
        var spots = new java.util.TreeSet<Integer>();
        for (int frame = 200; frame < 24100; frame += 400) spots.add(frame);
        spots.addAll(Set.of(3060, 3310, 3660, 4170, 4300, 4390, 4600, 5900, 6298,
                6508, 6800, 7048, 7300, 9750, 9860, 10045, 10645, 10769, 10929,
                11053, 11213, 12780, 13010, 13225, 13340, 13505, 13650, 14129,
                14770, 15023, 15233, 15446, 15600, 15850, 16000, 16901, 17006,
                17196, 17400, 17800, 18200, 18450, 18546, 19320, 19370, 19450, 20180, 20890, 20990,
                22530, 22780, 23280, 23770, 23800, 23900, 24000, 24400, 24750, 25100, 25500));
        var checked = new HashSet<Integer>();
        int lastHealth = 6, hits = 0, defeatFrame = -1, loadFrame = -1;
        boolean bossSeen = false;
        long outgoingHistory = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 24150) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                assertEquals(800, GameServices.camera().getWidth());
                assertInstanceOf(com.openggf.sprites.playable.Sonic.class, session.player());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                assertEquals(9, GameServices.level().getCurrentZone());
                if (GameServices.level().getCurrentAct() == 0) {
                    if (frame >= 24150) outgoingHistory = Math.max(outgoingHistory,
                            SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                } else if (loadFrame < 0) {
                    loadFrame = frame;
                    assertTrue(session.player().getCentreX() < 400, "ROM -$2C00 world rebase");
                }
                var boss = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance.class)
                        .stream().findFirst().orElse(null);
                if (boss != null) {
                    bossSeen = true;
                    int health = boss.getState().hitCount;
                    assertTrue(health <= lastHealth, "boss must not respawn or regain health");
                    hits += lastHealth - health;
                    lastHealth = health;
                    if (boss.getState().defeated && defeatFrame < 0) defeatFrame = frame;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                    var replay = registry.capture();
                    assertEquals(forward.entries().keySet(), replay.entries().keySet());
                    for (String key : forward.entries().keySet()) {
                        var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                        assertTrue(differences.isEmpty(), "input " + frame + " cycle " + cycle
                                + " " + key + ": " + differences);
                    }
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertTrue(bossSeen);
            assertEquals(6, hits, "all six drill hits must come from the ordinary cold encounter");
            assertEquals(23797, defeatFrame);
            assertEquals(24349, loadFrame);
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(0, GameServices.camera().getMinY(), "Act2 releases the inherited arena min-Y");
            assertEquals(2357, session.player().getCentreX());
            assertEquals(1980, session.player().getCentreY());
            assertFalse(session.player().isObjectControlled());
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance.class).isEmpty());
            assertEquals(1, GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.LrzDeathEggBackgroundInstance.class).size());
            assertTrue(outgoingHistory > 10, "record actual outgoing live history");
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(rewind.earliestAvailableFrame() > outgoingHistory,
                    "seamless handoff retains frame numbering but retires outgoing Act1 history");
            System.out.println("LRZ wide cold Act1 clear: " + checked.size() + " two-cycle rewind windows");
        }
    }

}
