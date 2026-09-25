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

    @Test void coldIncomingActTwoTraversesGravityTubesConveyorAndStaircaseWithRewind() throws Exception {
        runColdRoute("lower");
    }

    @Test void coldIncomingActTwoClimbsEnergyBridgesAndTogglesGravityWithRewind() throws Exception {
        runColdRoute("middle");
    }

    @Test void coldIncomingActTwoTraversesBothTransportersWithRewind() throws Exception {
        runColdRoute("transporters");
    }

    @Test void coldIncomingActTwoCompletesUpperGravityRouteCarrierAndCountdownLaunchWithRewind() throws Exception {
        runColdRoute("roof");
    }

    @Test void coldIncomingActTwoDescendsInvertedSpringShaftWithRewind() throws Exception {
        runColdRoute("shaft");
    }

    @Test void coldIncomingActTwoCrossesTiltingBridgeWithoutAShieldWithRewind() throws Exception {
        runColdRoute("tilt");
    }

    @Test void coldIncomingActTwoTraversesUpperTransportChainsAndEastHubWithRewind() throws Exception {
        runColdRoute("chain");
    }

    @Test void coldIncomingActTwoDefeatsGravityBossAndLoadsFinalStageWithoutTransformation() throws Exception {
        runColdRoute("clear");
    }

    private void runColdRoute(String route) throws Exception {
        boolean complete = route.equals("complete");
        boolean clear = route.equals("clear");
        boolean transition = complete || clear;
        int historyStart = clear ? 40206 : 14150;
        boolean chain = route.equals("chain");
        boolean tilt = route.equals("tilt");
        boolean shaft = route.equals("shaft");
        boolean roof = route.equals("roof");
        boolean transporters = route.equals("transporters");
        boolean middle = route.equals("middle");
        boolean lower = route.equals("lower") || middle || transporters || roof || shaft || tilt || chain || clear;
        boolean turbine = !route.equals("upper");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/" + (lower ? "dez2-sonic-tails-incoming-" + route
                        : "dez1-sonic-tails-cold-" + route) + "-320.bk2"));
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
        if (lower) {
            // Independent earlier tests cover DEZ1. These spots exercise incoming
            // release, gravity tube entry/exit, hub launch, moving belt, staircase,
            // the second tube's polarity release and the lower corridor.
            spots.clear();
            spots.addAll(Set.of(15190, 15300, 16070, 16110, 16190, 16210,
                    16580, 16750, 16940, 17050, 17240, 17340, 17440, 17560,
                    17640, 17680, 17820));
        }
        if (middle) {
            // The shorter lower route owns the earlier Act2 spots. Follow the
            // timed energy bridges, pressure-pad gravity toggle and corridor ride.
            spots.clear();
            spots.addAll(Set.of(17930, 17980, 18090, 18135, 18175, 18230,
                    18305, 18330, 18360, 18470, 18540, 18700, 18810, 18850));
        }
        if (transporters) {
            // The preceding route owns the bridge and switch checks. Cover both
            // transporter waits, travel, polarity release and the intervening lift.
            spots.clear();
            spots.addAll(Set.of(18950, 19020, 19040, 19100, 19140, 19165,
                    19250, 19380, 19450, 19520, 19565, 19620, 19645, 19730));
        }
        if (roof) {
            // Switch/tube ascent, inverted spindash, gravity swap, carrier rise,
            // carrier release, launcher capture/countdown/travel and conveyor descent.
            spots.clear();
            spots.addAll(Set.of(19800, 19930, 20100, 20300, 20760, 20785,
                    20830, 20920, 20970, 21005, 21170, 21230, 21280, 21330,
                    21410, 21550, 21570, 21800, 22020, 22070, 22190, 22230,
                    22260, 22430, 22630, 22750, 22910, 23120));
        }
        if (shaft) {
            // Curved-wall departure, both inverted spring contacts, corridor
            // spring avoidance and the first ceiling step; earlier tests own the prefix.
            spots.clear();
            spots.addAll(Set.of(23172, 23332, 23422, 23462, 23482, 23497,
                    23537, 23552, 23567, 23607, 23637, 23662, 23792, 23842));
        }
        if (tilt) {
            // Second spring shaft, pressure-pad toggle, staircase deployment,
            // bridge balance/crossing, unshielded exit jumps and following lift.
            spots.clear();
            spots.addAll(Set.of(23952, 24022, 24072, 24132, 24182, 24242,
                    24282, 24312, 24382, 24432, 24482, 24542, 24572, 24622,
                    24682, 24717, 24762, 24797, 24837, 24862, 24897, 24932,
                    25012, 25082, 25142, 25202));
        }
        if (chain) {
            // Spring avoidance, vertical tube, west-facing launcher/transport,
            // eastbound corridor, second transport and hub direction/re-capture.
            spots.clear();
            spots.addAll(Set.of(25222, 25272, 25362, 25422, 25482, 25532,
                    25582, 25772, 25822, 25882, 26012, 26067, 26182, 26282,
                    26332, 26412, 26472, 26597, 26652, 26752, 26827, 26932,
                    27092, 27132, 27202, 27302, 27402, 27502, 27572, 27622,
                    28342, 28362));
        }
        if (clear) {
            // Return transports, final traversal, column entries/releases,
            // all eight enemy-published boss hits, breakup and actual exit load.
            spots.clear();
            spots.addAll(Set.of(28417, 28482, 28572, 28647, 28712, 28772,
                    28822, 28972, 29012, 29112, 29232, 29362, 29502, 29592,
                    29672, 29732, 29812, 29872, 29982, 30032, 30092, 30142,
                    30222, 30262, 30372, 30422, 30512, 30562, 30612, 30682,
                    30772, 30852, 30952, 31032, 31122, 31192, 31292, 31392,
                    31472, 31582, 31652, 31832, 31952, 32152, 32652, 33152,
                    34152, 35402, 35752, 36112, 36412, 37792, 38452, 39526,
                    39876, 39926, 40006, 40106, 40166));
        }
        var endBossHealth = com.openggf.game.sonic3k.objects.DezEndBossInstance.class
                .getDeclaredMethod("healthForTest");
        endBossHealth.setAccessible(true);
        int lastEndBossHealth = 8, endBossHits = 0;
        boolean endBossSeen = false;
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
                if (transition && frame == historyStart) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                session.render();
                if (transition && frame >= historyStart && GameServices.level().getCurrentZone() == 11
                        && GameServices.level().getCurrentAct() == (clear ? 1 : 0)) {
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
                if (clear) {
                    assertFalse(session.player().isSuperSonic(), "cold clear uses ordinary Sonic");
                    var boss = GameServices.level().getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.DezEndBossInstance.class)
                            .stream().findFirst().orElse(null);
                    if (boss != null) {
                        endBossSeen = true;
                        int health = (int) endBossHealth.invoke(boss);
                        assertTrue(health <= lastEndBossHealth, "boss must not respawn or regain health");
                        endBossHits += lastEndBossHealth - health;
                        lastEndBossHealth = health;
                    }
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
                if (transporters && (frame == 19050 || frame == 19500)) {
                    assertTrue(session.player().isObjectControlled(), "transporter holds its rider");
                    assertEquals(frame == 19050 ? 5456 : 5968, session.player().getCentreX());
                }
                if (roof && (frame == 21530 || frame == 22110)) {
                    assertTrue(session.player().isObjectControlled(), "carrier/launcher holds its rider");
                    if (frame == 22110) assertEquals(7424, session.player().getCentreX());
                }
                if (tilt && frame >= 24567 && frame <= 24932) {
                    assertFalse(session.player().hasShield(), "bridge crossing must not rely on a shield jump");
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
            assertEquals(clear ? 23 : 11, GameServices.level().getCurrentZone());
            assertEquals(clear ? 0 : complete || lower ? 1 : 0, GameServices.level().getCurrentAct());
            if (lower) {
                assertEquals(clear ? 96 : chain ? 12992 : tilt ? 9525 : shaft ? 8211 : roof ? 8718 : transporters ? 6709 : middle ? 4853 : 3196, session.player().getCentreX());
                assertEquals(clear ? 112 : chain ? 2112 : tilt ? 2156 : shaft ? 1683 : roof ? 1132 : transporters ? 1395 : middle ? 2371 : 2476, session.player().getCentreY());
                assertEquals(clear ? 0 : chain ? 4 : tilt ? 7 : shaft ? 3 : roof ? 0 : transporters ? 15 : middle ? 14 : 7, session.player().getRingCount());
                assertEquals(chain, session.player().isObjectControlled(),
                        "chain ends captured in the hub; other routes finish with movement released");
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
            }
            if (clear) {
                assertTrue(endBossSeen, "gravity boss must be encountered");
                assertEquals(8, endBossHits, "all eight enemy-published hits must be consumed");
            }
            if (transition) {
                if (complete) assertEquals(2, completedBossPhases, "both eight-hit phases must be cleared");
                assertTrue(largestOutgoingRewindFrame > 10, "outgoing history must have been recorded");
                var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
                for (int n = 0; n < 30; n++) { session.step(neutral); session.render(); }
                var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                assertNotNull(rewind);
                if (clear) {
                    // A full LEVEL_LOAD resets controller and input numbering to
                    // zero; unlike the seamless DEZ1 handoff, comparing absolute
                    // frame numbers would reject a correctly isolated timeline.
                    assertEquals(0, rewind.earliestAvailableFrame());
                    assertTrue(rewind.currentFrame() < largestOutgoingRewindFrame,
                            "full load starts a fresh frame origin");
                    rewind.seekTo(0);
                    assertEquals(0, rewind.currentFrame());
                    assertEquals(23, GameServices.level().getCurrentZone(),
                            "earliest snapshot belongs to the final stage, not the outgoing boss");
                    assertEquals(0, GameServices.level().getCurrentAct());
                } else {
                    // Seamless loads retain numbering and re-root at the boundary.
                    assertTrue(rewind.earliestAvailableFrame() > largestOutgoingRewindFrame,
                            "destination must not retain the outgoing level's rewind history");
                }
            }
            if (turbine && !complete && !lower) {
                assertEquals(10763, session.player().getCentreX());
                assertEquals(2096, session.player().getCentreY());
                assertFalse(session.player().isObjectControlled(), "turbine releases movement past its corridor");
                assertTrue(session.player().getCentreX() > 10640 + 0x1B,
                        "sub_30F58 must open the actual door while turbine control is still positive");
            }
        }
    }
}
