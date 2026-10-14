package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHpzInstance;
import com.openggf.game.sonic3k.objects.HpzCollapseBlockFragmentObjectInstance;
import com.openggf.game.sonic3k.objects.HpzCraneEmeraldDebrisObjectInstance;
import com.openggf.game.sonic3k.objects.HpzShipSparkOrbiterObjectInstance;
import com.openggf.game.sonic3k.objects.HpzTeleporterRouteHelperObjectInstance;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.game.sonic3k.objects.SSZHPZTeleporterObjectInstance;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Controller-only cold LRZ1 through actual HPZ and the released character-specific SSZ entry. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzJoinedColdRouteCapture {
    private record Chain(boolean knuckles, String movie, String prefixMovie, int prefixLength,
                         int length, int hpzLoad, int sszLoad, int sszAct,
                         int sszHistoryStart, int endpointX, int endpointY, int endpointRings,
                         Set<Integer> spots) { }

    @Test
    void coldPairDefeatsKnucklesAndReplaysTheJoinedTheftCollapseAndSszArrival() throws Exception {
        verify(new Chain(false, "lrz-sonic-tails-cold-hpz-ssz1-320.bk2",
                "lrz-boss-sonic-tails-cold-hpz-320.bk2", 53047, 67455, 52925, 67004,
                0, 66900, 485, 3180, 0,
                Set.of(52940, 53040, 53125, 53380, 53700, 54080, 57250, 57500,
                        57900, 58400, 59400, 60217, 60400, 61053, 61745, 61850,
                        61930, 62030, 62150, 62600, 62900, 63150, 63290, 63500,
                        63800, 64000, 64250, 64800, 65000, 65300, 65500, 65750,
                        65930, 66100, 66300, 66500, 66730, 66830, 67020, 67120,
                        67220, 67320, 67400)));
    }

    @Test
    void coldKnucklesReplaysUpperPadAndTheJoinedSszCraneControlRelease() throws Exception {
        verify(new Chain(true, "lrz-knuckles-cold-hpz-ssz2-320.bk2",
                "lrz-knuckles-cold-hpz-320.bk2", 49526, 51710, 49286, 50138,
                1, 50115, 356, 1196, 3,
                Set.of(49295, 49380, 49530, 49700, 49800, 49895, 49970, 50040,
                        50150, 50200, 50300, 50500, 50700, 50900, 51000, 51100,
                        51200, 51300, 51400, 51550, 51620, 51650)));
    }

    private static void verify(Chain chain) throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(route(chain.movie()));
        var prefix = loader.loadMovieOrInputLog(route(chain.prefixMovie()));
        assertEquals(chain.length(), movie.getFrameCount());
        assertEquals(chain.prefixLength(), prefix.getFrameCount());
        for (int frame = 0; frame < prefix.getFrameCount(); frame++) {
            assertEquals(prefix.getFrame(frame), movie.getFrame(frame), "qualified incoming input " + frame);
        }
        var settings = new GameplayCaptureSession.Settings(320, chain.knuckles() ? "knuckles" : "sonic",
                chain.knuckles() ? "" : "tails", "off", null, null, null);
        var checked = new HashSet<String>();
        var health = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing(true);
        int hpzLoad = -1, hpzAssembly = -1, sszLoad = -1, sszAssembly = -1, released = -1;
        long lrzHistory = 0, hpzHistory = 0;
        boolean hpzIsolated = false, sszIsolated = false, sawTransport = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            int lives = GameServices.gameState().getLives();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Short live-history tails follow all source replay windows. Their real full
                // loads retire history; raw registry restores never cross those boundaries.
                if (frame == chain.hpzLoad() - 25 || frame == chain.sszHistoryStart()) {
                    GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
                }
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertPlayable(session, chain, lives, frame);
                lives = GameServices.gameState().getLives();
                var level = GameServices.level();
                var mode = SessionManager.getCurrentGameplayMode();
                var rewind = mode.getRewindController();
                int zone = level.getCurrentZone(), act = level.getCurrentAct();
                if (frame >= chain.hpzLoad() - 25 && hpzLoad < 0 && rewind != null) {
                    lrzHistory = Math.max(lrzHistory, rewind.currentFrame());
                }
                if (zone == 22 && act == 1 && hpzLoad < 0) {
                    hpzLoad = frame;
                    assertTrue(lrzHistory > 10, "nonempty outgoing LRZ history");
                }
                if (hpzLoad >= 0 && hpzAssembly < 0 && releasedAssembly()) {
                    hpzAssembly = frame;
                    assertEquals(chain.knuckles() ? 16 : 48, session.player().getCentreX());
                    assertEquals(chain.knuckles() ? 748 : 2796, session.player().getCentreY());
                    assertEquals(0, session.player().getRingCount(), "actual fresh HPZ destination counters");
                    assertSame(session.player(), GameServices.camera().getFocusedSprite());
                }
                if (hpzAssembly >= 0 && !hpzIsolated && rewind != null) {
                    assertTrue(rewind.currentFrame() < lrzHistory, "released HPZ history retires LRZ");
                    hpzIsolated = true;
                    GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
                }
                if (frame >= chain.sszHistoryStart() && zone == 22 && rewind != null) {
                    hpzHistory = Math.max(hpzHistory, rewind.currentFrame());
                }
                var pending = new HashSet<String>();
                if (chain.spots().contains(frame)) pending.add("route-" + frame);
                if (zone == 22 && act == 1) {
                    var hpz = S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                    if (hpz.teleporterTransportActive()) {
                        sawTransport = true;
                        // loc_45744 enters the owning rise routine before the late camera/fade
                        // boundary. A fitted Y threshold can select a window after that boundary.
                        if (level.getObjectManager().activeObjectsOfType(SSZHPZTeleporterObjectInstance.class).stream()
                                .anyMatch(pad -> pad.stateForTest() == SSZHPZTeleporterObjectInstance.STATE_RISING)) {
                            pending.add("pad-rise");
                        }
                    }
                    if (!chain.knuckles() && sawTransport && !hpz.teleporterTransportActive() && !session.player().isObjectControlled()) {
                        pending.add("pad-release");
                    }
                    for (var beam : level.getObjectManager().activeObjectsOfType(TeleporterBeamObjectInstance.class)) {
                        if (!sawTransport) pending.add("pad-charge");
                    }
                    for (var actor : level.getObjectManager().activeObjectsOfType(CutsceneKnucklesHpzInstance.class)) {
                        int hits = actor.collisionPropertyForTest();
                        if (actor.initializedForTest()) {
                            health.add(hits); pending.add("knuckles-health-" + hits);
                        }
                        if (actor.routineForTest() == 0x18) pending.add("knuckles-defeat");
                        if (actor.routineForTest() == 0x24) pending.add("theft-camera-pan");
                        if (actor.routineForTest() == 0x56) pending.add("knuckles-tired");
                    }
                    if (!level.getObjectManager().activeObjectsOfType(HpzCraneEmeraldDebrisObjectInstance.class).isEmpty()) pending.add("emerald-theft");
                    if (level.getObjectManager().activeObjectsOfType(HpzShipSparkOrbiterObjectInstance.class).stream()
                            .anyMatch(orbiter -> orbiter.phaseForTest() >= 3)) pending.add("spark-chains");
                    if (!level.getObjectManager().activeObjectsOfType(HpzCollapseBlockFragmentObjectInstance.class).isEmpty()) {
                        pending.add("collapse-fragments");
                        assertEquals(0x61, Byte.toUnsignedInt(level.getCurrentLevel().getMap().getValue(0, 0x30, 7)));
                        assertEquals(0x61, Byte.toUnsignedInt(level.getCurrentLevel().getMap().getValue(0, 0x31, 7)));
                    }
                    if (level.getObjectManager().activeObjectsOfType(HpzTeleporterRouteHelperObjectInstance.class).stream()
                            .anyMatch(helper -> helper.stageForTest(0) == 1)) pending.add("altar-hold");
                    if (hpz.knucklesCutsceneFlag(7)) pending.add("altar-beam-ready");
                }
                if (zone == 10) {
                    assertEquals(chain.sszAct(), act, "native character-specific SSZ act");
                    if (sszLoad < 0) {
                        sszLoad = frame;
                        assertTrue(hpzHistory > 10, "nonempty outgoing HPZ history");
                    }
                    if (sszAssembly < 0 && releasedAssembly()) {
                        sszAssembly = frame;
                        assertEquals(chain.knuckles() ? 0xA0 : 0x100, session.player().getCentreX());
                        assertEquals((GameServices.camera().getY() & 0xFFFF) + 0x65, session.player().getCentreY());
                        assertEquals(0, session.player().getRingCount());
                        assertSame(session.player(), GameServices.camera().getFocusedSprite());
                    }
                    if (sszAssembly >= 0 && !sszIsolated && rewind != null) {
                        assertTrue(rewind.currentFrame() < hpzHistory, "SSZ retires the outgoing HPZ timeline");
                        sszIsolated = true;
                        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
                    }
                    for (var arrival : level.getObjectManager().activeObjectsOfType(SszArrivalControllerObjectInstance.class)) {
                        if (arrival.phaseForTest() == SszArrivalControllerObjectInstance.PHASE_RISE) pending.add("ssz-arrival-rise");
                        if (arrival.phaseForTest() == SszArrivalControllerObjectInstance.PHASE_SWING) pending.add("ssz-arrival-swing");
                    }
                    var ssz = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                    if (released < 0 && releasedAssembly() && !session.player().isObjectControlled()
                            && !session.player().isControlLocked() && (!chain.knuckles() || ssz.cutsceneFlag(5))) {
                        released = frame; pending.add("ssz-control-release");
                    }
                }
                pending.removeAll(checked);
                if (!pending.isEmpty()) {
                    replayTwice(session, movie, drawing, frame, pending, chain, lives);
                    checked.addAll(pending);
                }
            }
            assertEquals(chain.hpzLoad(), hpzLoad); assertEquals(hpzLoad + 1, hpzAssembly);
            assertEquals(chain.sszLoad(), sszLoad); assertEquals(sszLoad + 1, sszAssembly);
            assertTrue(hpzIsolated && sszIsolated, "both real full loads retire their source timelines");
            assertTrue(released > sszAssembly, "scripted arrival finishes after native assembly");
            var required = new HashSet<>(Set.of("pad-charge", "pad-rise", "ssz-arrival-rise",
                    "ssz-arrival-swing", "ssz-control-release"));
            if (!chain.knuckles()) {
                required.addAll(Set.of("pad-release", "knuckles-defeat", "theft-camera-pan", "emerald-theft",
                        "spark-chains", "collapse-fragments", "knuckles-tired", "altar-hold", "altar-beam-ready"));
                for (int hit = 0; hit <= 8; hit++) required.add("knuckles-health-" + hit);
                assertEquals(Set.of(0,1,2,3,4,5,6,7,8), health);
            }
            chain.spots().forEach(frame -> required.add("route-" + frame));
            assertEquals(required, checked);
            assertEquals(chain.endpointX(), session.player().getCentreX());
            assertEquals(chain.endpointY(), session.player().getCentreY());
            assertEquals(chain.endpointRings(), session.player().getRingCount());
            assertFalse(session.player().getAir());
            assertFalse(session.player().isObjectControlled() || session.player().isControlLocked());
            System.out.println("HPZ joined " + (chain.knuckles() ? "Knuckles" : "pair") + ": " + checked.size()
                    + (chain.knuckles() ? " two-cycle whole-world spots (26x45, rise1x8), HPZ " : " two-cycle45 whole-world spots, HPZ ") + hpzLoad + "/" + hpzAssembly
                    + ", SSZ " + sszLoad + "/" + sszAssembly + ", native control release " + released);
        }
    }

    private static void replayTwice(GameplayCaptureSession session, Bk2Movie movie,
                                    RouteFrameDrawing drawing, int frame, Set<String> labels,
                                    Chain chain, int lives) {
        var level = GameServices.level();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertFalse(SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion());
        // Knuckles' upper pad enters STATE_RISING at 50106, reaches loc_45B94's
        // Camera_Y < $240 / Player_1 X >= $B00 handoff at 50126, and loads SSZ2 at 50138. Its eight-frame
        // rise window ends before the independent 50115..50125 live-history tail.
        // Every other selected spot retains the full 45-frame window.
        int window = level.getCurrentZone() == 22
                ? Math.min(45, chain.sszHistoryStart() - frame - 1) : 45;
        assertTrue(window >= 8, "raw window precedes outgoing history: " + labels + " at " + frame);
        assertTrue(frame + window < movie.getFrameCount());
        var world = level.getCurrentLevel();
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved, level.hasPendingFreshLevelTransitionBoundary());
        for (int n = 1; n <= window; n++) {
            session.step(movie.getFrame(frame + n)); drawing.draw(session);
            assertSame(world, level.getCurrentLevel()); assertPlayable(session, chain, lives, frame + n);
        }
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved); same(saved, registry.capture(), labels + " restore " + cycle);
            session.restoreInputHistory(movie.getFrame(frame));
            for (int n = 1; n <= window; n++) {
                session.step(movie.getFrame(frame + n)); drawing.draw(session);
                assertSame(world, level.getCurrentLevel()); assertPlayable(session, chain, lives, frame + n);
            }
            same(expected, registry.capture(), labels + " replay " + cycle);
        }
        registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
    }

    private static boolean releasedAssembly() {
        return !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                && !GameServices.level().getTransitions().isTitleCardRequested();
    }

    private static void assertPlayable(GameplayCaptureSession session, Chain chain, int minimumLives, int frame) {
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "input " + frame);
        assertFalse(session.player().getDead(), "cold death " + frame);
        assertTrue(GameServices.gameState().getLives() >= minimumLives, "hidden respawn " + frame);
        assertSame(GameServices.sprites().getMainPlayable(), session.player());
        assertEquals(320, GameServices.camera().getWidth());
        assertEquals(WidescreenAspect.NATIVE_4_3.name(), GameServices.configuration().getString(SonicConfiguration.DISPLAY_ASPECT));
        var followers = GameServices.sprites().getRegisteredSidekicks();
        if (chain.knuckles()) {
            assertInstanceOf(Knuckles.class, session.player()); assertTrue(followers.isEmpty());
        } else {
            assertInstanceOf(Sonic.class, session.player()); assertEquals(1, followers.size());
            assertInstanceOf(Tails.class, followers.getFirst());
            assertSame(followers.getFirst(), GameServices.level().getObjectManager().getObjectServices().playerQuery().nativeP2OrNull());
            // Native CPU recovery is allowed. P1 survival/lives and complete P2 world state are checked.
        }
    }

    private static Path route(String name) { return Path.of("src/test/resources/routes/s3k", name); }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }
}
