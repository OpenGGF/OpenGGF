package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.MhzMinibossInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.GameLoopRewindTestAccess.owner;

/** Cold locked-on Knuckles routes; pad input drives the production GameLoop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzKnucklesColdRouteCapture {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Tag("exhaustive-audit")
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

    @Tag("exhaustive-audit")
    @Test void coldKnucklesCompletesActTwoAndIsolatesTheFbzTimeline() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz2-knuckles-cold-complete-320.bk2"));
        var actOne = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz1-knuckles-cold-complete-320.bk2"));
        assertEquals(66282, movie.getFrameCount());
        for (int frame = 0; frame < actOne.getFrameCount(); frame++) {
            assertEquals(actOne.getFrame(frame), movie.getFrame(frame),
                    "preserve qualified cold Act1 input " + frame);
            assertEquals(0, movie.getFrame(frame).p2InputMask());
        }
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Act1 has a separate 43-window contract. These windows cover the real
        // Act2 switch, vines/loops, walls, earned ring recovery, final pulley,
        // fresh ground jump/glide and the admitted end-boss chase.
        var spots = Set.of(30050, 32145, 34660, 41911, 42512, 54220,
                56200, 57500, 59574, 61196, 61830, 62200, 63690);
        var checked = new HashSet<String>();
        var health = new HashSet<Integer>();
        int reload = -1;
        long outgoingHistory = 0;
        RewindController outgoingController = null, incomingController = null;
        boolean isolatedHistory = false;
        boolean escapedAtOne = false;
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            int previousLives = GameServices.gameState().getLives();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 63600) GameServices.configuration().setSessionOverride(
                        SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                incomingController = observeIncomingHistory(session, outgoingController, incomingController);
                assertEquals(CharacterKey.KNUCKLES, session.player().characterKey());
                assertEquals(320, GameServices.camera().getWidth());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                assertFalse(session.player().getDead(), "cold Knuckles death at " + frame);
                assertFalse(session.player().isSuperSonic());
                int lives = GameServices.gameState().getLives();
                assertTrue(lives >= previousLives, "no hidden respawn at " + frame);
                previousLives = lives;
                var level = GameServices.level();
                var manager = level.getObjectManager();
                var pending = new HashSet<String>();
                if (spots.contains(frame)) pending.add("knuckles-route-" + frame);
                for (var boss : manager.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance.class)) {
                    if (boss.getCustomFlag(0x100) == 0) continue;
                    health.add(boss.getState().hitCount);
                    assertTrue(boss.getState().hitCount >= 1, "native MHZ terminal health is one");
                    if (boss.getState().hitCount == 9) pending.add("admission");
                    if (boss.getState().hitCount == 5) pending.add("chase-hit");
                    if (boss.getState().hitCount == 1 && (boss.getCustomFlag(0x38) & 0x80) != 0) {
                        // loc_760C4 has admitted the chase; a pending pre-arena
                        // touch can briefly publish HP1 before sub_76782 clamps it.
                        pending.add("terminal-flash");
                    }
                    if (boss.isDefeated()) {
                        escapedAtOne = true;
                        assertEquals(1, boss.getState().hitCount);
                        pending.add("escape-at-one");
                        if (GameServices.gameState().isEndOfLevelActive()) pending.add("capsule-results");
                    }
                }
                if (level.getCurrentZone() == 7 && level.getCurrentAct() == 1
                        && !manager.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.MhzShipSequenceControllerInstance.class).isEmpty()) {
                    pending.add("ship");
                    if (session.player().isObjectControlled()) pending.add("ship-carry");
                }
                if (!manager.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.MhzEndBossPaletteFadeController.class).isEmpty()) {
                    pending.add("weather-fade");
                }
                var rewind = owner(session.loop()).getRewindController();
                if (frame >= 63600 && level.getCurrentZone() == 7) {
                    if (rewind != null) outgoingController = rewind;
                    assertNotNull(outgoingController, "the enabled Knuckles route records before its exit");
                    outgoingHistory = Math.max(outgoingHistory, outgoingController.currentFrame());
                    if (rewind == null) assertRetiredHistory(session, outgoingController);
                } else if (level.getCurrentZone() == 4 && reload < 0) {
                    reload = frame;
                    assertTrue(outgoingHistory > 10, "record real outgoing live history");
                    assertNotNull(outgoingController);
                    assertRetiredHistory(session, outgoingController);
                }
                if (incomingController != null && !isolatedHistory) {
                    assertTrue(incomingController.currentFrame() < outgoingHistory,
                            "actual FBZ assembly retires the outgoing timeline");
                    isolatedHistory = true;
                }
                if (level.getCurrentZone() == 4 && level.getCurrentAct() == 0
                        && !level.hasPendingFreshLevelTransitionBoundary()
                        && !owner(session.loop()).getFadeManager().hasPendingCompletion()
                        && level.getFrameCounter() > 0) {
                    pending.add("fbz-released");
                }
                pending.removeAll(checked);
                if (pending.isEmpty()) continue;
                assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode());
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                var savedLevel = level.getCurrentLevel();
                int horizon = Math.min(pending.contains("weather-fade") ? 90 : 45,
                        movie.getFrameCount() - frame - 1);
                if (frame < 66064) horizon = Math.min(horizon, 66064 - frame - 1);
                assertTrue(horizon > 0, "same-world interval " + pending);
                for (int n = 1; n <= horizon; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                    incomingController = observeIncomingHistory(session, outgoingController, incomingController);
                    assertSame(savedLevel, level.getCurrentLevel(), "same level replay " + pending);
                    assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode());
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), pending + " restore " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= horizon; n++) {
                        session.step(movie.getFrame(frame + n));
                        drawing.draw(session);
                        incomingController = observeIncomingHistory(session, outgoingController, incomingController);
                    }
                    same(forward, registry.capture(), pending + " replay " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                checked.addAll(pending);
            }
            var required = new HashSet<>(Set.of("admission", "chase-hit", "terminal-flash",
                    "escape-at-one", "capsule-results", "ship", "ship-carry", "weather-fade", "fbz-released"));
            spots.forEach(frame -> required.add("knuckles-route-" + frame));
            assertEquals(required, checked);
            assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9), health);
            assertTrue(escapedAtOne);
            assertTrue(isolatedHistory);
            assertNotNull(incomingController, "the route reaches an admitted FBZ timeline");
            assertNotNull(owner(session.loop()).getRewindController());
            assertEquals(66064, reload);
            assertEquals(4, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
            assertFalse(session.player().isControlLocked());
            assertTrue(session.player().getCentreX() > 128, "ordinary released FBZ movement");
            System.out.println("MHZ Knuckles Act2: " + checked.size() + " two-cycle whole-world windows");
        }
    }

    private static RewindController observeIncomingHistory(GameplayCaptureSession session,
            RewindController outgoing, RewindController first) {
        var context = owner(session.loop());
        var level = context.getLevelManager();
        if (first == null && level.getCurrentZone() == 4 && level.getCurrentAct() == 0
                && !level.hasPendingFreshLevelTransitionBoundary() && !level.isLevelInactiveForTransition()
                && !context.getFadeManager().hasPendingCompletion()) {
            var installed = context.getRewindController();
            if (installed != null) {
                assertNotNull(outgoing);
                assertNotSame(outgoing, installed);
                assertEquals(0, installed.earliestAvailableFrame());
                assertTrue(installed.currentFrame() <= 1, "the first admitted FBZ history starts at frame zero");
                return installed;
            }
        }
        return first;
    }

    private static void assertRetiredHistory(GameplayCaptureSession session, RewindController outgoing) {
        var context = owner(session.loop());
        assertNotSame(outgoing, context.getRewindController());
        int frame = outgoing.currentFrame();
        var level = context.getLevelManager();
        int worldFrame = level.getFrameCounter();
        var objects = level.getObjectManager();
        outgoing.step();
        outgoing.seekTo(0);
        assertFalse(outgoing.stepBackward(), "retired MHZ history rejects replay");
        assertEquals(frame, outgoing.currentFrame());
        assertEquals(worldFrame, level.getFrameCounter());
        assertSame(objects, level.getObjectManager());
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
