package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdR12aPlayerAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Original-USA detached R12A data and complete explicit component ingress. No World/entry/route claim.
 * N1ecc5f3a Sonic/Main:135-155; Main:860-876,894-907,942-958; actual map230F60.
 * Authored source-only: all new Java UNRUN. No native clears or current art contents are inferred.
 */
class TestCdAct2PlayerFrame {
    private static CdR12aTerrainAssets terrain;
    private static CdR12aPlayerAssets player;
    private static CdR11bAssets past;
    private static final Runnable NOTHING = () -> { };

    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        if (root == null || root.isBlank()) return;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            terrain = CdR12aTerrainAssets.fromPrograms(source); player = CdR12aPlayerAssets.fromPrograms(source);
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void absentAndEveryMissingExplicitIngressArgumentAreRejectedWithoutDefaults() {
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(null, null, null, null, null, null));
        requireActual(); var s = state(); var e = environment(1, 1, 1, 1); var a = art(); var c = clock();
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(null, player, s, e, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(terrain, null, s, e, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(terrain, player, null, e, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, null, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, e, null, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, e, a, null));
    }

    @Test
    void actualDetachedPairRetainsAllExplicitReferencesAndCreatesNoInitArtPrimeOrEntitlement() {
        requireActual(); var s = state(); var e = environment(1, 1, 0x2345, 0x5678); var a = art(); var c = clock();
        var f = CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, e, a, c);
        assertSame(s, f.state()); assertSame(e, f.environment()); assertSame(a, f.artState()); assertSame(c, f.vIntClock());
        assertSame(s, f.captureActor().state()); assertSame(e, f.captureShared().environment());
        assertSame(a, f.captureShared().artState()); assertSame(c, f.captureShared().vIntClock());
        var bound = f.stageSource(); assertSame(bound, f.stageSource()); assertTrue(bound.ownsAct2(terrain, player));
        assertSame(terrain.terrain(), bound.terrain()); assertSame(player.player(), bound.player()); assertSame(player.sonic(), bound.sonic());
        assertEquals(2, bound.stage().actNumber()); assertEquals(1, bound.ordinaryPeriod()); assertEquals(0x230F60, bound.mappingAddress());
        assertThrows(IllegalStateException.class, f::assets);
        assertTrue(f.display().isEmpty()); assertFalse(f.paused()); assertFalse(f.closed());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        var sample = f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(sample.phase()));
        assertSame(s, f.state()); assertSame(e, f.environment()); assertSame(a, f.artState()); assertSame(c, f.vIntClock());
        assertFalse(f.closed());
    }

    @Test
    void ordinaryServicesRequireTheBoundFullActAndPeriodAndNeverAdmitOldFrameActOne() {
        requireActual();
        for (int period : new int[]{1, 0x80, 0x81, 0x82}) {
            var f = frame(1, period); f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
            f.serviceLevelPaletteFade(8, NOTHING, NOTHING);
            assertEquals(period, f.environment().movement().gates().timeZone());
            assertEquals(1, f.environment().movement().gates().zoneAct());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut());
        }
        int[][] wrong = {{0, 1}, {2, 1}, {0x0101, 1}, {0xFFFF, 1}, {1, 0}, {1, 2}, {1, 0x7F}, {1, 0x83}, {1, 0xFF}};
        for (var pair : wrong) {
            var f = frame(pair[0], pair[1]); var a = f.captureActor(); var s = f.captureShared();
            assertSourceRejected(() -> f.serviceLevelLoad(8, TestCdAct2PlayerFrame::unreached,
                    TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
            assertSourceRejected(() -> f.serviceLevelPaletteFade(8, TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
            f.bindArtPublication((request, value) -> unreached());
            var sample = f.prepareInput(8);
            assertSourceRejected(() -> f.serviceVBlank(sample.phase()));
            assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
        }
        var old = CdPlayerFrameRuntime.fromPastEntry(past, state(), environment(1, 0, 0, 0), art(), clock());
        assertSourceRejected(() -> old.serviceLevelLoad(8, TestCdAct2PlayerFrame::unreached,
                TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
        assertSourceRejected(() -> old.serviceLevelPaletteFade(8, TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
        assertTrue(old.stageSource().ownsPast(past)); assertFalse(old.closed());
    }

    @Test
    void selectorsCAnd12KeepNativeCallbackOrderRawInputWordTimersClockWrapAndAllNonwrites() {
        requireActual(); var s = state(); var e = environment(1, 1, 0xFFFF, 0xFFFF); var a = art();
        var clock = new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(1), 0xFFFFFFFFL);
        var f = CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, e, a, clock);
        var order = new ArrayList<String>();
        Runnable cut = () -> {
            assertSame(s, f.state()); assertSame(a, f.artState()); assertSame(clock, f.vIntClock());
            assertSame(e.movement(), f.environment().movement()); assertSame(e.globals(), f.environment().globals());
            assertSame(e.dynamicSlots(), f.environment().dynamicSlots());
            assertEquals(new CdPlayerBody.Controls(0xF5, 0xD1, 8, 0x10, 0x81), f.environment().controls());
            assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
            assertThrows(IllegalStateException.class, () -> f.serviceLevelPaletteFade(0, NOTHING, NOTHING));
        };
        f.serviceLevelLoad(0xF5, () -> { order.add("PUBLICATION"); cut.run(); },
                () -> { order.add("COPY_DRAW"); cut.run(); }, () -> { order.add("FAST_PLC"); cut.run(); });
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(1), 0), f.vIntClock());
        f.serviceLevelPaletteFade(0xF5, () -> order.add("FADE"), () -> order.add("FAST_PLC"));
        assertEquals(List.of("PUBLICATION", "COPY_DRAW", "FAST_PLC", "FADE", "FAST_PLC"), order);
        assertEquals(afterServices(e), f.environment()); assertSame(s, f.state()); assertSame(a, f.artState());
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(1), 1), f.vIntClock());
        assertEquals(OptionalLong.of(2), f.vIntClock().absoluteValue());
        assertFalse(f.paused()); assertFalse(f.closed()); assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test
    void ordinaryInitUsesActualMappingAndReplaysOnlySameOwnerValuesWithoutArtPrime() {
        requireActual(); var before = initState(); var env = initEnvironment();
        var art = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var f = CdPlayerFrameRuntime.fromAct2Entry(terrain, player, before, env, art, clock());
        f.bindArtPublication((request, value) -> fail("mapFrame0/lastFrame0 has no cue or prime"));
        var priorA = f.captureActor(); var priorS = f.captureShared(); var source = f.stageSource();
        for (int pass = 0; pass < 2; pass++) {
            if (pass != 0) { f.restoreActor(priorA); f.restoreShared(priorS); }
            var sample = f.prepareInput(8); f.serviceVBlank(sample.phase());
            var result = f.runBody(1).result(); var m = before.motion();
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, result.boundary());
            var expectedMotion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                    m.groundVelocity(), 2, m.angle(), m.flags(), 4, 9, 19, m.animation(), m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            assertEquals(new CdPlayerBody.State(expectedMotion, before.animation(), new CdPlayerBody.Header(1, 0x230F60, 0x780, 2, 0x18),
                    before.timers(), before.history(), before.rotationDistance(), before.objectLinks(), before.rotationAngle()), f.state());
            var movement = env.movement();
            assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 0xC, 0x80),
                    movement.bounds(), movement.gates(), movement.globals()), new CdPlayerBody.Controls(8, 8, 0, 0, 1),
                    new CdPlayerBody.Globals(1, 160, 0, 0, 0, 0, 0, 0), env.dynamicSlots()), f.environment());
            assertSame(before.history(), f.state().history()); assertSame(before.animation(), f.state().animation());
            assertArtValue(art, f.artState()); assertSame(source, f.stageSource()); assertTrue(f.display().isEmpty());
            assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
            assertThrows(IllegalStateException.class, () -> f.runBody(2));
        }
        assertEquals(priorA.state(), before); assertEquals(0, before.motion().routine());
    }

    @Test
    void captureOwnersRemainFreshAndRestoreCannotRetargetActualSourceOrExecuteCallbacks() {
        requireActual(); var s = state(); var e = environment(1, 1, 1, 1); var a = art(); var c = clock();
        var f = CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, e, a, c);
        var equal = CdPlayerFrameRuntime.fromAct2Entry(terrain, player, s, e, a, c);
        var old = CdPlayerFrameRuntime.fromPastEntry(past, s, e, a, c);
        var actor = f.captureActor(); var shared = f.captureShared(); var bound = f.stageSource();
        for (var other : List.of(equal, old)) {
            assertNotSame(actor.owner(), other.captureActor().owner());
            assertThrows(IllegalArgumentException.class, () -> other.restoreActor(actor));
            assertThrows(IllegalArgumentException.class, () -> other.restoreShared(shared));
            assertThrows(IllegalArgumentException.class, () -> f.restoreActor(other.captureActor()));
            assertThrows(IllegalArgumentException.class, () -> f.restoreShared(other.captureShared()));
        }
        int[] callbacks = {0}; Runnable call = () -> callbacks[0]++;
        f.serviceLevelLoad(8, call, call, call); f.restoreActor(actor); f.restoreShared(shared);
        assertEquals(3, callbacks[0]); assertSame(s, f.state()); assertSame(e, f.environment());
        assertSame(a, f.artState()); assertSame(c, f.vIntClock()); assertSame(bound, f.stageSource());
        assertNotSame(bound, equal.stageSource()); assertTrue(bound.ownsAct2(terrain, player));
        assertEquals(3, CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);
        assertEquals(5, CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);
    }

    @Test
    void act2NeverUsesOldProgrammeExitEvenWithNumericallyMatchingActOnePresentGates() {
        requireActual(); var f = frame(1, 1); var a = f.captureActor(); var s = f.captureShared();
        assertSourceRejected(() -> f.serviceOldProgrammeExitLoad(8, a.state(), s.environment(),
                TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
        assertSourceRejected(() -> f.serviceOldProgrammeExitPaletteFade(8, a.state(), s.environment(),
                TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
    }

    @Test
    void pendingReentryCallbackFailureAndClosedGuardsRetainTheirExistingSourcePrefixes() {
        requireActual(); var f = frame(1, 1); var s = f.captureShared(); var a = f.captureActor();
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0, TestCdAct2PlayerFrame::unreached,
                TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
        f.clearPendingInput();
        var fault = new IllegalArgumentException("source publication stopped");
        assertSame(fault, assertThrows(IllegalArgumentException.class,
                () -> f.serviceLevelLoad(0xF5, () -> { throw fault; }, TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached)));
        assertTrue(f.closed()); assertSame(a.state(), f.state()); assertSame(s.artState(), f.artState()); assertSame(s.vIntClock(), f.vIntClock());
        assertEquals(new CdPlayerBody.Controls(0xF5, 0xD1, 8, 0x10, 0x81), f.environment().controls());
        assertSame(s.environment().movement(), f.environment().movement()); assertSame(s.environment().globals(), f.environment().globals());
        assertThrows(IllegalStateException.class, () -> f.serviceLevelPaletteFade(0, TestCdAct2PlayerFrame::unreached, TestCdAct2PlayerFrame::unreached));
        assertThrows(IllegalStateException.class, () -> f.restoreActor(a));
        assertThrows(IllegalStateException.class, () -> f.restoreShared(s));
    }

    private static void requireActual() { assumeTrue(terrain != null && player != null && past != null, "Set soniccd.media.root to original USA media"); }
    private static CdPlayerFrameRuntime frame(int act, int period) {
        return CdPlayerFrameRuntime.fromAct2Entry(terrain, player, state(), environment(act, period, 1, 1), art(), clock());
    }
    private static CdPlayerFrameRuntime.VIntClock clock() { return new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0); }
    private static CdPlayerArtRuntime.State art() {
        byte[] cpu = new byte[0x300], resident = new byte[0x300];
        for (int i = 0; i < cpu.length; i++) { cpu[i] = (byte) (i * 7 + 3); resident[i] = (byte) (i * 11 + 5); }
        return new CdPlayerArtRuntime.State(cpu, resident, 7, true);
    }
    private static CdPlayerBody.State state() {
        byte[] history = new byte[0x100]; for (int i = 0; i < history.length; i++) history[i] = (byte) (i * 13 + 7);
        return new CdPlayerBody.State(new CdPlayerMotionState(0x01234567, 0x023489AB, -0x123, 0x234, -0x345,
                0, 0x56, 0x42, 0x23, 7, 11, 6, 7, 9, 0, -0x1234, 0x44, 0x10, 0x77, 0x88),
                new CdPlayerBody.AnimationCursor(5, 6, 3), new CdPlayerBody.Header(1, 0x12345678, 0x2345, 3, 0x21),
                new CdPlayerBody.Timers(0x78, 0x1234, 0x2345, 0x3456), new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0x42),
                0x67, new CdPlayerBody.ObjectLinks(0x20, 0x7F), 0x89);
    }
    private static CdPlayerBody.Environment environment(int act, int period, int bored, int warp) {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78), new CdPlayerMovement.Gates(act, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, bored, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 8, 0x10, 0x81), new CdPlayerBody.Globals(0x1234, 0x2345, 0x12, 0x34, 0x4567, warp, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdPlayerBody.Environment afterServices(CdPlayerBody.Environment e) {
        var m = e.movement(); var g = m.globals(); var b = e.globals();
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(),
                new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), 0, g.scoreChain(),
                        g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())),
                new CdPlayerBody.Controls(0xF5, 0, 8, 0x10, 0x81), new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(),
                b.debugMode(), b.debugCheat(), 0, b.invincible(), b.bossMusic()), e.dynamicSlots());
    }
    private static CdPlayerBody.State initState() {
        return new CdPlayerBody.State(new CdPlayerMotionState(0x07001234, 0x04205678, -0x123, 0x234, -0x345,
                0, 0, 0x42, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new CdPlayerBody.AnimationCursor(0, 0, 0),
                new CdPlayerBody.Header(1, 0, 0, 0, 0), new CdPlayerBody.Timers(0x78, 0, 0, 0),
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]), 0), 0, new CdPlayerBody.ObjectLinks(0, 0), 0);
    }
    private static CdPlayerBody.Environment initEnvironment() {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(0x10, 0x2000, 0x700, 0), new CdPlayerMovement.Gates(1, 1, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 0, 0x12, 0x34, 0x56, 0x78)), new CdPlayerBody.Controls(0, 0, 0, 0, 1),
                new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0), CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static void assertArtValue(CdPlayerArtRuntime.State expected, CdPlayerArtRuntime.State actual) {
        assertArrayEquals(expected.cpuBuffer().copy(), actual.cpuBuffer().copy()); assertArrayEquals(expected.residentBank().copy(), actual.residentBank().copy());
        assertEquals(expected.lastFrame(), actual.lastFrame()); assertEquals(expected.dirty(), actual.dirty());
    }
    private static void assertSourceRejected(org.junit.jupiter.api.function.Executable call) {
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY, assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, call).boundary());
    }
    private static void unreached() { fail("Unreached source callback"); }
}
