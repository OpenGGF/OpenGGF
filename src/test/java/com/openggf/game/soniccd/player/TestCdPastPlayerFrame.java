package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
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
 * Actual configured original-USA source products, with complete explicit component values.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Main860-876 (selectorC),
 * Main894-907/942-958 (selector12), Sonic135-155 (Init).
 * These values do not prove native clears, load order, invocation authority or a Past world/route.
 */
class TestCdPastPlayerFrame {
    private static CdR11aAssets present;
    private static CdR11bAssets past;
    private static CdR11bAssets anotherPast;
    private static final Runnable NOTHING = () -> { };

    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        if (root == null || root.isBlank()) return;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            present = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            anotherPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void missingActualAggregateRejectsWithoutDefaults() {
        assertThrows(NullPointerException.class,
                () -> CdPlayerFrameRuntime.fromPastEntry(null, null, null, null, null));
    }

    @Test
    void completeIngressIsBorrowedWithoutInitializationOrArtPrime() {
        requireActual();
        var state = wholeState(3, 0x55);
        var environment = wholeEnvironment(0, 0x2345, 0x5678);
        var art = sentinelArt();
        var clock = new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFEDCBA98L), 0x1234);
        var f = CdPlayerFrameRuntime.fromPastEntry(past, state, environment, art, clock);
        assertSame(state, f.state()); assertSame(environment, f.environment());
        assertSame(art, f.artState()); assertSame(clock, f.vIntClock());
        assertSame(state, f.captureActor().state()); assertSame(environment, f.captureShared().environment());
        assertSame(art, f.captureShared().artState()); assertSame(clock, f.captureShared().vIntClock());
        assertTrue(f.display().isEmpty()); assertFalse(f.paused()); assertFalse(f.closed());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        // Ordinary service still requires the existing second-stage resident-bank binding.
        var selection = f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(selection.phase()));
        assertSame(state, f.state()); assertSame(environment, f.environment());
        assertSame(art, f.artState()); assertSame(clock, f.vIntClock()); assertFalse(f.closed());
    }

    @Test
    void eachCompleteIngressArgumentIsRequired() {
        requireActual(); var state = wholeState(0, 0); var env = wholeEnvironment(0, 0, 0);
        var art = sentinelArt(); var clock = clock();
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromPastEntry(past, null, env, art, clock));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromPastEntry(past, state, null, art, clock));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromPastEntry(past, state, env, null, clock));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromPastEntry(past, state, env, art, null));
    }

    @Test
    void sourceBindingKeepsActualReferencesAndRejectsTheLegacyPresentGetter() {
        requireActual(); var f = frame(0, 0, 0, 0);
        var b = f.stageSource();
        assertSame(b, f.stageSource()); assertTrue(b.ownsPast(past)); assertFalse(b.ownsPast(anotherPast));
        assertFalse(b.ownsPresent(present)); assertSame(past.terrain(), b.terrain());
        assertSame(past.player(), b.player()); assertSame(past.sonic(), b.sonic());
        assertSame(past.identity().stage(), b.stage()); assertSame(past.identity().variant(), b.variant());
        assertEquals(0, b.ordinaryPeriod()); assertEquals(0x230F60, b.mappingAddress());
        assertThrows(IllegalStateException.class, f::assets);
        var a = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present);
        assertSame(present, a.assets()); assertSame(a.stageSource(), a.stageSource());
        assertTrue(a.stageSource().ownsPresent(present)); assertFalse(a.stageSource().ownsPast(past));
        assertEquals(1, a.stageSource().ordinaryPeriod()); assertEquals(0x21F200, a.stageSource().mappingAddress());
        assertNotSame(a.stageSource(), b);
    }

    @Test
    void freshCaptureOwnersRejectOldAndEqualValuedForeignVisits() {
        requireActual(); var state = wholeState(0, 0); var env = wholeEnvironment(0, 0, 0);
        var art = sentinelArt(); var clock = clock();
        var f = CdPlayerFrameRuntime.fromPastEntry(past, state, env, art, clock);
        var same = CdPlayerFrameRuntime.fromPastEntry(past, state, env, art, clock);
        var independent = CdPlayerFrameRuntime.fromPastEntry(anotherPast, state, env, art, clock);
        var a = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present);
        var actor = f.captureActor(); var shared = f.captureShared();
        for (var other : List.of(same, independent, a)) {
            assertNotSame(actor.owner(), other.captureActor().owner());
            assertNotSame(shared.owner(), other.captureShared().owner());
            assertThrows(IllegalArgumentException.class, () -> other.restoreActor(actor));
            assertThrows(IllegalArgumentException.class, () -> other.restoreShared(shared));
            assertThrows(IllegalArgumentException.class, () -> f.restoreActor(other.captureActor()));
            assertThrows(IllegalArgumentException.class, () -> f.restoreShared(other.captureShared()));
        }
        assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
        assertSame(clock, f.vIntClock());
        assertEquals(3, CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);
        assertEquals(5, CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);
    }

    @Test
    void sourceServicesAcceptOnlyTheirBoundOrdinaryPeriodOrActiveMarkers() {
        requireActual();
        for (int period : new int[]{0, 0x80, 0x81, 0x82}) {
            var f = frame(period, 0, 0, 0);
            f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
            f.serviceLevelPaletteFade(8, NOTHING, NOTHING);
            assertEquals(period, f.environment().movement().gates().timeZone());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut());
        }
        for (int period : new int[]{1, 2, 0x7F, 0x83, 0xFF}) {
            var f = frame(period, 0, 0, 0); var a = f.captureActor(); var s = f.captureShared();
            assertSourceRejected(() -> f.serviceLevelLoad(8, TestCdPastPlayerFrame::unreached,
                    TestCdPastPlayerFrame::unreached, TestCdPastPlayerFrame::unreached));
            assertSourceRejected(() -> f.serviceLevelPaletteFade(8, TestCdPastPlayerFrame::unreached,
                    TestCdPastPlayerFrame::unreached));
            assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
            f.bindArtPublication((request, value) -> fail("Rejected source must not service Sonic art"));
            for (var phase : new PlcLifecyclePhase[]{PlcLifecyclePhase.ORDINARY_LEVEL, PlcLifecyclePhase.NORMAL_PAUSE, PlcLifecyclePhase.LAG}) {
                f.prepareInput(8);
                assertSourceRejected(() -> f.serviceVBlank(phase));
                assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
            }
        }
        var old = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present);
        var s = old.captureShared();
        old.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), period(s.environment(), 0),
                s.artState(), s.paused(), s.vIntClock()));
        assertSourceRejected(() -> old.serviceLevelLoad(8, TestCdPastPlayerFrame::unreached,
                TestCdPastPlayerFrame::unreached, TestCdPastPlayerFrame::unreached));
    }

    @Test
    void selectorCReadsRawThenPublishesCopiesAndDrawsBeforeFastPlcAndFinish() {
        requireActual(); var f = frame(0x80, 0, 0xFFFF, 0xFFFF);
        var a = f.captureActor(); var s = f.captureShared(); var before = s.environment();
        var order = new ArrayList<String>();
        f.bindArtPublication((request, value) -> fail("SelectorC has no Sonic DPLC/DMA"));
        Runnable atCut = () -> {
            assertSame(a.state(), f.state()); assertSame(a.display(), f.display()); assertSame(s.artState(), f.artState());
            assertSame(s.vIntClock(), f.vIntClock()); assertFalse(f.paused());
            assertEquals(new CdPlayerBody.Controls(0xF5, 0xD1, 8, 0x10, 0x81), f.environment().controls());
            assertSame(before.movement(), f.environment().movement()); assertSame(before.globals(), f.environment().globals());
            assertSame(before.dynamicSlots(), f.environment().dynamicSlots());
        };
        f.serviceLevelLoad(0xF5, () -> { order.add("PUBLICATION"); atCut.run(); },
                () -> { order.add("COPY_DRAW"); atCut.run(); }, () -> { order.add("FAST_PLC"); atCut.run(); });
        assertEquals(List.of("PUBLICATION", "COPY_DRAW", "FAST_PLC"), order);
        assertEquals(afterService(before, 0xF5, 0xD1, 0, 0), f.environment());
        assertEquals(a, f.captureActor()); assertSame(s.artState(), f.artState());
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 1), f.vIntClock());
        assertFalse(f.paused()); assertFalse(f.closed());
        assertThrows(IllegalStateException.class, () -> f.runBody(0x9999));
    }

    @Test
    void selectorCTimersKeepActiveWordChargePauseAndLongClockSemantics() {
        requireActual();
        int[][] cases = {{0, 0, 0, 0, 0}, {1, 1, 0, 2, 2}, {0xFFFF, 0xFFFF, 0, 0, 0},
                {1, 0xFFFF, 1, 2, 0xFFFF}, {0xFFFF, 1, 0xFF, 0, 1}};
        for (var c : cases) for (boolean paused : new boolean[]{false, true}) {
            var f = frame(0, c[2], c[0], c[1]); var s = f.captureShared();
            f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), paused,
                    new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(1), 0xFFFFFFFFL)));
            var actor = f.captureActor(); var art = f.artState(); int[] calls = {0};
            f.serviceLevelLoad(0xF0, () -> calls[0]++, () -> calls[0]++, () -> calls[0]++);
            assertEquals(3, calls[0]); assertEquals(paused ? c[0] : c[3], f.environment().movement().globals().boredTimer());
            assertEquals(paused ? c[1] : c[4], f.environment().globals().timeWarpTimer());
            assertEquals(s.environment().globals().levelFrames(), f.environment().globals().levelFrames());
            assertEquals(paused, f.paused()); assertEquals(actor, f.captureActor()); assertSame(art, f.artState());
            assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(1), 0), f.vIntClock());
        }
    }

    @Test
    void selectorCNewAndHeldStartRemainRawWithoutPauseOrLogicalWrites() {
        requireActual(); var f = frame(0, 0, 0, 0); var controls = f.environment().controls();
        f.serviceLevelLoad(0x80, NOTHING, NOTHING, NOTHING);
        assertEquals(0x80, f.environment().controls().rawTap()); assertFalse(f.paused());
        f.serviceLevelLoad(0x80, NOTHING, NOTHING, NOTHING);
        assertEquals(0, f.environment().controls().rawTap()); assertFalse(f.paused());
        assertEquals(controls.logicalHeld(), f.environment().controls().logicalHeld());
        assertEquals(controls.logicalTap(), f.environment().controls().logicalTap());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test
    void selectorCShapeNullAndClosedCutGuardsDoNotWriteOrConsumePendingInput() {
        requireActual(); var f = frame(0, 0, 0, 0); var a = f.captureActor(); var s = f.captureShared();
        for (int held : new int[]{-1, 256, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> f.serviceLevelLoad(held, null, null, null));
        }
        assertThrows(NullPointerException.class, () -> f.serviceLevelLoad(0, null, NOTHING, NOTHING));
        assertThrows(NullPointerException.class, () -> f.serviceLevelLoad(0, NOTHING, null, NOTHING));
        assertThrows(NullPointerException.class, () -> f.serviceLevelLoad(0, NOTHING, NOTHING, null));
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
        f.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0, TestCdPastPlayerFrame::unreached,
                TestCdPastPlayerFrame::unreached, TestCdPastPlayerFrame::unreached));
        assertThrows(IllegalStateException.class, () -> f.bindArtPublication((request, value) -> { }));
        assertSame(a.state(), f.state()); assertSame(s.environment(), f.environment());
        assertSame(s.artState(), f.artState()); assertSame(s.vIntClock(), f.vIntClock());
        assertThrows(IllegalStateException.class, f::captureActor);
        f.clearPendingInput(); f.serviceLevelLoad(0, NOTHING, NOTHING, NOTHING); assertFalse(f.closed());
    }

    @Test
    void selectorCRejectsUnconsumedBodyEntitlementAndClosedOwners() {
        requireActual(); var f = initFrame(); f.bindArtPublication((request, value) -> fail("Frame0 has no cue"));
        var selection = f.prepareInput(0); f.serviceVBlank(selection.phase());
        var state = f.state(); var env = f.environment(); var clock = f.vIntClock();
        assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0, TestCdPastPlayerFrame::unreached,
                TestCdPastPlayerFrame::unreached, TestCdPastPlayerFrame::unreached));
        assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(clock, f.vIntClock());
        f.runBody(1); f.close();
        assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0, TestCdPastPlayerFrame::unreached,
                TestCdPastPlayerFrame::unreached, TestCdPastPlayerFrame::unreached));
    }

    @Test
    void selectorCCaughtReentryCannotCreateAnotherMutationOrClock() {
        requireActual(); var f = frame(0, 0, 1, 1); var a = f.captureActor(); var s = f.captureShared(); int[] calls = {0};
        Runnable callback = () -> {
            calls[0]++; var current = f.environment();
            assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0, NOTHING, NOTHING, NOTHING));
            assertThrows(IllegalStateException.class, () -> f.serviceLevelPaletteFade(0, NOTHING, NOTHING));
            assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
            assertThrows(IllegalStateException.class, f::beginLevelStart);
            assertThrows(IllegalStateException.class, f::captureShared);
            assertThrows(IllegalStateException.class, () -> f.restoreActor(a));
            assertThrows(IllegalStateException.class, () -> f.restoreShared(s));
            assertThrows(IllegalStateException.class, f::close);
            assertSame(current, f.environment()); assertSame(s.vIntClock(), f.vIntClock());
        };
        f.serviceLevelLoad(0x80, callback, callback, callback);
        assertEquals(3, calls[0]); assertEquals(1, f.vIntClock().deltaSinceAdmittedCut()); assertFalse(f.closed());
    }

    @Test
    void selectorCFailureAtEveryCallbackKeepsOnlyTheReachedPrefixAndCloses() {
        requireActual();
        for (int stop = 0; stop < 3; stop++) for (boolean fatal : new boolean[]{false, true}) {
            var f = frame(0, 0, 1, 1); var a = f.captureActor(); var s = f.captureShared();
            var order = new ArrayList<Integer>(); int selected = stop;
            Throwable fault = fatal ? new AssertionError("source callback stopped") : new IllegalArgumentException("source callback stopped");
            Runnable[] callbacks = new Runnable[3];
            for (int i = 0; i < 3; i++) {
                int index = i;
                callbacks[i] = () -> { order.add(index); if (index == selected) throwFault(fault); };
            }
            assertSame(fault, assertThrows(Throwable.class,
                    () -> f.serviceLevelLoad(0xF5, callbacks[0], callbacks[1], callbacks[2])));
            assertEquals(List.of(0, 1, 2).subList(0, stop + 1), order);
            assertEquals(new CdPlayerBody.Environment(s.environment().movement(),
                    new CdPlayerBody.Controls(0xF5, 0xD1, 8, 0x10, 0x81),
                    s.environment().globals(), s.environment().dynamicSlots()), f.environment());
            assertSame(a.state(), f.state()); assertSame(a.display(), f.display());
            assertSame(s.artState(), f.artState()); assertSame(s.vIntClock(), f.vIntClock()); assertTrue(f.closed());
            assertThrows(IllegalStateException.class, () -> f.restoreActor(a));
            assertThrows(IllegalStateException.class, () -> f.restoreShared(s));
            assertThrows(IllegalStateException.class, () -> f.bindArtPublication((r, v) -> { }));
            assertThrows(IllegalStateException.class, () -> f.runBody(1));
        }
    }

    @Test
    void selectorCUncaughtReentryClosesAfterRawReadBeforeLaterCallbacks() {
        requireActual(); var f = frame(0, 0, 1, 1); var s = f.captureShared();
        assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0x80, NOTHING, f::beginLevelStart,
                TestCdPastPlayerFrame::unreached));
        assertEquals(0x80, f.environment().controls().rawHeld()); assertSame(s.vIntClock(), f.vIntClock());
        assertSame(s.environment().movement(), f.environment().movement()); assertSame(s.artState(), f.artState());
        assertTrue(f.closed());
    }

    @Test
    void selectorCAndFadeReplayOnlyValuesUnderTheExistingSameOwner() {
        requireActual(); var f = frame(0x82, 0, 1, 1); var a = f.captureActor(); var s = f.captureShared();
        var source = f.stageSource(); int[] callbacks = {0};
        Runnable count = () -> callbacks[0]++;
        f.serviceLevelLoad(0xF5, count, count, count); f.serviceLevelPaletteFade(0xF5, count, count);
        var afterA = f.captureActor(); var afterS = f.captureShared();
        f.restoreActor(a); f.restoreShared(s);
        assertEquals(5, callbacks[0]); assertSame(source, f.stageSource());
        assertSame(a.state(), f.state()); assertSame(s.environment(), f.environment());
        assertSame(s.artState(), f.artState()); assertSame(s.vIntClock(), f.vIntClock());
        f.serviceLevelLoad(0xF5, count, count, count); f.serviceLevelPaletteFade(0xF5, count, count);
        assertEquals(10, callbacks[0]); assertEquals(afterA, f.captureActor()); assertEquals(afterS, f.captureShared());
        assertSame(source, f.stageSource());
    }

    @Test
    void genuineComponentInitUsesPastMapAndPreservesTheExplicitSixRestoredFieldsWithoutArtPrime() {
        requireActual(); var f = initFrame(); var before = f.state(); var env = f.environment(); var art = f.artState();
        f.bindArtPublication((request, value) -> fail("Initial mapFrame0/lastFrame0 must not prime DPLC"));
        var selection = f.prepareInput(8); f.serviceVBlank(selection.phase());
        var publication = f.runBody(1); var result = publication.result(); var m = before.motion();
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, result.boundary()); assertTrue(publication.sounds().isEmpty());
        var expectedMotion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), 2, m.angle(), m.flags(), 4, 9, 19, m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        assertEquals(new CdPlayerBody.State(expectedMotion, before.animation(),
                new CdPlayerBody.Header(1, 0x230F60, 0x780, 2, 0x18), before.timers(),
                before.history(), before.rotationDistance(), before.objectLinks(), before.rotationAngle()), f.state());
        var g = env.globals(); var movement = env.movement();
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 0xC, 0x80),
                movement.bounds(), movement.gates(), movement.globals()), new CdPlayerBody.Controls(8, 8, 0, 0, 1),
                new CdPlayerBody.Globals(1, g.cameraXCentre(), g.timeAttackMode(), g.debugMode(), g.debugCheat(),
                        g.timeWarpTimer(), g.invincible(), g.bossMusic()), env.dynamicSlots()), f.environment());
        assertSame(before.history(), f.state().history()); assertSame(before.animation(), f.state().animation());
        assertEquals(0x78, f.state().timers().hurt()); assertEquals(art, f.artState());
        assertSame(art.cpuBuffer(), f.artState().cpuBuffer()); assertSame(art.residentBank(), f.artState().residentBank());
        assertTrue(f.display().isEmpty());
        assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
        assertThrows(IllegalStateException.class, () -> f.runBody(2));
    }

    private static void requireActual() {
        assumeTrue(present != null && past != null && anotherPast != null,
                "Set soniccd.media.root for original-USA component cases");
    }
    private static CdPlayerFrameRuntime frame(int period, int charge, int bored, int warp) {
        return CdPlayerFrameRuntime.fromPastEntry(past, wholeState(3, charge),
                wholeEnvironment(period, bored, warp), sentinelArt(), clock());
    }
    private static CdPlayerFrameRuntime.VIntClock clock() {
        return new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0);
    }
    private static CdPlayerArtRuntime.State sentinelArt() {
        byte[] cpu = new byte[0x300], resident = new byte[0x300];
        for (int i = 0; i < cpu.length; i++) { cpu[i] = (byte) (i * 7 + 3); resident[i] = (byte) (i * 11 + 5); }
        return new CdPlayerArtRuntime.State(cpu, resident, 7, true);
    }
    private static CdPlayerBody.State wholeState(int mapFrame, int charge) {
        byte[] history = new byte[0x100];
        for (int i = 0; i < history.length; i++) history[i] = (byte) (i * 13 + 7);
        return new CdPlayerBody.State(new CdPlayerMotionState(0x01234567, 0x023489AB, -0x123, 0x234, -0x345,
                0, 0x56, 0x42, 0x23, 7, 11, 6, 7, 9, charge, -0x1234, 0x44, 0x10, 0x77, 0x88),
                new CdPlayerBody.AnimationCursor(5, 6, mapFrame), new CdPlayerBody.Header(1, 0x12345678, 0x2345, 3, 0x21),
                new CdPlayerBody.Timers(0x78, 0x1234, 0x2345, 0x3456),
                new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0x42), 0x67,
                new CdPlayerBody.ObjectLinks(0x20, 0x7F), 0x89);
    }
    private static CdPlayerBody.Environment wholeEnvironment(int period, int bored, int warp) {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78),
                new CdPlayerMovement.Gates(0, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, bored, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 8, 0x10, 0x81),
                new CdPlayerBody.Globals(0x1234, 0x2345, 0x12, 0x34, 0x4567, warp, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdPlayerBody.Environment period(CdPlayerBody.Environment e, int period) {
        var m = e.movement(); var g = m.gates();
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                new CdPlayerMovement.Gates(g.zoneAct(), period, g.miniSonic(), g.waterSlide(), g.windTunnel(),
                        g.speedShoes(), g.timeWarpDirection()), m.globals()), e.controls(), e.globals(), e.dynamicSlots());
    }
    private static CdPlayerBody.Environment afterService(CdPlayerBody.Environment e, int held, int tap, int bored, int warp) {
        var m = e.movement(); var g = m.globals(); var b = e.globals(); var c = e.controls();
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(),
                new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                        bored, g.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())),
                new CdPlayerBody.Controls(held, tap, c.logicalHeld(), c.logicalTap(), c.controlLocked()),
                new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(),
                        warp, b.invincible(), b.bossMusic()), e.dynamicSlots());
    }
    private static CdPlayerFrameRuntime initFrame() {
        // Arbitrary explicit restored component values, not fitted route coordinates or a native-entry transcript.
        var state = new CdPlayerBody.State(new CdPlayerMotionState(0x01340000, 0x02560000, -0x123, 0x234, -0x345,
                0, 0, 0x42, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new CdPlayerBody.AnimationCursor(0, 0, 0), new CdPlayerBody.Header(1, 0, 0, 0, 0),
                new CdPlayerBody.Timers(0x78, 0, 0, 0), new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]), 0),
                0, new CdPlayerBody.ObjectLinks(0, 0), 0);
        var env = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(0x10, 0x2000, 0x300, 0), new CdPlayerMovement.Gates(0, 0, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 0, 0x12, 0x34, 0x56, 0x78)),
                new CdPlayerBody.Controls(0, 0, 0, 0, 1), new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return CdPlayerFrameRuntime.fromPastEntry(past, state, env,
                new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false), clock());
    }
    private static void assertSourceRejected(org.junit.jupiter.api.function.Executable call) {
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, call).boundary());
    }
    private static void throwFault(Throwable fault) {
        if (fault instanceof Error fatal) throw fatal;
        throw (RuntimeException) fault;
    }
    private static void unreached() { fail("Unreached callback"); }
}
