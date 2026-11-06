package com.openggf.tools;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SSZHPZTeleporterObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.FadeManagerSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** R35: independently reached MTZ/receiver route; old first/complete oracles remain separate. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszWidePairedSecondReplicaColdRouteCapture {
    private static final String ROUTE = "src/test/resources/routes/s3k/"
            + "ssz1-sonic-tails-cold-second-replica-800";
    private static final int HORIZON = 45;
    private static final Set<Integer> ALL_HEALTH = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

    @TempDir Path temporary;

    @Tag("exhaustive-audit")
    @Test
    void coldSecondReplicaAndReceiverReplayEveryRegistryKey() throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(Path.of(ROUTE + ".bk2"));
        assertEquals(17546, movie.getFrameCount());
        // Production author/loader proof precedes boot, including both physical pads and edges.
        Path authored = temporary.resolve("second-replica.bk2");
        InputLogAuthorTool.author(Files.readString(Path.of(ROUTE + ".script")), authored, "s3k");
        var roundtrip = loader.loadMovieOrInputLog(authored);
        assertEquals(movie.getFrameCount(), roundtrip.getFrameCount());
        var first = loader.loadMovieOrInputLog(Path.of("src/test/resources/routes/s3k/"
                + "ssz1-sonic-tails-cold-first-replica-800.bk2"));
        assertEquals(9914, first.getFrameCount());
        for (int frame = 0; frame < movie.getFrameCount(); frame++) {
            assertEquals(movie.getFrame(frame), roundtrip.getFrame(frame), "authored pad " + frame);
            assertEquals(RecordedInputSnapshots.fromBk2(movie.getFrame(frame),
                            frame == 0 ? null : movie.getFrame(frame - 1)),
                    RecordedInputSnapshots.fromBk2(roundtrip.getFrame(frame),
                            frame == 0 ? null : roundtrip.getFrame(frame - 1)), "logical pad " + frame);
            if (frame < first.getFrameCount()) {
                assertEquals(first.getFrame(frame), movie.getFrame(frame), "original prefix " + frame);
            }
        }
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off",
                null, null, null);
        var drawing = new RouteFrameDrawing(true);
        // Prefix mechanisms remain covered separately by the shorter first-replica method.
        var spots = Set.of(30, 1390, 6040, 6257, 8050, 9312, 9750, 10500, 11640,
                14617, 14943, 15023, 15361, 15377, 15396, 15688, 16472, 17197, 17198,
                17434, 17500);
        var checked = new HashSet<String>();
        var ghz = new Health("GHZ");
        var mtz = new Health("MTZ");
        var mtzHurts = new HashSet<Integer>();
        boolean previousHurt = false, charged = false, rose = false, settled = false;
        boolean released = false, receiver = false, liveOrbs = false;
        CompositeSnapshot beforeMtz = null, beforeMtzForward = null;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            var p1 = session.player();
            var p2 = GameServices.sprites().getRegisteredSidekicks().getFirst();
            var world = GameServices.level().getCurrentLevel();
            var manager = GameServices.level().getObjectManager();
            var runtime = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            int lives = GameServices.gameState().getLives();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                context(session, p1, p2, world, manager, lives, "ordinary input=" + frame);
                assertSame(runtime, S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry())
                        .orElseThrow(), "same installed SSZ state");
                var pending = new HashSet<String>();
                if (spots.contains(frame)) pending.add("input-" + frame);
                for (var boss : manager.activeObjectsOfType(SszGhzBossObjectInstance.class)) {
                    int hp = boss.hitsRemainingForTest();
                    if (ghz.observe(hp)) pending.add("ghz-health-" + hp);
                    if (hp == 0) assertTrue(boss.getState().defeated);
                }
                for (var boss : manager.activeObjectsOfType(SszMtzBossObjectInstance.class)) {
                    int hp = boss.hitsRemainingForTest();
                    if (mtz.observe(hp)) pending.add("mtz-health-" + hp);
                    liveOrbs |= boss.liveOrbsForTest() > 0;
                    if (hp == 0) assertTrue(boss.getState().defeated);
                }
                boolean hurt = p1.isHurt();
                if (frame >= 15397 && hurt != previousHurt) {
                    pending.add(hurt ? "mtz-hurt-" + frame : "mtz-recovered-" + frame);
                    if (hurt) mtzHurts.add(frame);
                }
                previousHurt = hurt;
                var source = pad(190);
                if (source != null && source.stateForTest() != SSZHPZTeleporterObjectInstance.STATE_IDLE) {
                    assertEquals(5888, source.getSpawn().x());
                    assertEquals(1072, source.getSpawn().y());
                    assertEquals(0xF6, source.getSpawn().subtype());
                    assertTrue(p1.isObjectControlled());
                    assertSame(source, manager.getRidingObject(p1), "actual source-pad rider");
                    int state = source.stateForTest();
                    if (state == SSZHPZTeleporterObjectInstance.STATE_CHARGING && !charged) {
                        charged = true; pending.add("pad190-charge");
                    }
                    if (state == SSZHPZTeleporterObjectInstance.STATE_RISING && !rose) {
                        rose = true; pending.add("pad190-rise");
                    }
                    if (state == SSZHPZTeleporterObjectInstance.STATE_SETTLING && !settled) {
                        settled = true; pending.add("pad190-settle");
                    }
                }
                if (charged && !p1.isObjectControlled() && !released) {
                    released = true;
                    assertEquals(17520, frame, "original source owner releases controls");
                }
                var destination = pad(189);
                if (released && destination != null && manager.getRidingObject(p1) == destination) {
                    assertReceiver(p1, destination);
                    receiver = true;
                }
                semanticBoundary(frame, p1);
                pending.removeAll(checked);
                if (!pending.isEmpty()) {
                    replay(session, movie, frame, pending.toString(), drawing, p1, p2,
                            world, manager, lives);
                    checked.addAll(pending);
                }
                if (frame == 15396 || frame == 15441) {
                    var snapshot = SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
                    GameplayInputBranchTool.requireRestorableCheckpoint(snapshot,
                            GameServices.level().hasPendingFreshLevelTransitionBoundary());
                    if (frame == 15396) beforeMtz = snapshot;
                    else beforeMtzForward = snapshot;
                }
            }
            assertEquals(ALL_HEALTH, ghz.seen, "each GHZ hit");
            assertEquals(ALL_HEALTH, mtz.seen, "each MTZ hit");
            assertTrue(liveOrbs, "actual live MTZ attack children");
            assertEquals(Set.of(15689, 16473), mtzHurts, "observed hurts belong only this new route");
            assertTrue(charged && rose && settled && released && receiver);
            assertEquals(-1, runtime.eventsBgByte(2), "MTZ defeat publishes before transport");
            var destination = pad(189);
            assertNotNull(destination);
            assertEquals(5888, p1.getCentreX());
            assertEquals(139, p1.getCentreY());
            assertEquals(0, p1.getXSpeed());
            assertEquals(0, p1.getYSpeed());
            assertEquals(0, p1.getGSpeed());
            assertEquals(0, p1.getRingCount(), "new independently reached receiver only; old46 unchanged");
            assertFalse(p1.isHurt());
            assertFalse(p2.isHurt());
            assertEquals(3, GameServices.gameState().getLives(), "independent receiver endpoint lives");
            assertReceiver(p1, destination);
            for (int spot : spots) assertTrue(checked.contains("input-" + spot));
            for (int hp : ALL_HEALTH) {
                assertTrue(checked.contains("ghz-health-" + hp));
                assertTrue(checked.contains("mtz-health-" + hp));
            }
            assertTrue(checked.containsAll(Set.of("pad190-charge", "pad190-rise", "pad190-settle")));
            // Restore across actual boss/orb retirement and receiver creation, still in SSZ.
            // This is a graph-lifetime check, separate from the local 45-input windows.
            assertNotNull(beforeMtz);
            assertNotNull(beforeMtzForward);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var endpoint = registry.capture();
            GameplayInputBranchTool.requireRestorableCheckpoint(endpoint,
                    GameServices.level().hasPendingFreshLevelTransitionBoundary());
            for (int cycle = 0; cycle < 2; cycle++) {
                String where = "retired-MTZ/receiver graph cycle=" + cycle;
                context(session, p1, p2, world, manager, lives, where + " before restore");
                GameplayInputBranchTool.requireRestorableCheckpoint(beforeMtz,
                        GameServices.level().hasPendingFreshLevelTransitionBoundary());
                registry.restore(beforeMtz);
                same(beforeMtz, registry.capture(), where + " EARLY_RESTORE "
                        + eligibility(session, world, manager));
                session.restoreInputHistory(movie.getFrame(15396));
                for (int n = 1; n <= HORIZON; n++) {
                    session.step(movie.getFrame(15396 + n)); drawing.draw(session);
                    context(session, p1, p2, world, manager, lives, where + " replay n=" + n);
                }
                same(beforeMtzForward, registry.capture(), where + " EARLY_FORWARD "
                        + eligibility(session, world, manager));
                GameplayInputBranchTool.requireRestorableCheckpoint(endpoint,
                        GameServices.level().hasPendingFreshLevelTransitionBoundary());
                registry.restore(endpoint);
                same(endpoint, registry.capture(), where + " RECEIVER_RESTORE "
                        + eligibility(session, world, manager));
                session.restoreInputHistory(movie.getFrame(17545));
                context(session, p1, p2, world, manager, lives, where + " restored receiver");
                assertReceiver(p1, pad(189));
            }
            assertEquals(0, drawing.skippedFrames(), "normal drawing in ordinary and replay paths");
            System.out.println("SSZ800 second replica: " + checked.size()
                    + " strict two-cycle45 windows plus retired-MTZ/receiver graph cycles; actual receiver189 release; draws=" + drawing.drawnFrames());
        }
    }

    private static void replay(GameplayCaptureSession session, Bk2Movie movie, int frame,
            String labels, RouteFrameDrawing drawing, AbstractPlayableSprite p1,
            AbstractPlayableSprite p2, Object world, Object manager, int lives) throws Exception {
        assertTrue(frame + HORIZON < movie.getFrameCount(), "complete 45-input window " + labels);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        context(session, p1, p2, world, manager, lives, labels + " capture");
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved,
                GameServices.level().hasPendingFreshLevelTransitionBoundary());
        for (int n = 1; n <= HORIZON; n++) {
            session.step(movie.getFrame(frame + n)); drawing.draw(session);
            context(session, p1, p2, world, manager, lives,
                    "FIRST_FORWARD " + labels + " frame=" + frame + " n=" + n);
            semanticBoundary(frame + n, p1);
        }
        var forward = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            String where = labels + " frame=" + frame + " cycle=" + cycle;
            context(session, p1, p2, world, manager, lives, where + " before restore");
            GameplayInputBranchTool.requireRestorableCheckpoint(saved,
                    GameServices.level().hasPendingFreshLevelTransitionBoundary());
            registry.restore(saved);
            same(saved, registry.capture(), where + " RESTORE " + eligibility(session, world, manager));
            session.restoreInputHistory(movie.getFrame(frame));
            for (int n = 1; n <= HORIZON; n++) {
                session.step(movie.getFrame(frame + n)); drawing.draw(session);
                context(session, p1, p2, world, manager, lives,
                        "REPLAY " + where + " n=" + n);
                semanticBoundary(frame + n, p1);
            }
            same(forward, registry.capture(), where + " FORWARD " + eligibility(session, world, manager));
        }
        context(session, p1, p2, world, manager, lives, labels + " final restore eligibility");
        registry.restore(saved);
        same(saved, registry.capture(), labels + " FINAL_RESTORE " + eligibility(session, world, manager));
        session.restoreInputHistory(movie.getFrame(frame));
    }

    private static void context(GameplayCaptureSession session, AbstractPlayableSprite p1,
            AbstractPlayableSprite p2, Object world, Object manager, int lives, String where) {
        assertSame(world, GameServices.level().getCurrentLevel(), where);
        assertSame(manager, GameServices.level().getObjectManager(), where);
        assertEquals(10, GameServices.level().getCurrentZone(), where);
        assertEquals(0, GameServices.level().getCurrentAct(), where);
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), where);
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary(), where);
        assertFalse(SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion(), where);
        assertSame(p1, session.player(), where);
        assertSame(p1, GameServices.sprites().getMainPlayable(), where);
        assertSame(p1, GameServices.camera().getFocusedSprite(), where);
        assertEquals(800, GameServices.camera().getWidth(), where);
        assertInstanceOf(Sonic.class, p1);
        var roster = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, roster.size(), where);
        assertSame(p2, roster.getFirst(), where);
        assertInstanceOf(Tails.class, p2);
        assertFalse(p1.isCpuControlled(), where + " native P1 dispatch");
        assertTrue(p2.isCpuControlled(), where + " native CPU sidekick dispatch");
        assertSame(p2, GameServices.level().getObjectManager().getObjectServices()
                .playerQuery().nativeP2OrNull(), where);
        assertTrue(p1.isNativeSlotPresent(), where);
        assertTrue(p2.isNativeSlotPresent(), where);
        assertFalse(p1.getDead(), where + " P1 death");
        assertFalse(p2.getDead(), where + " P2 death");
        assertTrue(GameServices.gameState().getLives() >= lives, where + " life loss");
        assertFalse(p1.isSuperSonic(), where);
    }

    private static SSZHPZTeleporterObjectInstance pad(int layout) {
        var pads = GameServices.level().getObjectManager()
                .activeObjectsOfType(SSZHPZTeleporterObjectInstance.class).stream()
                .filter(p -> p.getSpawn().layoutIndex() == layout).toList();
        assertTrue(pads.size() <= 1, "unique actual placement " + layout);
        return pads.isEmpty() ? null : pads.getFirst();
    }

    private static void semanticBoundary(int input, AbstractPlayableSprite p1) {
        if (input == 17253 || input == 17400 || input == 17455) {
            var source = pad(190);
            assertNotNull(source, "original source-pad lifetime input=" + input);
            assertSame(source, GameServices.level().getObjectManager().getRidingObject(p1));
            assertTrue(p1.isObjectControlled());
            assertTrue(p1.isObjectControlAllowsCpu());
            assertTrue(p1.isObjectControlSuppressesMovement());
            int expected = input == 17253 ? SSZHPZTeleporterObjectInstance.STATE_CHARGING
                    : input == 17400 ? SSZHPZTeleporterObjectInstance.STATE_RISING
                    : SSZHPZTeleporterObjectInstance.STATE_SETTLING;
            assertEquals(expected, source.stateForTest());
        }
        if (input == 17520) {
            assertFalse(p1.isObjectControlled(), "transport release");
            assertFalse(p1.isControlLocked());
        }
        if (input == 17545) {
            var destination = pad(189);
            assertNotNull(destination);
            assertReceiver(p1, destination);
        }
    }

    private static void assertReceiver(AbstractPlayableSprite p1,
            SSZHPZTeleporterObjectInstance destination) {
        assertSame(destination, GameServices.level().getObjectManager().getRidingObject(p1));
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().contains(destination));
        assertFalse(destination.isDestroyed());
        assertEquals(5888, destination.getSpawn().x());
        assertEquals(176, destination.getSpawn().y());
        assertEquals(0, destination.getSpawn().subtype());
        assertEquals(SSZHPZTeleporterObjectInstance.STATE_IDLE, destination.stateForTest());
        assertFalse(p1.getAir());
        assertFalse(p1.isJumping());
        assertFalse(p1.isObjectControlled());
        assertFalse(p1.isControlLocked());
        assertFalse(p1.isObjectControlAllowsCpu());
        assertFalse(p1.isObjectControlSuppressesMovement());
        assertTrue(p1.isOnObject());
        // SolidObjCheckSloped2 / byte_466E8: original pad slope, no terrain rescan.
        var params = destination.getSolidParams();
        int column = (p1.getCentreX() - destination.getX() + params.halfWidth()) >> 1;
        byte[] slope = destination.getSlopeData();
        assertTrue(column >= 0 && column < slope.length, "actual pad slope column");
        assertEquals(destination.getY() - (slope[column] & 0xFF) - 1,
                p1.getCentreY() + p1.getYRadius(), "actual receiver feet seating");
    }

    private static String eligibility(GameplayCaptureSession session, Object world, Object manager) {
        var mode = SessionManager.getCurrentGameplayMode();
        return "worldSame=" + (world == GameServices.level().getCurrentLevel())
                + " managerSame=" + (manager == GameServices.level().getObjectManager())
                + " mode=" + session.loop().getCurrentGameMode()
                + " pendingFresh=" + GameServices.level().hasPendingFreshLevelTransitionBoundary()
                + " fade=" + mode.getFadeManager().getState()
                + " callbackPending=" + mode.getFadeManager().hasPendingCompletion();
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where + " keyset");
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " FIRST_KEY=" + key
                    + " oldType=" + (expected.get(key) == null ? "null" : expected.get(key).getClass().getName())
                    + " newType=" + (actual.get(key) == null ? "null" : actual.get(key).getClass().getName())
                    + " old/new=" + differences
                    + " savedFadePoison=" + (expected.get("fademanager") instanceof FadeManagerSnapshot f
                    && f.isPoisoned()));
        }
    }

    private static final class Health {
        private final String name;
        private final Set<Integer> seen = new HashSet<>();
        private int previous = 8;
        Health(String name) { this.name = name; }
        boolean observe(int health) {
            assertTrue(health >= 0 && health <= previous, name + " cannot heal: " + previous + "→" + health);
            previous = health;
            return seen.add(health);
        }
    }
}
