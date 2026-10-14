package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold 800px Sonic+Tails inputs; no position, clock, ring or boss-state seeds. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszWidePairedColdRouteCapture {
    @Test
    void coldFirstReplicaAndTransportReplayWithRetiredColumnDebris() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-first-replica-800.bk2"));
        assertEquals(9914, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off",
                null, null, null);
        // Arrival, bridge, traversal, column release/retirement, preliminary and final
        // arena bounds, the fight, escape, raised pad and receiving platform.
        Set<Integer> spots = Set.of(30, 120, 400, 1000, 1300, 1390, 1500, 2000,
                2500, 3000, 3500, 4000, 4500, 5000, 5500, 5900, 6040, 6160,
                6212, 6257, 6300, 6500, 6800, 7100, 7400, 7500, 7600, 7750,
                7900, 7951, 8050, 8150, 8500, 8751, 9000, 9312, 9350, 9450,
                9550, 9750);
        var checked = new HashSet<Integer>();
        boolean seen = false;
        int health = 8, hits = 0;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertInstanceOf(Sonic.class, session.player());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                for (var boss : GameServices.level().getObjectManager()
                        .activeObjectsOfType(SszGhzBossObjectInstance.class)) {
                    seen = true;
                    int next = boss.hitsRemainingForTest();
                    assertTrue(next <= health, "GHZ replica cannot regain health");
                    hits += health - next;
                    health = next;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore at " + frame + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {
                        session.step(movie.getFrame(frame + n)); drawing.draw(session);
                    }
                    same(forward, registry.capture(), "replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(seen);
            assertEquals(8, hits);
            assertEquals(0, health);
            assertEquals(10, GameServices.level().getCurrentZone());
            assertEquals(512, session.player().getCentreX());
            assertEquals(1420, session.player().getCentreY(), "first receiving platform");
            assertEquals(46, session.player().getRingCount());
            assertFalse(session.player().isObjectControlled(), "pad released ordinary movement");
            System.out.println("SSZ wide cold first replica: " + checked.size()
                    + " two-cycle rewind windows");
        }
    }


    @Test
    void coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline() throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-800.bk2"));
        var firstReplica = loader.loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-first-replica-800.bk2"));
        assertEquals(21014, movie.getFrameCount());
        assertEquals(9914, firstReplica.getFrameCount());
        for (int frame = 0; frame < firstReplica.getFrameCount(); frame++) {
            assertEquals(firstReplica.getFrame(frame), movie.getFrame(frame),
                    "preserve qualified wide first-replica input " + frame);
        }
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off",
                null, null, null);
        // The separate first-replica method owns its 40 early windows. These
        // later spots cover bar release, walkways, raised/receiving pads, the
        // oscillating and bouncy clouds, Y-wrap carrier, narrow column, ordinary
        // charge, lower loop, springs/zigzag, Mecha approach and the launch.
        var spots = Set.of(10500, 11000, 11640, 12100, 12630, 13344, 13600,
                14000, 14240, 14968, 15150, 15182, 15300, 15350, 15400,
                15610, 15702, 15900, 16060, 16330, 16380, 16520, 16800,
                16963, 18590, 19000, 19500, 20000, 20693);
        var checked = new HashSet<String>();
        var ghzHealth = new HashSet<Integer>();
        var mtzHealth = new HashSet<Integer>();
        var mechaHealth = new HashSet<Integer>();
        int reload = -1;
        int released = -1;
        int controlsReleased = -1;
        long outgoingHistory = 0;
        boolean isolatedHistory = false;
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            int previousLives = GameServices.gameState().getLives();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // All SSZ raw replay windows finish before this independently
                // recorded tail. A real full load must retire that live history.
                if (frame == 20743) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode());
                assertEquals(800, GameServices.camera().getWidth());
                assertInstanceOf(Sonic.class, session.player());
                assertFalse(session.player().getDead(), "wide cold Sonic death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                int lives = GameServices.gameState().getLives();
                assertTrue(lives >= previousLives, "no hidden P1 respawn at input " + frame);
                previousLives = lives;
                assertNativePair(session);
                var level = GameServices.level();
                var manager = level.getObjectManager();
                var pending = new HashSet<String>();
                if (spots.contains(frame)) pending.add("wide-route-" + frame);
                for (var boss : manager.activeObjectsOfType(SszGhzBossObjectInstance.class)) {
                    int health = boss.hitsRemainingForTest();
                    ghzHealth.add(health);
                    if (health == 8) pending.add("ghz-admission");
                    if (health == 0) pending.add("ghz-defeat");
                }
                for (var boss : manager.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance.class)) {
                    int health = boss.hitsRemainingForTest();
                    mtzHealth.add(health);
                    if (health == 8) pending.add("mtz-admission");
                    if (health == 7) pending.add("mtz-hit");
                    if (health == 0) pending.add("mtz-defeat");
                }
                for (var boss : manager.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class)) {
                    int health = boss.getCollisionProperty();
                    mechaHealth.add(health);
                    if (health == 8) pending.add("mecha-admission");
                    if (health == 4) pending.add("mecha-hit");
                    if (health == 1) pending.add("mecha-last-hit");
                    if (health == 0) pending.add("mecha-defeat");
                }
                for (var end : manager.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicActEndObjectInstance.class)) {
                    if (end.timerForTest() == 9) pending.add("results-gate");
                    if (end.resultsRequestedForTest() && !checked.contains("results-requested")) {
                        assertTrue(session.player().isObjectControlled(), "native results pose owner");
                        pending.add("results-requested");
                    }
                }
                for (var launch : manager.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.SszLaunchControllerObjectInstance.class)) {
                    pending.add("launch");
                    if (launch.routineForTest() == 12 && launch.counterForTest() > 8) {
                        pending.add("spiral");
                    }
                }
                var mode = SessionManager.getCurrentGameplayMode();
                var rewind = mode.getRewindController();
                if (frame >= 20743 && level.getCurrentZone() == 10 && rewind != null) {
                    outgoingHistory = Math.max(outgoingHistory, rewind.currentFrame());
                }
                if (level.getCurrentZone() == 11) {
                    assertEquals(0, level.getCurrentAct());
                    if (reload < 0) {
                        reload = frame;
                        assertTrue(outgoingHistory > 10, "actual outgoing SSZ history exists");
                    }
                    if (released < 0 && !level.hasPendingFreshLevelTransitionBoundary()
                            && !level.getTransitions().isTitleCardRequested()) {
                        released = frame;
                        assertSame(GameServices.sprites().getMainPlayable(), session.player());
                        assertSame(session.player(), GameServices.camera().getFocusedSprite());
                        assertFalse(session.player().isObjectControlled());
                        // loc_44A26 owns the native DEZ entrance run after assembly.
                        assertTrue(session.player().isControlLocked());
                        assertFalse(manager.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance.class).isEmpty());
                        assertEquals(48, session.player().getCentreX(), "native DEZ1 start");
                        assertEquals(2476, session.player().getCentreY(), "Sonic floor seating");
                        assertEquals(0, session.player().getRingCount(), "native fresh destination rings");
                    }
                    if (released >= 0 && !isolatedHistory && rewind != null) {
                        assertTrue(rewind.currentFrame() < outgoingHistory,
                                "the released DEZ timeline retires outgoing SSZ history");
                        isolatedHistory = true;
                    }
                    if (frame == 20870) pending.add("dez-intro");
                    if (released >= 0 && controlsReleased < 0 && !session.player().isControlLocked()
                            && manager.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance.class).isEmpty()) {
                        // loc_44A56 releases both native pad locks and retires its owner.
                        controlsReleased = frame;
                        assertFalse(session.player().isObjectControlled());
                        assertFalse(GameServices.sprites().getRegisteredSidekicks().getFirst().isControlLocked());
                        pending.add("dez-released");
                    }
                }
                pending.removeAll(checked);
                if (pending.isEmpty()) continue;
                assertFalse(level.hasPendingFreshLevelTransitionBoundary());
                assertFalse(mode.getFadeManager().hasPendingCompletion());
                var registry = mode.getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                GameplayInputBranchTool.requireRestorableCheckpoint(saved,
                        level.hasPendingFreshLevelTransitionBoundary());
                var savedLevel = level.getCurrentLevel();
                int horizon = 45;
                assertTrue(frame + horizon < movie.getFrameCount());
                if (level.getCurrentZone() == 10) assertTrue(frame + horizon < 20863,
                        "raw registry replay cannot cross the actual full load");
                for (int n = 1; n <= horizon; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                    assertSame(savedLevel, level.getCurrentLevel(), "same-world replay " + pending);
                    assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode());
                    assertFalse(session.player().getDead());
                    assertTrue(GameServices.gameState().getLives() >= lives);
                    assertNativePair(session);
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), pending + " restore " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= horizon; n++) {
                        session.step(movie.getFrame(frame + n)); drawing.draw(session);
                        assertSame(savedLevel, level.getCurrentLevel());
                        assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode());
                        assertFalse(session.player().getDead());
                        assertTrue(GameServices.gameState().getLives() >= lives);
                        assertNativePair(session);
                    }
                    same(forward, registry.capture(), pending + " replay " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                checked.addAll(pending);
            }
            var required = new HashSet<>(Set.of("ghz-admission", "ghz-defeat", "mtz-admission",
                    "mtz-hit", "mtz-defeat", "mecha-admission", "mecha-hit", "mecha-last-hit",
                    "mecha-defeat", "results-gate", "results-requested", "launch", "spiral", "dez-intro", "dez-released"));
            spots.forEach(frame -> required.add("wide-route-" + frame));
            assertEquals(required, checked);
            var allHealth = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
            assertEquals(allHealth, ghzHealth, "all first-replica native hits");
            assertEquals(allHealth, mtzHealth, "all second-replica native hits");
            assertEquals(allHealth, mechaHealth, "all final Mecha native hits");
            assertEquals(20863, reload, "actual DEZ load");
            assertEquals(20864, released, "ordinary destination assembly dispatch");
            assertTrue(isolatedHistory);
            assertTrue(controlsReleased > released, "native entrance releases after assembly");
            assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
            assertFalse(session.player().isObjectControlled());
            assertFalse(session.player().isControlLocked());
            assertFalse(session.player().getAir());
            assertTrue(session.player().getCentreX() > 48, "ordinary released DEZ movement");
            assertEquals(3, session.player().getRingCount(), "destination rings are earned after release");
            System.out.println("SSZ800 paired complete: " + checked.size()
                    + " semantic spots, two-cycle45 whole-world windows, DEZ load/release " + reload + "/" + released + "; ordinary control release " + controlsReleased);
        }
    }

    private static void assertNativePair(GameplayCaptureSession session) {
        assertSame(GameServices.sprites().getMainPlayable(), session.player());
        var sidekicks = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, sidekicks.size(), "one native Tails follower");
        assertInstanceOf(com.openggf.sprites.playable.Tails.class, sidekicks.getFirst());
        assertSame(sidekicks.getFirst(), GameServices.level().getObjectManager()
                .getObjectServices().playerQuery().nativeP2OrNull(), "native P2 slot owns the follower");
        // CPU Tails may recover offscreen under her native owner. This contract
        // asserts the roster, P1 survival/lives and complete captured P2 state.
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }
}
