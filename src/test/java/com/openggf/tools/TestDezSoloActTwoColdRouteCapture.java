package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.DezEndBossInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.GameLoopRewindTestAccess.owner;

/** Solo Act2 production traversal and boss, preserving the complete cold DEZ1 prefix. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezSoloActTwoColdRouteCapture {
    @Test
    void coldSonicAloneClearsActTwoAndLoadsFinalStage() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez2-sonic-solo-incoming-clear-320.bk2"));
        assertEquals(53853, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        // The separate Act1 test covers its prefix. These windows cover incoming control,
        // gravity tubes, conveyor/staircase, energy bridges, pressure pads, transporters,
        // springs, curves, carrier/launch chain, shaft, tilting bridge, hubs and boss columns.
        var spots = Set.of(23532, 24000, 24380, 24550, 24882, 24980, 25396,
                25600, 25800, 26600, 26850, 27050, 27120, 27300, 27659,
                27720, 27852, 27898, 27929, 28049, 28059, 28300, 28849,
                28910, 29200, 29696, 30000, 30431, 30500, 30900, 31200,
                31500, 31744, 31774, 31810, 32000, 32440, 32600, 33000,
                33200, 33400, 33700, 34000, 34300, 35830, 36000, 36200,
                36500, 36610, 36700, 37100, 37355, 37550, 38210, 38300,
                38550, 38630, 38651, 38800, 39591, 39900, 40100, 40400,
                40600, 40961, 41180, 41400, 41800, 42060, 43060, 43100,
                43325, 43553, 43800, 44100, 44483, 44550, 44724, 44900,
                45300, 45600, 45900, 46300, 46600, 46900, 47200, 47500,
                47800, 48263, 48500, 49000, 49660, 50680, 50980, 51340,
                51700, 52000, 53020, 53380, 53500, 53600, 53761, 53801);
        var checked = new HashSet<Integer>();
        var health = DezEndBossInstance.class.getDeclaredMethod("healthForTest");
        health.setAccessible(true);
        int lastHealth = 8, hits = 0, loadFrame = -1;
        boolean bossSeen = false;
        long outgoingFrame = 0;
        RewindController outgoingHistory = null;
        RewindController incomingHistory = null;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 53650) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                assertInstanceOf(Sonic.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (GameServices.level().getCurrentZone() == 11) {
                    if (frame >= 53650) {
                        var installed = owner(session.loop()).getRewindController();
                        if (installed != null) outgoingHistory = installed;
                        assertNotNull(outgoingHistory, "the enabled DEZ route records before its exit");
                        outgoingFrame = Math.max(outgoingFrame, outgoingHistory.currentFrame());
                        if (installed == null) assertRetiredHistory(session, outgoingHistory);
                    }
                } else if (loadFrame < 0) {
                    loadFrame = frame;
                    assertNotNull(outgoingHistory, "the completed DEZ encounter has an outgoing timeline");
                    assertRetiredHistory(session, outgoingHistory);
                }
                if (loadFrame >= 0 && incomingHistory == null) {
                    incomingHistory = owner(session.loop()).getRewindController();
                    if (incomingHistory != null) {
                        assertNotSame(outgoingHistory, incomingHistory);
                        assertEquals(0, incomingHistory.earliestAvailableFrame());
                        assertTrue(incomingHistory.currentFrame() <= 1,
                                "the first installed destination history starts at frame zero");
                    }
                }
                var boss = GameServices.level().getObjectManager().activeObjectsOfType(DezEndBossInstance.class)
                        .stream().findFirst().orElse(null);
                if (boss != null) {
                    bossSeen = true;
                    int currentHealth = (int) health.invoke(boss);
                    assertTrue(currentHealth <= lastHealth, "boss must not respawn or regain health");
                    hits += lastHealth - currentHealth;
                    lastHealth = currentHealth;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(bossSeen);
            assertEquals(8, hits, "all eight hits must come from the production encounter");
            assertEquals(53732, loadFrame);
            assertEquals(23, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertTrue(outgoingFrame > 10);
            // Unlike DEZ1's seamless handoff, StartNewLevel $1700 resets the frame origin.
            assertNotNull(incomingHistory, "the route reaches an installed destination timeline");
            var rewind = owner(session.loop()).getRewindController();
            assertNotNull(rewind, "ready destination gameplay requires live history");
            assertEquals(0, rewind.earliestAvailableFrame());
            rewind.seekTo(0);
            assertEquals(0, rewind.currentFrame());
            assertEquals(23, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
        }
    }

    @Test
    void coldWideSonicAloneClearsActTwoAndLoadsFinalStage() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez2-sonic-solo-incoming-clear-800.bk2"));
        assertEquals(53575, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "", "off", null, null, null);
        // Independent 800px cold route: periodic whole-world windows plus bridge,
        // gravity, launcher, tilting-platform, hub, boss and incoming-load boundaries.
        var spots = Set.of(
                20874, 20900, 21300, 21700, 22100, 22500, 22900, 23300, 23700,
                24100, 24500, 24900, 25300, 25700, 26100, 26500, 26900, 27300,
                27447, 27700, 27712, 27860, 27920, 27970, 28100, 28120, 28320,
                28500, 28900, 29300, 29700, 30100, 30449, 30500, 30900, 31300,
                31700, 32100, 32450, 32500, 32900, 33050, 33300, 33700, 33780,
                34100, 34500, 34750, 34900, 35000, 35300, 35700, 36100, 36500,
                36900, 37300, 37700, 38100, 38500, 38750, 38900, 39300, 39700,
                40087, 40100, 40200, 40330, 40500, 40800, 40900, 41300, 41700,
                42100, 42500, 42900, 43150, 43300, 43700, 44100, 44500, 44900,
                45000, 45300, 45700, 46100, 46500, 46900, 47000, 47300, 47700,
                48100, 48500, 48900, 49000, 49300, 49700, 50000, 50100, 50500,
                50900, 51000, 51300, 51700, 52000, 52100, 52500, 52900, 53000,
                53300, 53370);
        var checked = new HashSet<Integer>();
        var health = DezEndBossInstance.class.getDeclaredMethod("healthForTest");
        health.setAccessible(true);
        int lastHealth = 8, hits = 0, loadFrame = -1;
        boolean bossSeen = false;
        long outgoingFrame = 0;
        RewindController outgoingHistory = null;
        RewindController incomingHistory = null;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 53200) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertEquals(800, GameServices.camera().getWidth());
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                assertInstanceOf(Sonic.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (GameServices.level().getCurrentZone() == 11) {
                    if (frame >= 53200) {
                        var installed = owner(session.loop()).getRewindController();
                        if (installed != null) outgoingHistory = installed;
                        assertNotNull(outgoingHistory, "the enabled DEZ route records before its exit");
                        outgoingFrame = Math.max(outgoingFrame, outgoingHistory.currentFrame());
                        if (installed == null) assertRetiredHistory(session, outgoingHistory);
                    }
                } else if (loadFrame < 0) {
                    loadFrame = frame;
                    assertNotNull(outgoingHistory, "the completed DEZ encounter has an outgoing timeline");
                    assertRetiredHistory(session, outgoingHistory);
                }
                if (loadFrame >= 0 && incomingHistory == null) {
                    incomingHistory = owner(session.loop()).getRewindController();
                    if (incomingHistory != null) {
                        assertNotSame(outgoingHistory, incomingHistory);
                        assertEquals(0, incomingHistory.earliestAvailableFrame());
                        assertTrue(incomingHistory.currentFrame() <= 1,
                                "the first installed destination history starts at frame zero");
                    }
                }
                var boss = GameServices.level().getObjectManager().activeObjectsOfType(DezEndBossInstance.class)
                        .stream().findFirst().orElse(null);
                if (boss != null) {
                    bossSeen = true;
                    int currentHealth = (int) health.invoke(boss);
                    assertTrue(currentHealth <= lastHealth, "boss must not respawn or regain health");
                    hits += lastHealth - currentHealth;
                    lastHealth = currentHealth;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore at " + frame + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                    same(forward, registry.capture(), "replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(bossSeen);
            assertEquals(8, hits, "all eight hits must come from the production encounter");
            assertEquals(53454, loadFrame);
            assertEquals(23, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertTrue(outgoingFrame > 10);
            // Unlike DEZ1's seamless handoff, StartNewLevel $1700 resets the frame origin.
            assertNotNull(incomingHistory, "the route reaches an installed destination timeline");
            var rewind = owner(session.loop()).getRewindController();
            assertNotNull(rewind, "ready destination gameplay requires live history");
            assertEquals(0, rewind.earliestAvailableFrame());
            rewind.seekTo(0);
            assertEquals(0, rewind.currentFrame());
            assertEquals(23, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
        }
    }

    @Test
    void coldTailsAloneClearsActTwoAndLoadsFinalStage() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez2-tails-solo-incoming-clear-320.bk2"));
        assertEquals(62599, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        // Keep the short pending-pad test independent; extend coverage through the
        // return launcher, upper route, all boss cycles and the real final-stage load.
        var spots = Set.of(29520, 35351, 36388, 37593, 40277, 45994, 46395, 46480,
                46581, 46615, 46680, 47043, 47156, 47438, 47800, 48000,
                48550, 49000, 49141, 49500, 49700, 50000, 51000, 51232,
                51450, 51800, 52400, 53000, 53140, 53300, 53360, 53520,
                53800, 53990, 54200, 54400, 54600, 54860, 55200, 55600,
                55890, 56400, 57000, 57600, 58200, 58800, 59400, 60000,
                60600, 61200, 61800, 62100, 62200, 62300, 62501, 62541);
        var checked = new HashSet<Integer>();
        var health = DezEndBossInstance.class.getDeclaredMethod("healthForTest");
        health.setAccessible(true);
        int lastHealth = 8, hits = 0, loadFrame = -1;
        boolean bossSeen = false;
        long outgoingFrame = 0;
        RewindController outgoingHistory = null;
        RewindController incomingHistory = null;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 62360) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                assertInstanceOf(com.openggf.sprites.playable.Tails.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (GameServices.level().getCurrentZone() == 11) {
                    if (frame >= 62360) {
                        var installed = owner(session.loop()).getRewindController();
                        if (installed != null) outgoingHistory = installed;
                        assertNotNull(outgoingHistory, "the enabled DEZ route records before its exit");
                        outgoingFrame = Math.max(outgoingFrame, outgoingHistory.currentFrame());
                        if (installed == null) assertRetiredHistory(session, outgoingHistory);
                    }
                } else if (loadFrame < 0) {
                    loadFrame = frame;
                    assertNotNull(outgoingHistory, "the completed DEZ encounter has an outgoing timeline");
                    assertRetiredHistory(session, outgoingHistory);
                }
                if (loadFrame >= 0 && incomingHistory == null) {
                    incomingHistory = owner(session.loop()).getRewindController();
                    if (incomingHistory != null) {
                        assertNotSame(outgoingHistory, incomingHistory);
                        assertEquals(0, incomingHistory.earliestAvailableFrame());
                        assertTrue(incomingHistory.currentFrame() <= 1,
                                "the first installed destination history starts at frame zero");
                    }
                }
                var boss = GameServices.level().getObjectManager().activeObjectsOfType(DezEndBossInstance.class)
                        .stream().findFirst().orElse(null);
                if (boss != null) {
                    bossSeen = true;
                    int currentHealth = (int) health.invoke(boss);
                    assertTrue(currentHealth <= lastHealth, "boss must not respawn or regain health");
                    hits += lastHealth - currentHealth;
                    lastHealth = currentHealth;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(bossSeen);
            assertEquals(8, hits, "all eight hits must come from the production encounter");
            assertEquals(62478, loadFrame);
            assertEquals(23, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertTrue(outgoingFrame > 10);
            // Unlike DEZ1's seamless handoff, StartNewLevel $1700 resets the frame origin.
            assertNotNull(incomingHistory, "the route reaches an installed destination timeline");
            var rewind = owner(session.loop()).getRewindController();
            assertNotNull(rewind, "ready destination gameplay requires live history");
            assertEquals(0, rewind.earliestAvailableFrame());
            rewind.seekTo(0);
            assertEquals(0, rewind.currentFrame());
            assertEquals(23, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
        }
    }

    @Test
    void coldTailsLowerGravityPadPreservesPendingContactAcrossRewind() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez2-tails-solo-lower-gravity-pad-320.bk2"));
        assertEquals(47140, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        var spots = Set.of(29520, 35351, 36388, 37593, 40277, 45994,
                46395, 46480, 46581, 46615, 46680, 47043);
        var checked = new HashSet<Integer>();
        boolean bridgeReached = false;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at " + frame);
                assertInstanceOf(com.openggf.sprites.playable.Tails.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (frame == 46581) {
                    assertEquals(8010, session.player().getCentreX());
                    assertEquals(2136, session.player().getCentreY());
                    assertTrue(GameServices.gameState().isReverseGravityActive());
                }
                if (frame == 46681) assertFalse(GameServices.gameState().isReverseGravityActive(),
                        "jumping off and returning presses the rearmed ceiling pad through production contact");
                if (frame > 46681 && session.player().getCentreX() >= 8640
                        && session.player().getCentreY() >= 2440 && !session.player().getAir()) bridgeReached = true;
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Tails restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                same(forward, registry.capture(), "Tails replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(bridgeReached);
            assertEquals(11, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
        }
    }

    private static void assertRetiredHistory(GameplayCaptureSession session, RewindController outgoing) {
        var context = owner(session.loop());
        assertNotSame(outgoing, context.getRewindController(), "the production exit retires DEZ history");
        int frame = outgoing.currentFrame();
        int worldFrame = context.getLevelManager().getFrameCounter();
        var objects = context.getLevelManager().getObjectManager();
        outgoing.step();
        outgoing.seekTo(0);
        assertFalse(outgoing.stepBackward(), "retired DEZ history rejects replay");
        assertEquals(frame, outgoing.currentFrame());
        assertEquals(worldFrame, context.getLevelManager().getFrameCounter());
        assertSame(objects, context.getLevelManager().getObjectManager());
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }
}
