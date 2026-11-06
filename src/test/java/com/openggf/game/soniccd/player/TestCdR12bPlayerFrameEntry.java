package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bPlayerAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.objects.CdTimeWarpLoadData;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.OptionalInt;
import java.util.Arrays;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import static com.openggf.game.soniccd.objects.CdTimeWarpLoadData.NativeStore.*;
import static com.openggf.game.soniccd.objects.CdTimeWarpLoadData.StoreTarget.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Authenticated original-USA R12B pair with complete explicit component ingress. No World/entry/route claim.
 * N1ecc5f3a Sonic/Main:135-155; Main:860-876,894-907,942-958; Load Saved Data:7-40.
 * Actual native act1/period0/map230F60. Constructed component values are not observed destination state.
 * Authored source-only: all new Java UNRUN. No native clears or current art contents are inferred.
 */
class TestCdR12bPlayerFrameEntry {
    private static CdR12bTerrainAssets terrain;
    private static CdR12bPlayerAssets player;
    private static CdR11bAssets past;
    private static final Runnable NOTHING = () -> { };
    private static final List<CdTimeWarpLoadData.NativeStore> OWNED = List.of(
            RESTORE_X, RESTORE_Y, RESTORE_FLAGS, RESTORE_GVEL, RESTORE_X_VELOCITY,
            RESTORE_Y_VELOCITY, RESTORE_BOTTOM, RESTORE_MINI, SET_LEFT_BOUND);

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
            terrain = CdR12bTerrainAssets.fromPrograms(source); player = CdR12bPlayerAssets.fromPrograms(source);
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void absentAndEveryMissingExplicitIngressArgumentAreRejectedWithoutDefaults() {
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(null, null, null, null, null, null));
        requireActual(); var s = state(); var e = environment(1, 0, 1, 1); var a = art(); var c = clock();
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(null, player, s, e, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(terrain, null, s, e, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, null, e, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, null, a, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, null, c));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, null));
    }

    @Test
    void actualDetachedPairRetainsAllExplicitReferencesAndCreatesNoInitArtPrimeOrEntitlement() {
        requireActual(); var s = state(); var e = environment(1, 0, 0x2345, 0x5678); var a = art(); var c = clock();
        var f = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, c);
        assertSame(s, f.state()); assertSame(e, f.environment()); assertSame(a, f.artState()); assertSame(c, f.vIntClock());
        assertSame(s, f.captureActor().state()); assertSame(e, f.captureShared().environment());
        assertSame(a, f.captureShared().artState()); assertSame(c, f.captureShared().vIntClock());
        var bound = f.stageSource(); assertSame(bound, f.stageSource()); assertTrue(bound.ownsAct2Past(terrain, player));
        assertSame(terrain.terrain(), bound.terrain()); assertSame(player.player(), bound.player()); assertSame(player.sonic(), bound.sonic());
        assertEquals(2, bound.stage().actNumber()); assertEquals(0, bound.ordinaryPeriod());
        assertEquals(CdStagePayload.R12B.stage(), bound.stage()); assertEquals(CdNativeFile.R12B, terrain.identity().file());
        assertEquals(terrain.identity(), player.identity()); assertEquals(0x230F60, bound.mappingAddress());
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
        for (int period : new int[]{0, 0x80, 0x81, 0x82}) {
            var f = frame(1, period); f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
            f.serviceLevelPaletteFade(8, NOTHING, NOTHING);
            assertEquals(period, f.environment().movement().gates().timeZone());
            assertEquals(1, f.environment().movement().gates().zoneAct());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut()); assertTrue(f.vIntClock().absoluteValue().isEmpty());
        }
        int[][] wrong = {{0, 0}, {2, 0}, {0x0101, 0}, {0xFFFF, 0}, {1, 1}, {1, 2}, {1, 0x7F}, {1, 0x83}, {1, 0xFF}};
        for (var pair : wrong) {
            var f = frame(pair[0], pair[1]); var a = f.captureActor(); var s = f.captureShared();
            assertSourceRejected(() -> f.serviceLevelLoad(8, TestCdR12bPlayerFrameEntry::unreached,
                    TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
            assertSourceRejected(() -> f.serviceLevelPaletteFade(8, TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
            f.bindArtPublication((request, value) -> unreached());
            var sample = f.prepareInput(8);
            assertSourceRejected(() -> f.serviceVBlank(sample.phase()));
            assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
        }
        var old = CdPlayerFrameRuntime.fromPastEntry(past, state(), environment(1, 0, 0, 0), art(), clock());
        assertSourceRejected(() -> old.serviceLevelLoad(8, TestCdR12bPlayerFrameEntry::unreached,
                TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        assertSourceRejected(() -> old.serviceLevelPaletteFade(8, TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        assertTrue(old.stageSource().ownsPast(past)); assertFalse(old.closed());
    }

    @Test
    void selectorsCAnd12KeepNativeCallbackOrderRawInputWordTimersClockWrapAndAllNonwrites() {
        requireActual(); var s = state(); var e = environment(1, 0, 0xFFFF, 0xFFFF); var a = art();
        var clock = new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(1), 0xFFFFFFFFL);
        var f = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, clock);
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
        var f = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, before, env, art, clock());
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
        requireActual(); var s = state(); var e = environment(1, 0, 1, 1); var a = art(); var c = clock();
        var f = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, c);
        var equal = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, c);
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
        assertNotSame(bound, equal.stageSource()); assertTrue(bound.ownsAct2Past(terrain, player));
        assertEquals(3, CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);
        assertEquals(5, CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);
    }

    @Test
    void r12bNeverUsesOldProgrammeExitEvenWithNumericallyMatchingActOnePresentGates() {
        requireActual(); var f = frame(1, 1); var a = f.captureActor(); var s = f.captureShared();
        assertSourceRejected(() -> f.serviceOldProgrammeExitLoad(8, a.state(), s.environment(),
                TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        assertSourceRejected(() -> f.serviceOldProgrammeExitPaletteFade(8, a.state(), s.environment(),
                TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
    }

    @Test
    void pendingReentryCallbackFailureAndClosedGuardsRetainTheirExistingSourcePrefixes() {
        requireActual(); var f = frame(1, 0); var s = f.captureShared(); var a = f.captureActor();
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceLevelLoad(0, TestCdR12bPlayerFrameEntry::unreached,
                TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        f.clearPendingInput();
        var fault = new IllegalArgumentException("source publication stopped");
        assertSame(fault, assertThrows(IllegalArgumentException.class,
                () -> f.serviceLevelLoad(0xF5, () -> { throw fault; }, TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached)));
        assertTrue(f.closed()); assertSame(a.state(), f.state()); assertSame(s.artState(), f.artState()); assertSame(s.vIntClock(), f.vIntClock());
        assertEquals(new CdPlayerBody.Controls(0xF5, 0xD1, 8, 0x10, 0x81), f.environment().controls());
        assertSame(s.environment().movement(), f.environment().movement()); assertSame(s.environment().globals(), f.environment().globals());
        assertThrows(IllegalStateException.class, () -> f.serviceLevelPaletteFade(0, TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        assertThrows(IllegalStateException.class, () -> f.restoreActor(a));
        assertThrows(IllegalStateException.class, () -> f.restoreShared(s));
    }

    @Test
    void factoryIsOneExplicitSixArgumentStaticIngressWithoutAnOverloadOrConstructor() throws Exception {
        var method = CdPlayerFrameRuntime.class.getDeclaredMethod("fromAct2PastEntry", CdR12bTerrainAssets.class,
                CdR12bPlayerAssets.class, CdPlayerBody.State.class, CdPlayerBody.Environment.class,
                CdPlayerArtRuntime.State.class, CdPlayerFrameRuntime.VIntClock.class);
        assertEquals(CdPlayerFrameRuntime.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers())); assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(1, Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("fromAct2PastEntry")).count());
        assertTrue(Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredConstructors())
                .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void selectedActualR12bDplcAndArtUseExistingServiceOwnerAndPreserveUnwrittenTails() {
        requireActual(); var before = state();
        var s = new CdPlayerBody.State(before.motion(), new CdPlayerBody.AnimationCursor(5, 6, 5), before.header(),
                before.timers(), before.history(), before.rotationDistance(), before.objectLinks(), before.rotationAngle());
        var e = environment(1, 0, 0, 0); var a = art(); var c = clock();
        var f = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, c);
        int frame = s.animation().mapFrame(); byte[] cpu = a.cpuBuffer().copy(); int written = 0;
        var cue = player.sonic().dplcFrames().get(frame); assertEquals(3, cue.pieces().size());
        for (int i = 0; i < cue.pieces().size(); i++) {
            byte[] piece = player.sonic().artPiece(frame, i).copy();
            System.arraycopy(piece, 0, cpu, written, piece.length); written += piece.length;
        }
        assertEquals(480, written, "Actual R12B frame5 copies 15 tiles and preserves the CPU tail");
        byte[] resident = a.residentBank().copy(); System.arraycopy(cpu, 0, resident, 0, 0x2E0);
        var order = new ArrayList<String>();
        f.bindArtPublication((request, value) -> {
            order.add("SONIC_DMA"); assertSame(c, f.vIntClock()); assertSame(s, f.state());
            assertArrayEquals(Arrays.copyOf(cpu, 0x2E0), request.bytes().copy());
            assertArrayEquals(cpu, value.cpuBuffer().copy()); assertArrayEquals(resident, value.residentBank().copy());
            assertEquals(frame, value.lastFrame()); assertFalse(value.dirty());
            assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
        });
        var sample = f.prepareInput(8);
        f.serviceVBlank(sample.phase(), () -> order.add("PALETTE"), () -> order.add("LEVEL_ART"), () -> order.add("MAIN_MUSIC"));
        assertEquals(List.of("PALETTE", "SONIC_DMA", "LEVEL_ART", "MAIN_MUSIC"), order);
        assertArrayEquals(cpu, f.artState().cpuBuffer().copy()); assertArrayEquals(resident, f.artState().residentBank().copy());
        assertEquals(1, f.vIntClock().deltaSinceAdmittedCut()); assertSame(s, f.state());
        assertEquals(e.globals().levelFrames(), f.environment().globals().levelFrames()); assertTrue(f.display().isEmpty());
        f.clearPendingInput(); assertFalse(f.closed());
    }

    @Test
    void invalidSelectedArtFrameFailsAtItsExistingServiceCutAfterOnlyRawControllerPrefix() {
        requireActual(); var before = state();
        var s = new CdPlayerBody.State(before.motion(), new CdPlayerBody.AnimationCursor(5, 6, 0xFF), before.header(),
                before.timers(), before.history(), before.rotationDistance(), before.objectLinks(), before.rotationAngle());
        var e = environment(1, 0, 0x2345, 0x5678); var a = art(); var c = clock();
        var f = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, a, c);
        var source = f.stageSource(); int[] palettes = {0};
        f.bindArtPublication((request, value) -> unreached()); var sample = f.prepareInput(8);
        assertThrows(IndexOutOfBoundsException.class, () -> f.serviceVBlank(sample.phase(),
                () -> palettes[0]++, TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        assertEquals(1, palettes[0]); assertTrue(f.closed()); assertSame(s, f.state()); assertSame(a, f.artState()); assertSame(c, f.vIntClock());
        assertSame(e.movement(), f.environment().movement()); assertSame(e.globals(), f.environment().globals());
        assertSame(e.dynamicSlots(), f.environment().dynamicSlots()); assertSame(source, f.stageSource());
        assertEquals(new CdPlayerBody.Controls(8, 8, 8, 0x10, 0x81), f.environment().controls());
        assertTrue(f.display().isEmpty()); assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
    }

    @Test
    void nineAddressedR12bFieldsKeepExactOtherComponentsFractionsAndRawWidthEdges() {
        requireActual();
        for (var instruction : OWNED) for (int candidate : new int[]{0, 0x7F, 0x80, 0xFF, 0x7FFF, 0x8000, 0xFFFF}) {
            int value = candidate & (instruction.target().width() == 1 ? 0xFF : 0xFFFF);
            var f = frame(1, 0x80); var a = f.captureActor(); var shared = f.captureShared(); var source = f.stageSource();
            f.bindArtPublication((request, art) -> unreached());
            f.applyTimeWarpLoadStore(loadCall(f, instruction, value));
            assertEquals(expectedState(a.state(), instruction.target(), value), f.state(), instruction.name());
            assertEquals(expectedEnvironment(shared.environment(), instruction.target(), value), f.environment(), instruction.name());
            assertEquals(value, f.readTimeWarpLoadField(instruction.target()));
            assertSame(a.owner(), f.captureActor().owner()); assertSame(shared.owner(), f.captureShared().owner());
            assertSame(a.display(), f.display()); assertSame(shared.artState(), f.artState());
            assertSame(shared.vIntClock(), f.vIntClock()); assertEquals(shared.paused(), f.paused());
            assertSame(source, f.stageSource()); assertFalse(f.closed());
        }
    }

    @Test
    void staleUnknownOutOfWidthAndExternalSavedTargetsRejectWithoutAnyComponentWrite() {
        requireActual(); var f = frame(1, 0x80); var a = f.captureActor(); var s = f.captureShared();
        for (var instruction : OWNED) {
            int old = f.readTimeWarpLoadField(instruction.target());
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.empty(), 0)));
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(old ^ 1), 0)));
            int outside = instruction.target().width() == 1 ? 0x100 : 0x10000;
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(loadCall(f, instruction, outside)));
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(loadCall(f, instruction, -1)));
        }
        for (var instruction : CdTimeWarpLoadData.NativeStore.values()) if (!OWNED.contains(instruction)) {
            assertThrows(IllegalArgumentException.class, () -> f.readTimeWarpLoadField(instruction.target()));
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(0), 0)));
        }
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertFalse(f.closed());
    }

    @Test
    void genuineLoadHelperRoutesOnlyReachedR12bTargetsAndNeverWritesSavedValueOrPulsesClock() {
        requireActual(); var f = frame(1, 0x80); var original = f.state(); var environment = f.environment();
        var shared = f.captureShared(); var saved = suppliedSaved(false); int[] valuesBefore = Arrays.stream(CdTimeWarpSaveData.SavedField.values()).mapToInt(saved::read).toArray();
        byte[] mask = saved.knownMask().copy(); var source = f.stageSource(); var port = new ActualLoadPort(f, saved);
        f.bindArtPublication((request, art) -> unreached());
        var result = CdTimeWarpLoadData.load(saved, port);
        assertEquals(List.of(RESTORE_X, RESTORE_Y, RESTORE_FLAGS, RESTORE_GVEL, RESTORE_X_VELOCITY,
                RESTORE_Y_VELOCITY, RESTORE_RINGS, RESTORE_LIVES_FLAGS, RESTORE_TIME, RESTORE_WATER_ROUTINE,
                RESTORE_BOTTOM, RESTORE_DEST_BOTTOM, RESTORE_FG_X, RESTORE_FG_Y, RESTORE_BG_X, RESTORE_BG_Y,
                RESTORE_BG2_X, RESTORE_BG2_Y, RESTORE_BG3_X, RESTORE_BG3_Y), result.completedNativeStores());
        assertEquals(result.completedNativeStores(), port.completed); assertSame(saved, port.currentSaved());
        var expected = original; var expectedEnv = environment;
        int[] values = {0x3456, 0xFEDC, 0xC7, 0x8000, 0xFFFF, 0x1234, 0x0480};
        for (int i = 0; i < values.length; i++) {
            var target = OWNED.get(i).target(); expected = expectedState(expected, target, values[i]);
            expectedEnv = expectedEnvironment(expectedEnv, target, values[i]);
        }
        assertEquals(expected, f.state()); assertEquals(expectedEnv, f.environment());
        assertEquals(0x34564567, f.state().motion().centreX16_16()); assertEquals(0xFEDC89AB, f.state().motion().centreY16_16());
        assertEquals(-32768, f.state().motion().groundVelocity()); assertEquals(-1, f.state().motion().xVelocity());
        assertEquals(List.of(CdTimeWarpLoadData.LiveRead.ZONE, CdTimeWarpLoadData.LiveRead.SPAWN_MODE), port.liveReads);
        assertEquals(0x12, f.environment().movement().gates().miniSonic(), "PP does not load saved mini");
        assertEquals(0x12, f.environment().movement().bounds().left(), "positive spawn2 does not store left");
        assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
        assertSame(original.history(), f.state().history()); assertSame(source, f.stageSource());
        assertArrayEquals(valuesBefore, Arrays.stream(CdTimeWarpSaveData.SavedField.values()).mapToInt(saved::read).toArray()); assertArrayEquals(mask, saved.knownMask().copy());
        assertTrue(f.display().isEmpty()); assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test
    void unknownLaterSavedByteRetainsOnlyTheGenuineHelperEarlierStorePrefix() {
        requireActual(); var f = frame(1, 0x80); var before = f.state(); var shared = f.captureShared();
        var saved = suppliedSaved(true); var port = new ActualLoadPort(f, saved);
        var error = assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> CdTimeWarpLoadData.load(saved, port));
        assertEquals(0xFF1566, error.nativeAddress());
        assertEquals(List.of(RESTORE_X, RESTORE_Y, RESTORE_FLAGS, RESTORE_GVEL), port.completed);
        var expected = before; int[] values = {0x3456, 0xFEDC, 0xC7, 0x8000};
        for (int i = 0; i < values.length; i++) expected = expectedState(expected, OWNED.get(i).target(), values[i]);
        assertEquals(expected, f.state()); assertEquals(shared, f.captureShared()); assertSame(saved, port.currentSaved());
        assertFalse(f.closed()); assertTrue(port.external.isEmpty()); assertTrue(port.liveReads.isEmpty());
    }

    @Test
    void savedFieldPortsRejectPendingEntitledBusyClosedAndWrongSourceCutsBeforeWrites() {
        requireActual(); var f = frame(1, 0x80); var a = f.captureActor(); var shared = f.captureShared();
        f.prepareInput(0); assertThrows(IllegalStateException.class, () -> f.readTimeWarpLoadField(null));
        assertThrows(IllegalStateException.class, () -> f.applyTimeWarpLoadStore(null)); f.clearPendingInput();
        assertEquals(a, f.captureActor()); assertEquals(shared, f.captureShared());
        f.serviceLevelLoad(0, () -> {
            assertThrows(IllegalStateException.class, () -> f.readTimeWarpLoadField(null));
            assertThrows(IllegalStateException.class, () -> f.applyTimeWarpLoadStore(null));
        }, NOTHING, NOTHING);
        var s = initState(); var e = initEnvironment(); var art = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var entitled = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, s, e, art, clock());
        entitled.bindArtPublication((request, state) -> unreached()); var sample = entitled.prepareInput(0); entitled.serviceVBlank(sample.phase());
        assertThrows(IllegalStateException.class, () -> entitled.readTimeWarpLoadField(null));
        assertThrows(IllegalStateException.class, () -> entitled.applyTimeWarpLoadStore(null));
        for (var bad : List.of(frame(0, 0x80), frame(1, 1))) {
            var actor = bad.captureActor(); var common = bad.captureShared();
            assertSourceRejected(() -> bad.readTimeWarpLoadField(null)); assertSourceRejected(() -> bad.applyTimeWarpLoadStore(null));
            assertEquals(actor, bad.captureActor()); assertEquals(common, bad.captureShared());
        }
        var priorState = f.state(); var priorEnv = f.environment(); var priorArt = f.artState(); var priorClock = f.vIntClock();
        f.close(); assertThrows(IllegalStateException.class, () -> f.readTimeWarpLoadField(null));
        assertThrows(IllegalStateException.class, () -> f.applyTimeWarpLoadStore(null));
        assertSame(priorState, f.state()); assertSame(priorEnv, f.environment()); assertSame(priorArt, f.artState()); assertSame(priorClock, f.vIntClock());
    }

    @Test
    void markerClearUsesOnlyExistingWriterAndCannotRelabelR12bAsPresentOrFuture() {
        requireActual();
        for (int period : new int[]{0x80, 0x81, 0x82}) {
            var f = frame(1, period); var before = f.environment(); var state = f.state(); var source = f.stageSource();
            var art = f.artState(); var clock = f.vIntClock(); assertTrue(f.clearLevelStartTimeZoneMarker());
            assertEquals(period & 0x7F, f.environment().movement().gates().timeZone());
            assertSame(state, f.state()); assertSame(art, f.artState()); assertSame(clock, f.vIntClock()); assertSame(source, f.stageSource());
            var expected = expectedEnvironment(before, MINI_SONIC, before.movement().gates().miniSonic());
            var g = expected.movement().gates(); var m = expected.movement();
            expected = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                    new CdPlayerMovement.Gates(g.zoneAct(), period & 0x7F, g.miniSonic(), g.waterSlide(), g.windTunnel(), g.speedShoes(), g.timeWarpDirection()), m.globals()),
                    expected.controls(), expected.globals(), expected.dynamicSlots());
            assertEquals(expected, f.environment());
            if (period == 0x80) { assertFalse(f.clearLevelStartTimeZoneMarker()); f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING); }
            else assertSourceRejected(() -> f.serviceLevelLoad(8, TestCdR12bPlayerFrameEntry::unreached,
                    TestCdR12bPlayerFrameEntry::unreached, TestCdR12bPlayerFrameEntry::unreached));
        }
    }

    private static void requireActual() { assumeTrue(terrain != null && player != null && past != null, "Set soniccd.media.root to original USA media"); }
    private static CdPlayerFrameRuntime frame(int act, int period) {
        return CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, state(), environment(act, period, 1, 1), art(), clock());
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
                new CdPlayerMovement.Bounds(0x10, 0x2000, 0x700, 0), new CdPlayerMovement.Gates(1, 0, 0, 0, 0, 0, 0),
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
    private static CdTimeWarpLoadData.StoreCall loadCall(CdPlayerFrameRuntime f, CdTimeWarpLoadData.NativeStore instruction, int value) {
        return new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(f.readTimeWarpLoadField(instruction.target())), value);
    }
    private static CdPlayerBody.State expectedState(CdPlayerBody.State before, CdTimeWarpLoadData.StoreTarget target, int value) {
        var m = before.motion();
        var next = new CdPlayerMotionState(target == PRIMARY_X ? value << 16 | (m.centreX16_16() & 0xFFFF) : m.centreX16_16(),
                target == PRIMARY_Y ? value << 16 | (m.centreY16_16() & 0xFFFF) : m.centreY16_16(),
                target == PRIMARY_X_VELOCITY ? (short) value : m.xVelocity(), target == PRIMARY_Y_VELOCITY ? (short) value : m.yVelocity(),
                target == PRIMARY_GVEL ? (short) value : m.groundVelocity(), m.routine(), m.angle(), target == PRIMARY_FLAGS ? value : m.flags(),
                m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        return new CdPlayerBody.State(next, before.animation(), before.header(), before.timers(), before.history(),
                before.rotationDistance(), before.objectLinks(), before.rotationAngle());
    }
    private static CdPlayerBody.Environment expectedEnvironment(CdPlayerBody.Environment before, CdTimeWarpLoadData.StoreTarget target, int value) {
        var m = before.movement(); var b = m.bounds(); var g = m.gates();
        var bounds = new CdPlayerMovement.Bounds(target == LEFT_BOUND ? value : b.left(), b.right(), target == BOTTOM_BOUND ? value : b.bottom(), b.bossFight());
        var gates = new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), target == MINI_SONIC ? value : g.miniSonic(),
                g.waterSlide(), g.windTunnel(), g.speedShoes(), g.timeWarpDirection());
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), bounds, gates, m.globals()),
                before.controls(), before.globals(), before.dynamicSlots());
    }
    private static CdTimeWarpSaveData.State suppliedSaved(boolean missingXVelocity) {
        byte[] bytes = new byte[42], mask = new byte[42]; Arrays.fill(mask, (byte) 1);
        int[][] fields = {{4,0x34},{5,0x56},{6,0xFE},{7,0xDC},{8,0xC7},{9,0x04},{10,0x80},
                {31,0x80},{32,0},{33,0xFF},{34,0xFF},{35,0x12},{36,0x34},{41,0xE1}};
        for (var row : fields) bytes[row[0]] = (byte) row[1];
        if (missingXVelocity) mask[33] = 0;
        return CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(bytes), CdAssetBytes.copyOf(mask));
    }
    /** Genuine helper with explicit PP zone0/spawn2; external scalar targets stay in their test port. */
    private static final class ActualLoadPort implements CdTimeWarpLoadData.Port {
        final CdPlayerFrameRuntime frame; final CdTimeWarpSaveData.State saved;
        final EnumMap<CdTimeWarpLoadData.StoreTarget, Integer> external = new EnumMap<>(CdTimeWarpLoadData.StoreTarget.class);
        final List<CdTimeWarpLoadData.NativeStore> completed = new ArrayList<>();
        final List<CdTimeWarpLoadData.LiveRead> liveReads = new ArrayList<>();
        ActualLoadPort(CdPlayerFrameRuntime frame, CdTimeWarpSaveData.State saved) { this.frame = frame; this.saved = saved; }
        public CdTimeWarpSaveData.State currentSaved() { return saved; }
        public OptionalInt read(CdTimeWarpLoadData.StoreTarget target) {
            if (OWNED.stream().anyMatch(i -> i.target() == target)) return OptionalInt.of(frame.readTimeWarpLoadField(target));
            var value = external.get(target); return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
        public OptionalInt readLive(CdTimeWarpLoadData.LiveRead field) {
            liveReads.add(field); return OptionalInt.of(field == CdTimeWarpLoadData.LiveRead.ZONE ? 0 : 2);
        }
        public void commit(CdTimeWarpLoadData.StoreCall call) {
            if (OWNED.contains(call.instruction())) frame.applyTimeWarpLoadStore(call); else external.put(call.target(), call.value());
            completed.add(call.instruction());
        }
    }
    private static void unreached() { fail("Unreached source callback"); }
}
