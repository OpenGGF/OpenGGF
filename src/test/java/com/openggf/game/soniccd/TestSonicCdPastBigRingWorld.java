package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual-World object-only ingress; bounded Root qualification is recorded in the implementation plan.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Level End/Main.asm:385-562;
 * Object Spawner.asm:385-446; Object Animate.asm:14-95. Original USA/FixBugs=0.
 * Every scene starts from the ordinary healthy8278 cut of the existing five-jump recipe.
 * No player, programme, ring count, camera, art, pool filler or callback authority is fabricated.
 * These are object-only ingress witnesses, not naturally spawned9599, native parity or art pixels.
 * The actual low ring count leaves the >=50 touch/programme/player/scroll/FM allocation path UNWITNESSED.
 */
@Isolated
class TestSonicCdPastBigRingWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void lowRingGatePreservesAnOtherwiseUnsupportedParentRoutineAndAllOtherOwners() throws Exception {
        var p = ordinary(); sameChunk(p); assertLowRings(p);
        var entry = fixed(p, 127, false).moveByte(ROUTINE, 255).moveByte(SPRITE_FLAGS, 0x84)
                .moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 253).moveByte(ANIMATION_TIME, 0)
                .moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468).moveByte(FLAGS, 0x40);
        var baseline = baseline(p); seed(p, entry); replay(p);
        assertEquals(entry, p.placement().pool().state(127), "Below50 never admits routine, Init, animation or Draw/BCLR7");
        baseline.assertOtherOwners(p, true); assertEquals(1, calls(p)); assertTrue(bigRingSounds(p).isEmpty());
    }

    @Test void lowRingInsideRangeUsesCurrentPeriodWithoutDrawingOrInitialising() throws Exception {
        var p = ordinary(); sameChunk(p); assertLowRings(p);
        var entry = fixed(p, 127, false).moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 7)
                .moveByte(X_RADIUS, 0x31).moveByte(Y_RADIUS, 0x42).moveByte(WIDTH, 0x53).moveWord(TILE, 0x1234)
                .moveLong(MAPPINGS, 0x76543210);
        var baseline = baseline(p); seed(p, entry); replay(p);
        int camera = presentation(p).pastForegroundImage().cameraX();
        assertTrue((((entry.centreX() & 0xFF80) - (((camera - 128) & 0xFFFF) & 0xFF80)) & 0xFFFF) <= 0x280);
        assertEquals(0, p.frame().environment().movement().gates().timeZone() & 0x80);
        assertEquals(entry, p.placement().pool().state(127)); baseline.assertOtherOwners(p, true);
    }

    @Test void lowRingOutsideRangeClearsOnlyActualSavedLoadedBitAndDeletesAll64Bytes() throws Exception {
        var p = ordinary(); sameChunk(p); assertLowRings(p); int camera = presentation(p).pastForegroundImage().cameraX();
        var entry = fixed(p, 127, false).moveByte(ROUTINE, 253).moveByte(SPRITE_FLAGS, 4).moveByte(SAVED_FLAGS_ID, 241)
                .moveWord(X, ((camera & 0xFF80) + 0x400) & 0xFFFF).moveWord(X_FRACTION, 0x1357).moveByte(FLAGS, 0x40);
        var before = p.placement().capture(); int period = p.frame().environment().movement().gates().timeZone() & 0x7F;
        var bank = before.savedFlags().copy(); int at = 2 + 3 * 241 + period; bank[at] = (byte) (bank[at] & 0x7F);
        var baseline = baseline(p); seed(p, entry); replay(p);
        assertArrayEquals(new byte[64], p.placement().pool().state(127).copyBytes());
        assertArrayEquals(bank, p.placement().capture().savedFlags().copy()); assertEquals(before.status(), p.placement().status());
        baseline.assertOtherOwners(p, true); assertTrue(bigRingSounds(p).isEmpty());
    }

    @Test void everyNonzeroSubtypeUsesFlashBeforeTheActualLowRingGateAndRetainsDirtyNonwrites() throws Exception {
        var p = ordinary(); sameChunk(p); assertLowRings(p);
        var entry = fixed(p, 127, true).moveByte(SUBTYPE, 0xFF).moveByte(ROUTINE, 0)
                .moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468).moveByte(PRIORITY, 6)
                .moveByte(WIDTH, 0x21).moveByte(X_RADIUS, 0x32).moveByte(Y_RADIUS, 0x43).moveByte(FLAGS, 0x40)
                .moveByte(ANIMATION, 0).moveByte(PREVIOUS_ANIMATION, 0).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        var baseline = baseline(p); seed(p, entry); replay(p); var actual = p.placement().pool().state(127);
        var expected = entry.moveByte(ROUTINE, 2).moveWord(TILE, 0x03EF)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_BIG_RING_FLASH).range().nativeAddress())
                .moveByte(ANIMATION_FRAME, 1).moveByte(ANIMATION_TIME, 2).moveByte(MAP_FRAME, 0);
        assertEquals(expected.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertEquals((entry.spriteFlags() | 4) & 0x7C, actual.spriteFlags() & 0x7C);
        baseline.assertOtherOwners(p, false); assertEquals(1, calls(p)); assertTrue(bigRingSounds(p).isEmpty());
    }

    @Test void flashRawLastFrameFcAdvancesRoutineThenNextPassDeletesAndStillCallsDraw() throws Exception {
        var p = ordinary(); sameChunk(p); assertLowRings(p);
        var entry = fixed(p, 127, true).moveByte(ROUTINE, 2).moveByte(ANIMATION, 0).moveByte(PREVIOUS_ANIMATION, 0)
                .moveByte(ANIMATION_FRAME, 8).moveByte(ANIMATION_TIME, 0).moveByte(MAP_FRAME, 7);
        seed(p, entry); replay(p); var animated = p.placement().pool().state(127);
        assertEquals(entry.moveByte(ROUTINE, 4).moveByte(ANIMATION_TIME, 2).moveByte(SPRITE_FLAGS, animated.spriteFlags()), animated);
        replay(p); assertArrayEquals(new byte[64], p.placement().pool().state(127).copyBytes());
        assertEquals(1, calls(p)); assertTrue(bigRingSounds(p).isEmpty());
    }

    @Test void flashDeleteCallsItsClearedIdZeroDrawTailWithoutChangingAnyOtherOwner() throws Exception {
        var p = ordinary(); sameChunk(p); var entry = fixed(p, 127, true).moveByte(ROUTINE, 4)
                .moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468).moveByte(FLAGS, 0x40);
        var baseline = baseline(p); seed(p, entry); replay(p);
        assertArrayEquals(new byte[64], p.placement().pool().state(127).copyBytes()); baseline.assertOtherOwners(p, true);
        assertEquals(1, calls(p)); assertTrue(bigRingSounds(p).isEmpty());
    }

    @Test void heldFlashAnimationDoesNotReadItsInvalidDirectoryUntilItsRealTimerExpires() throws Exception {
        var p = ordinary(); sameChunk(p); var entry = fixed(p, 127, true).moveByte(ROUTINE, 2)
                .moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_TIME, 2).moveByte(MAP_FRAME, 0);
        seed(p, entry); replay(p); var actual = p.placement().pool().state(127);
        assertEquals(entry.moveByte(ANIMATION_TIME, 1).moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
    }

    @Test void malformedFlashDirectoryRetainsOnlyNativeHeadAndClosesWithoutRestore() throws Exception {
        var p = ordinary(); sameChunk(p); var entry = fixed(p, 127, true).moveByte(ROUTINE, 2)
                .moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_TIME, 0);
        seed(p, entry); var failure = assertThrows(CdBigRing.UnsupportedBigRing.class, () -> step(p.harness(), neutral()));
        assertEquals(CdBigRing.UnsupportedBoundary.ANIMATION_DIRECTORY_READ, failure.boundary()); assertEquals(127, failure.wholeIndex());
        assertEquals(entry.moveByte(ANIMATION_TIME, 255), p.placement().pool().state(127));
        assertEquals(0, failure.getSuppressed().length); assertTrue(p.frame().closed()); retired(p);
        var calls = (List<?>) actualField(actualField(p.visit(), "values"), "calls");
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.BIG_RING_BODY)); assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, ((CdObjectDrawQueue) actualField(p.visit(), "drawQueue")).phase());
        // Never restore a closed Frame after the genuine failed source read.
    }

    @Test void flashOddRoutineStopsAheadOfRingsAndAllOtherOwnersWithOriginalImage() throws Exception {
        var p = ordinary(); sameChunk(p); assertLowRings(p); var entry = fixed(p, 127, true).moveByte(ROUTINE, 3);
        seed(p, entry); var program = p.harness().module().levelProgramState();
        var failure = assertThrows(CdBigRing.UnsupportedBigRing.class, () -> step(p.harness(), neutral()));
        assertEquals(CdBigRing.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary()); assertEquals(entry, p.placement().pool().state(127));
        assertSame(program, p.harness().module().levelProgramState()); assertEquals(0, failure.getSuppressed().length);
        assertTrue(p.frame().closed()); retired(p);
    }

    @Test void actualCachedBodyUsesOnlyBothAuthenticatedMappingRootsAndExistingPhysicalArtRegions() throws Exception {
        var p = ordinary(); var body = (CdBigRing) actualField(p.visit(), "bigRing");
        assertEquals(p.assets().view(CdR11bAssets.Asset.MAP_SPR_BIG_RING).range().nativeAddress(), body.assets().ringMappingsAddress());
        assertEquals(p.assets().view(CdR11bAssets.Asset.MAP_SPR_BIG_RING_FLASH).range().nativeAddress(), body.assets().flashMappingsAddress());
        assertEquals(4, body.assets().ring().mappingFrames().size()); assertEquals(8, body.assets().flash().mappingFrames().size());
        assertTrue(body.assets().ring().animations().isEmpty()); assertTrue(body.assets().flash().animations().isEmpty());
        assertArrayEquals(new int[]{10,10,2,10}, body.assets().ring().mappingFrames().stream().mapToInt(f -> f.pieces().size()).toArray());
        assertArrayEquals(new int[]{6,6,10,16,10,6,6,17}, body.assets().flash().mappingFrames().stream().mapToInt(f -> f.pieces().size()).toArray());
        assertEquals(81, SonicCdGameModule.PastLoadCall.BIG_RING_BODY.ordinal());
        assertEquals(0xAF, new SonicCdGameModule.PastBigRingFm().nativeId());
        var baseline = baseline(p); step(p.harness(), neutral()); healthy(p); baseline.assertOtherOwners(p, true);
    }

    @Test void actualProgrammeValuesCannotPublishSpecialStageWithoutThePrivateNativeTouchSite() throws Exception {
        var p = ordinary(); var module = p.harness().module(); var program = module.levelProgramState();
        var failure = assertThrows(IllegalStateException.class, () -> module.preparePastBigRingSpecialStage(
                (SonicCdPastLoadingVisit) p.visit(), p.frame(), program));
        assertTrue(failure.getMessage().contains("genuine Big Ring")); assertSame(program, module.levelProgramState()); healthy(p);
        var missing = assertThrows(SonicCdGameModule.UnsupportedBigRingProgram.class,
                () -> module.pastBigRingTimeStones((SonicCdPastLoadingVisit) p.visit(), null));
        assertEquals(SonicCdGameModule.BigRingProgramBoundary.TIME_STONES_READ, missing.boundary());
        assertSame(program, module.levelProgramState()); healthy(p);
    }

    @Test void scrollPreparationRetainsActualLowByteAndEveryOtherExistingScrollFieldWithoutAdoption() throws Exception {
        var p = ordinary(); var presentation = presentation(p); var expected = presentation.capturePastScroll();
        var ready = presentation.preparePastBigRingScrollLockHighByteOne(p.frame(), expected); var f = expected.state();
        var result = ready.result(); assertTrue(presentation.samePastScroll(expected));
        assertSame(expected.owner(), result.owner()); assertSame(expected.background(), result.background());
        assertSame(expected.plane(), result.plane()); assertSame(expected.copies(), result.copies());
        assertEquals(new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(), f.cameraY16_16(),
                f.horizontalCrossed(), f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags(), f.destBottomBound(),
                f.destLeftBound(), f.bottomBoundShift(), 0x0100 | (f.scrollLock() & 255)), result.state());
        healthy(p);
    }

    private static Prepared ordinary() throws Exception {
        var p = ready(); for (int call = p.returned() + 1; call <= 8278; call++) { step(p.harness(), route(call)); healthy(p); } return p;
    }
    private static void healthy(Prepared p) throws Exception {
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertFalse(p.frame().closed());
        assertInstanceOf(CdBigRing.class, actualField(p.visit(), "bigRing"));
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN)); retired(p);
    }
    private static void retired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeBigRing")); assertFalse((boolean) actualField(p.visit(), "bigRingPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingBigRing"));
    }
    private static void replay(Prepared p) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var source = p.frame().stageSource(); var body = actualField(p.visit(), "bigRing");
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, neutral()); healthy(p); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); healthy(p); step(h, neutral()); healthy(p); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); healthy(p); step(h, neutral()); healthy(p); assertNativeCapture(after, h);
        assertSame(world, h.mode().getWorldSession()); assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, p.frame().stageSource()); assertSame(body, actualField(p.visit(), "bigRing"));
    }
    private static CdObjectState fixed(Prepared p, int whole, boolean flash) {
        var pool = p.placement().pool(); assertEquals(0, pool.state(whole).id()); var m = p.frame().state().motion();
        return pool.state(whole).moveByte(ID, 0x14).moveByte(SUBTYPE, flash ? 1 : 0).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4)
                .moveWord(X, m.centreX16_16() >>> 16).moveWord(Y, m.centreY16_16() >>> 16)
                .moveByte(PRIORITY, 4).moveByte(WIDTH, 32).moveByte(X_RADIUS, 32).moveByte(Y_RADIUS, 32)
                .moveByte(MAP_FRAME, 0).moveWord(TILE, flash ? 0x03EF : 0x2488)
                .moveLong(MAPPINGS, p.assets().view(flash ? CdR11bAssets.Asset.MAP_SPR_BIG_RING_FLASH : CdR11bAssets.Asset.MAP_SPR_BIG_RING).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState state) {
        var before = p.placement().capture(); p.placement().pool().write(state);
        assertEquals(before.status(), p.placement().status()); assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var old : before.pool().slots()) if (old.identity().wholeIndex() != state.identity().wholeIndex()) assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void sameChunk(Prepared p) throws Exception { assertEquals(presentation(p).pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord()); }
    private static void assertLowRings(Prepared p) { assertTrue(p.harness().module().pastLoadState().ring().rings() < 50, "Use the genuine public Ring owner; do not seed a count"); }
    private static SonicCdPlayerPresentation presentation(Prepared p) throws Exception { return (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation"); }
    private static long calls(Prepared p) { return p.harness().module().pastLoadState().calls().stream().filter(c -> c == SonicCdGameModule.PastLoadCall.BIG_RING_BODY).count(); }
    private static List<SonicCdGameModule.PastBigRingFm> bigRingSounds(Prepared p) { return p.harness().module().pastLoadState().sounds().stream()
            .filter(SonicCdGameModule.PastBigRingFm.class::isInstance).map(SonicCdGameModule.PastBigRingFm.class::cast).toList(); }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }

    /** Healthy control step and restore; compare the real ordinary owners, never a fabricated expected actor. */
    private record Baseline(Object actor, Object shared, Object program, Object scroll, Object values, Object sounds,
                            CdObjectPlacementRuntime.Snapshot placement) {
        void assertOtherOwners(Prepared p, boolean sameSat) throws Exception {
            same(actor, p.frame().captureActor()); same(shared, p.frame().captureShared());
            same(program, p.harness().module().levelProgramState()); same(scroll, presentation(p).capturePastScroll());
            same(sounds, actualField(p.visit(), "sounds"));
            for (var component : values.getClass().getRecordComponents()) {
                if (component.getName().equals("calls") || !sameSat && component.getName().equals("cpuSat")) continue;
                var accessor = component.getAccessor(); accessor.setAccessible(true);
                if (!sameSat && component.getName().equals("loop")) {
                    var expectedLoop = accessor.invoke(values);
                    var actualLoop = accessor.invoke(actualField(p.visit(), "values"));
                    for (var field : expectedLoop.getClass().getRecordComponents()) {
                        if (field.getName().equals("completedDraw")) continue;
                        var read = field.getAccessor(); read.setAccessible(true);
                        same(read.invoke(expectedLoop), read.invoke(actualLoop));
                    }
                    var completed = (Optional<?>) actualField(actualLoop, "completedDraw");
                    var actualCpu = assertInstanceOf(CdObjectDrawQueue.CpuSat.class, completed.orElseThrow());
                    var cpuBytes = (CdAssetBytes) actualField(actualField(p.visit(), "values"), "cpuSat");
                    assertArrayEquals(cpuBytes.copy(), actualCpu.bytes(), "Both current CPU projections include the genuine flash Draw");
                    assertTrue(actualCpu.provenance().stream().anyMatch(v -> v.slotRef().wholeIndex() == 127));
                    continue;
                }
                same(accessor.invoke(values), accessor.invoke(actualField(p.visit(), "values")));
            }
            assertEquals(placement.status(), p.placement().status());
            for (var old : placement.pool().slots()) if (old.identity().wholeIndex() != 127)
                assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()), "Other actual physical image " + old.identity().wholeIndex());
        }
    }
    private static Baseline baseline(Prepared p) throws Exception {
        var registry = p.harness().mode().getRewindRegistry(); var full = registry.capture(); var before = captureNative(p.harness());
        step(p.harness(), neutral()); healthy(p);
        var result = new Baseline(p.frame().captureActor(), p.frame().captureShared(), p.harness().module().levelProgramState(),
                presentation(p).capturePastScroll(), actualField(p.visit(), "values"), actualField(p.visit(), "sounds"), p.placement().capture());
        registry.restore(full); assertNativeCapture(before, p.harness()); healthy(p); return result;
    }
    private static void same(Object expected, Object actual) throws Exception {
        if (expected == actual) return; assertNotNull(expected); assertNotNull(actual); assertEquals(expected.getClass(), actual.getClass());
        if (expected instanceof CdAssetBytes bytes) assertArrayEquals(bytes.copy(), ((CdAssetBytes) actual).copy());
        else if (expected instanceof CdObjectDrawQueue.CpuSat sat) {
            var other = (CdObjectDrawQueue.CpuSat) actual; assertArrayEquals(sat.bytes(), other.bytes()); assertEquals(sat.spriteCount(), other.spriteCount()); assertEquals(sat.provenance(), other.provenance());
        } else if (expected instanceof Optional<?> option) { var other = (Optional<?>) actual; assertEquals(option.isPresent(), other.isPresent()); if (option.isPresent()) same(option.orElseThrow(), other.orElseThrow()); }
        else if (expected instanceof List<?> list) { var other = (List<?>) actual; assertEquals(list.size(), other.size()); for (int i = 0; i < list.size(); i++) same(list.get(i), other.get(i)); }
        else if (expected instanceof Map<?,?> map) { var other = (Map<?,?>) actual; assertEquals(map.keySet(), other.keySet()); for (var key : map.keySet()) same(map.get(key), other.get(key)); }
        else if (expected.getClass().isArray()) { assertEquals(Array.getLength(expected), Array.getLength(actual)); for (int i = 0; i < Array.getLength(expected); i++) same(Array.get(expected, i), Array.get(actual, i)); }
        else if (expected.getClass().isRecord()) for (var component : expected.getClass().getRecordComponents()) { var accessor = component.getAccessor(); accessor.setAccessible(true); same(accessor.invoke(expected), accessor.invoke(actual)); }
        else assertEquals(expected, actual);
    }
}
