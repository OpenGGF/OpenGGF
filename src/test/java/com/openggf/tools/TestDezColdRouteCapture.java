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

/** Cold DEZ1 traversal, miniboss and production Act 2 handover. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezColdRouteCapture {
    @Test void coldUpperRouteTraversesLiftsConveyorsAndTimedBridgesWithRewind() throws Exception {
        runColdRoute("upper");
    }

    @Test void coldRouteClearsAllTurbinePanelsAndOpensDoorWhileObjectControlled() throws Exception {
        runColdRoute("turbine");
    }

    @Test void coldCompleteRouteDefeatsBothMinibossPhasesAndLoadsActTwo() throws Exception {
        runColdRoute("complete");
    }

    private void runColdRoute(String route) throws Exception {
        boolean complete = route.equals("complete");
        boolean turbine = !route.equals("upper");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez1-sonic-tails-cold-"
                        + route + "-320.bk2"));
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        // Intro/bridge, lift catches/releases, tube, conveyor lift, timed bridge,
        // spring ascent and the upper moving platform. Every spot restores the full registry.
        var spots = new java.util.HashSet<>(Set.of(650, 1260, 1700, 2100, 2182, 2400, 2500, 2640, 2700,
                3050, 3150, 3240, 3750, 4110, 4300, 4370, 4450, 4650, 4840, 4950, 5200));
        if (turbine) spots.addAll(Set.of(5540, 5620, 5800, 5960, 6100, 6330, 6410,
                6520, 6760, 6890, 7290, 7460, 7750, 7840, 8100, 8260, 8400, 8470, 8540));
        if (complete) {
            // Earlier traversal spots have independent, shorter tests above.
            spots.clear();
            spots.addAll(Set.of(8800, 8900, 9350, 9490, 9650, 9930, 10070, 10500,
                    11080, 11260, 11420, 11570, 12190, 12320, 12470, 12640,
                    12740, 13100, 13270, 13860, 13950, 14100));
        }
        var bossHits = com.openggf.game.sonic3k.objects.DezMinibossInstance.class
                .getSuperclass().getDeclaredField("collisionProperty");
        bossHits.setAccessible(true);
        int completedBossPhases = 0;
        boolean eightHits = false;
        long largestOutgoingRewindFrame = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Keep live history near the real load boundary, after snapshot spots.
                if (complete && frame == 14150) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                session.render();
                if (complete && frame >= 14150 && GameServices.level().getCurrentAct() == 0) {
                    var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (rewind != null) largestOutgoingRewindFrame = Math.max(largestOutgoingRewindFrame,
                            rewind.currentFrame());
                }
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (complete) {
                    var boss = GameServices.level().getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.DezMinibossInstance.class)
                            .stream().findFirst().orElse(null);
                    boolean nowEight = boss != null && bossHits.getInt(boss) == 8;
                    if (nowEight && !eightHits) completedBossPhases++;
                    eightHits = nowEight;
                }
                if (frame == 5300) {
                    assertEquals(9589, session.player().getCentreX());
                    assertEquals(640, session.player().getCentreY());
                    assertEquals(32, session.player().getRingCount());
                    assertTrue(session.player().isOnObject(), "upper moving pad carries the player");
                }
                if (turbine && frame == 8460) {
                    assertEquals(0x3F, com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                            .currentDez(GameServices.zoneRuntimeRegistry()).orElseThrow().panelBits(),
                            "ordinary steering must press every panel before the exit");
                }
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
            assertEquals(complete ? 1 : 0, GameServices.level().getCurrentAct());
            if (complete) {
                assertEquals(2, completedBossPhases, "both eight-hit phases must be cleared");
                assertTrue(largestOutgoingRewindFrame > 10, "outgoing history must have been recorded");
                var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
                for (int n = 0; n < 30; n++) { session.step(neutral); session.render(); }
                var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                assertNotNull(rewind);
                // Seamless loads retain the logical frame counter, but re-root
                // the oldest seekable snapshot after the outgoing act's last frame.
                assertTrue(rewind.earliestAvailableFrame() > largestOutgoingRewindFrame,
                        "Act 2 must not retain the outgoing level's rewind history");
            }
            if (turbine && !complete) {
                assertEquals(10763, session.player().getCentreX());
                assertEquals(2096, session.player().getCentreY());
                assertFalse(session.player().isObjectControlled(), "turbine releases movement past its corridor");
                assertTrue(session.player().getCentreX() > 10640 + 0x1B,
                        "sub_30F58 must open the actual door while turbine control is still positive");
            }
        }
    }
}
