package com.openggf.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozColdRouteObservations;
import com.openggf.game.sonic3k.objects.SozEndBossInstance;
import com.openggf.game.sonic3k.objects.SozMinibossInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

/** Cold controller-only routes with rendered full-world replay, independent of optional video output. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozColdRouteCapture {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void pairedColdActCompletesWithTraversalReplayAndPlayableDestination(int act) throws Exception {
        coldActCompletesWithTraversalReplayAndPlayableDestination(act, true, "sonic");
    }

    @ParameterizedTest
    @CsvSource({"0,sonic", "1,sonic", "0,tails", "1,tails", "0,knuckles"})
    void soloColdActCompletesWithTraversalReplayAndPlayableDestination(int act, String main) throws Exception {
        coldActCompletesWithTraversalReplayAndPlayableDestination(act, false, main);
    }

    @Test
    void knucklesColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef() throws Exception {
        coldActCompletesWithTraversalReplayAndPlayableDestination(1, false, "knuckles");
    }

    @Test
    void knucklesWideColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef() throws Exception {
        coldActCompletesWithTraversalReplayAndPlayableDestination(1, false, "knuckles", 800);
    }

    private void coldActCompletesWithTraversalReplayAndPlayableDestination(int act, boolean paired, String main) throws Exception {
        coldActCompletesWithTraversalReplayAndPlayableDestination(act, paired, main, 320);
    }

    private void coldActCompletesWithTraversalReplayAndPlayableDestination(int act, boolean paired, String main,
            int width) throws Exception {
        String route = "src/test/resources/routes/s3k/soz" + (act + 1)
                + (paired ? "-cold-sonic-tails" : "-cold-" + main)
                + (width == 320 ? "" : "-" + width) + ".bk2";
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(route));
        var settings = new GameplayCaptureSession.Settings(width, main, paired ? "tails" : "", "off", null, null, null);
        var checked = new HashSet<Integer>();
        var semanticChecked = new HashSet<String>();
        boolean bossSeen = false, sinking = false, capsule = false, resultsSeen = false,
                resultsFinished = false, isolatedHistory = false;
        int health = 8, hits = 0, readyFrame = -1, bonusLoads = 0;
        boolean bonusRoute = act == 0 && main.equals("knuckles");
        boolean knucklesActTwo = act == 1 && main.equals("knuckles");
        // Cork release, light pull, rock coupling/door, upper switch, spike-edge
        // glide/wall break, final switch and arena drop on the fixed cold route.
        var knucklesPuzzleSpots = width == 800
                ? java.util.Set.of(26570, 27370, 27430, 28480, 28680,
                        28920, 29060, 29240, 29300, 30180, 30360)
                : java.util.Set.of(27290, 28090, 28150, 29200, 29400,
                        29575, 29720, 29855, 29900, 30850, 31020);
        long outgoingHistory = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, act, settings);
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
            if (knucklesActTwo) {
                assertInstanceOf(com.openggf.sprites.playable.Knuckles.class, session.player());
                assertFalse(com.openggf.game.CrossGameFeatureProvider.isActive());
                assertNotNull(session.player().getSpriteRenderer());
            }
            for (int frame = 0; frame < movie.getFrameCount() + 300; frame++) {
                if (act == 1 && capsule)
                    GameServices.configuration().setSessionOverride(
                            SonicConfiguration.LIVE_REWIND_ENABLED, true);
                // This fixed cold route enters and returns from a real bonus stage.
                // Enable the production recorder around those loads to verify that
                // neither outgoing timeline can be restored into the replacement world.
                if (bonusRoute && frame == 3100)
                    GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
                if (bonusRoute && frame == 3800)
                    GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
                var beforeLevel = GameServices.level().getCurrentLevel();
                var beforeHistory = SessionManager.getCurrentGameplayMode().getRewindController();
                long beforeHistoryFrame = beforeHistory == null ? 0 : beforeHistory.currentFrame();
                var pendingSpots = new HashSet<String>();
                var input = input(movie, frame);
                session.step(input);
                session.render();
                if (bonusRoute && frame >= 3100 && frame < 3800
                        && beforeLevel != GameServices.level().getCurrentLevel()) {
                    assertTrue(beforeHistoryFrame > 10, "outgoing bonus boundary history at " + frame);
                    var afterHistory = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertTrue(afterHistory == null || afterHistory.currentFrame() < beforeHistoryFrame,
                            "bonus boundary clears outgoing timeline at " + frame);
                    bonusLoads++;
                }
                assertFalse(session.player().getDead(), "cold death at " + frame);
                assertEquals(main, session.player().characterKey().persisted());
                var followers = GameServices.sprites().getRegisteredSidekicks();
                assertEquals(paired ? 1 : 0, followers.size());
                if (paired)
                    assertEquals("tails", followers.getFirst().characterKey().persisted());
                assertEquals(width, GameServices.camera().getWidth());
                var level = GameServices.level();
                if (level.getCurrentZone() == 8 && level.getCurrentAct() == act) {
                    var objects = level.getObjectManager();
                    for (var golem : objects.activeObjectsOfType(SozMinibossInstance.class)) {
                        bossSeen = true;
                        // sub_772F6 starts the positional defeat at y_pos >= $A10.
                        sinking |= (golem.getY() & 0xFFFF) >= 0xA10;
                    }
                    for (var boss : objects.activeObjectsOfType(SozEndBossInstance.class)) {
                        if (!bossSeen)
                            pendingSpots.add("boss-entry");
                        bossSeen = true;
                        int next = boss.getCollisionProperty();
                        if (next < health)
                            pendingSpots.add("boss-hp-" + next);
                        assertTrue(next <= health, "boss health cannot increase");
                        hits += health - next;
                        health = next;
                    }
                    boolean opened = SozColdRouteObservations.capsuleOpened();
                    if (opened && !capsule)
                        pendingSpots.add("capsule-open");
                    capsule |= opened;
                    boolean results = GameServices.gameState().isEndOfLevelActive();
                    if (results && !resultsSeen)
                        pendingSpots.add("results-start");
                    resultsFinished |= resultsSeen && !results;
                    resultsSeen |= results;
                    if (act == 1 && !resultsSeen) {
                        var events = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry())
                                             .orElseThrow()
                                             .events();
                        String mode = "background-" + events.backgroundRoutine();
                        if (!semanticChecked.contains(mode))
                            pendingSpots.add(mode);
                    }
                    if (act == 1 && capsule) {
                        var history = SessionManager.getCurrentGameplayMode().getRewindController();
                        if (history != null)
                            outgoingHistory = Math.max(outgoingHistory, history.currentFrame());
                    }
                }
                boolean destination = act == 0 ? level.getCurrentZone() == 8 && level.getCurrentAct() == 1
                                               : level.getCurrentZone() == 9 && level.getCurrentAct() == 0;
                if (destination && act == 1 && !isolatedHistory) {
                    assertTrue(outgoingHistory > 10, "outgoing history exists before LRZ");
                    var history = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertTrue(history == null || history.currentFrame() < outgoingHistory,
                            "LRZ resets the outgoing timeline at load");
                    isolatedHistory = true;
                }
                if (destination && !session.player().isControlLocked()
                        && !session.player().isObjectControlled()
                        && (act == 1
                                || !S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry())
                                        .orElseThrow()
                                        .events()
                                        .seamlessEntry())
                        && readyFrame < 0)
                    readyFrame = frame;
                // Fixed input observations cover the cold traversal and boss/results approach;
                // a window must never restore the outgoing registry across a level load.
                // Tails reaches Act2 before 17000 and LRZ before 29000; keep replay windows before each load.
                int lastSourceSpot = knucklesActTwo ? 35000 : bonusRoute ? 23000 : main.equals("tails") ? (act == 0 ? 16000 : 28000)
                        : act == 0 && paired ? 26000 : 31000;
                if ((frame >= 100 && frame <= lastSourceSpot && (frame == 100 || frame % 1000 == 0))
                        || (bonusRoute && (frame == 3100 || frame == 3400 || frame == 3500 || frame == 3700))
                        || (knucklesActTwo && knucklesPuzzleSpots.contains(frame))
                        || (readyFrame >= 0 && frame == readyFrame + 30)) {
                    replay(session, movie, frame);
                    checked.add(frame);
                    semanticChecked.addAll(pendingSpots);
                    pendingSpots.clear();
                }
                if (!pendingSpots.isEmpty()) {
                    replay(session, movie, frame);
                    semanticChecked.addAll(pendingSpots);
                }
                if (readyFrame >= 0 && frame >= readyFrame + 180)
                    break;
            }
            if (bonusRoute) assertEquals(2, bonusLoads, "real bonus entry and return loads");
            assertTrue(bossSeen, "cold route reaches its real boss");
            assertTrue(resultsSeen && resultsFinished, "actual results sequence finishes");
            if (act == 0)
                assertTrue(sinking, "golem reaches the native sand defeat height");
            else {
                assertEquals(8, hits);
                assertEquals(0, health);
                assertTrue(capsule);
            }
            assertEquals(act == 0 ? 8 : 9, GameServices.level().getCurrentZone());
            assertEquals(act == 0 ? 1 : 0, GameServices.level().getCurrentAct());
            assertTrue(readyFrame >= 0, "destination releases both control owners");
            assertEquals(
                    knucklesActTwo ? 37 + knucklesPuzzleSpots.size() : bonusRoute ? 29 : main.equals("tails") ? (act == 0 ? 18 : 30) : act == 0 && paired ? 28 : 33, checked.size(), "all traversal and destination replay windows ran");
            if (act == 1) {
                assertTrue(semanticChecked.contains("boss-entry"));
                for (int hp = 0; hp < 8; hp++) assertTrue(semanticChecked.contains("boss-hp-" + hp));
                assertTrue(semanticChecked.contains("capsule-open"));
                assertTrue(semanticChecked.contains("results-start"));
                assertTrue(isolatedHistory, "observed the actual LRZ load and outgoing-history reset");
                assertNotNull(SessionManager.getCurrentGameplayMode().getRewindController());
            }
            System.out.println("SOZ" + (act + 1) + (paired ? " cold pair: ready=" : " cold " + main + " solo: ready=") + readyFrame
                    + ", width=" + width + ", replay windows=" + checked.size() + ", semantic=" + semanticChecked);
        }
    }
    private static Bk2FrameInput input(com.openggf.debug.playback.Bk2Movie movie, int frame) {
        return frame < movie.getFrameCount() ? movie.getFrame(frame)
                                             : new Bk2FrameInput(frame, 0, 0, false, "");
    }
    private static void replay(
            GameplayCaptureSession session, com.openggf.debug.playback.Bk2Movie movie, int frame) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var level = GameServices.level().getCurrentLevel();
        var saved = registry.capture();
        for (int n = 1; n <= 45; n++) {
            session.step(input(movie, frame + n));
            session.render();
        }
        assertSame(level, GameServices.level().getCurrentLevel(), "replay window crosses a load at " + frame);
        var forward = registry.capture();
        registry.restore(saved);
        same(saved, registry.capture(), "restore " + frame);
        session.restoreInputHistory(input(movie, frame));
        for (int n = 1; n <= 45; n++) {
            session.step(input(movie, frame + n));
            session.render();
        }
        same(forward, registry.capture(), "replay " + frame);
        registry.restore(saved);
        session.restoreInputHistory(input(movie, frame));
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (var key : expected.entries().keySet()) {
            var difference = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(difference.isEmpty(), where + " " + key + ": " + difference);
        }
    }
}
