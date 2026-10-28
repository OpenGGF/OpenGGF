package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdTimeWarpLoadData;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.openggf.game.soniccd.objects.CdTimeWarpLoadData.NativeStore.*;
import static com.openggf.game.soniccd.objects.CdTimeWarpLoadData.StoreTarget.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Addressed Frame fields in Load Saved Data.asm:7-40 at N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Explicit dirty component inputs and actual USA source binding, not a completed destination load.
 * Invocation/phase/order and all other scalar owners remain with the actual private load caller.
 */
class TestCdTimeWarpFrameStores {
    private static CdR11bAssets assets;
    private static final Runnable NOTHING = () -> { };
    private static final List<CdTimeWarpLoadData.NativeStore> OWNED = List.of(
            RESTORE_X, RESTORE_Y, RESTORE_FLAGS, RESTORE_GVEL, RESTORE_X_VELOCITY,
            RESTORE_Y_VELOCITY, RESTORE_BOTTOM, RESTORE_MINI, SET_LEFT_BOUND);

    @BeforeAll
    static void originalUsaBinding() throws Exception {
        var root = System.getProperty("soniccd.media.root");
        assumeTrue(root != null && !root.isBlank(), "Requires configured original-USA media");
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void nineAddressedFieldsExposeRawByteOrWordPreimagesWithoutMutation() {
        var f = frame(0, 0x80); var a = f.captureActor(); var s = f.captureShared();
        int[] values = {0x1234, 0x2345, 0x45, 0xCBAA, 0xEDCC, 0x2345, 0x3456, 0x12, 0x0012};
        for (int i = 0; i < OWNED.size(); i++) assertEquals(values[i], f.readTimeWarpLoadField(OWNED.get(i).target()));
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared());
    }

    @Test
    void everyAddressedStorePreservesAllOtherFullPlayerAndSharedComponents() {
        for (var instruction : OWNED) for (int candidate : new int[]{0, 0x7F, 0x80, 0xFF, 0x7FFF, 0x8000, 0xFFFF}) {
            int value = candidate & (instruction.target().width() == 1 ? 0xFF : 0xFFFF);
            var f = frame(0, 0x80); var a = f.captureActor(); var s = f.captureShared();
            var source = f.stageSource();
            f.bindArtPublication((request, art) -> fail("Saved field writes have no art service"));
            f.applyTimeWarpLoadStore(call(f, instruction, value));
            assertEquals(expectedState(a.state(), instruction.target(), value), f.state(), instruction.name());
            assertEquals(expectedEnvironment(s.environment(), instruction.target(), value), f.environment(), instruction.name());
            assertEquals(value, f.readTimeWarpLoadField(instruction.target()));
            assertSame(a.owner(), f.captureActor().owner()); assertSame(s.owner(), f.captureShared().owner());
            assertSame(a.display(), f.display()); assertSame(s.artState(), f.artState());
            assertSame(s.vIntClock(), f.vIntClock()); assertEquals(s.paused(), f.paused());
            assertSame(source, f.stageSource()); assertFalse(f.closed());
        }
    }

    @Test
    void wordPositionWritesPreserveFractionsAndVelocityWordsKeepTheirSignedRepresentation() {
        var f = frame(0, 0x80);
        f.applyTimeWarpLoadStore(call(f, RESTORE_X, 0xFFFF));
        f.applyTimeWarpLoadStore(call(f, RESTORE_Y, 0x8000));
        f.applyTimeWarpLoadStore(call(f, RESTORE_X_VELOCITY, 0xFFFF));
        f.applyTimeWarpLoadStore(call(f, RESTORE_Y_VELOCITY, 0x8000));
        f.applyTimeWarpLoadStore(call(f, RESTORE_GVEL, 0x7FFF));
        assertEquals(0xFFFF5678, f.state().motion().centreX16_16());
        assertEquals(0x80006789, f.state().motion().centreY16_16());
        assertEquals(-1, f.state().motion().xVelocity()); assertEquals(-32768, f.state().motion().yVelocity());
        assertEquals(32767, f.state().motion().groundVelocity());
    }

    @Test
    void unknownAndStalePreimagesRejectBeforeAnyOwnerChanges() {
        for (var instruction : OWNED) {
            var f = frame(0, 0x80); var a = f.captureActor(); var s = f.captureShared();
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.empty(), 0)));
            int old = f.readTimeWarpLoadField(instruction.target());
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(old ^ 1), 0)));
            assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared());
        }
    }

    @Test
    void externalRingCameraAndWaterTargetsStayWithTheirExistingOwners() {
        for (var instruction : CdTimeWarpLoadData.NativeStore.values()) if (!OWNED.contains(instruction)) {
            var f = frame(0, 0x80); var a = f.captureActor(); var s = f.captureShared();
            assertThrows(IllegalArgumentException.class, () -> f.readTimeWarpLoadField(instruction.target()));
            assertThrows(IllegalArgumentException.class, () -> f.applyTimeWarpLoadStore(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(0), 0)));
            assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared());
        }
    }

    @Test
    void realSavedHelperRestoresSixPlayerFieldsThenBoundsAtTheReachedNativeStores() {
        var f = frame(0, 0x80); var history = f.state().history(); var clock = f.vIntClock();
        var saved = suppliedSaved(-1); var port = new ActualPort(f, saved, 0, 2);
        var result = CdTimeWarpLoadData.load(saved, port);
        assertEquals(List.of(RESTORE_X, RESTORE_Y, RESTORE_FLAGS, RESTORE_GVEL, RESTORE_X_VELOCITY,
                RESTORE_Y_VELOCITY, RESTORE_RINGS, RESTORE_LIVES_FLAGS, RESTORE_TIME, RESTORE_WATER_ROUTINE,
                RESTORE_BOTTOM, RESTORE_DEST_BOTTOM, RESTORE_FG_X, RESTORE_FG_Y, RESTORE_BG_X, RESTORE_BG_Y,
                RESTORE_BG2_X, RESTORE_BG2_Y, RESTORE_BG3_X, RESTORE_BG3_Y), result.completedNativeStores());
        assertEquals(result.completedNativeStores(), port.completed);
        assertEquals(0x34565678, f.state().motion().centreX16_16());
        assertEquals(0xFEDC6789, f.state().motion().centreY16_16());
        assertEquals(0xC7, f.state().motion().flags());
        assertEquals(-32768, f.state().motion().groundVelocity());
        assertEquals(-1, f.state().motion().xVelocity()); assertEquals(0x1234, f.state().motion().yVelocity());
        assertEquals(0x0480, f.environment().movement().bounds().bottom());
        assertEquals(0x12, f.environment().movement().gates().miniSonic(), "PP never reads saved mini");
        assertEquals(0x12, f.environment().movement().bounds().left(), "unmarked spawn does not write left");
        assertSame(history, f.state().history()); assertSame(clock, f.vIntClock());
    }

    @Test
    void conditionalMiniAndSignedSpawnLeftStoresAreSelectedByTheSharedHelperOnly() {
        var f = frame(0, 0x80); var saved = suppliedSaved(-1); var port = new ActualPort(f, saved, 6, 0x80);
        CdTimeWarpLoadData.load(saved, port);
        assertEquals(List.of(RESTORE_MINI, SET_LEFT_BOUND), port.completed.subList(20, 22));
        assertEquals(0xE1, f.environment().movement().gates().miniSonic());
        assertEquals(0x33B6, f.environment().movement().bounds().left());
        assertEquals(0, f.environment().movement().gates().zoneAct(), "readLive is explicit component input, no zone write");
    }

    @Test
    void missingSavedByteRetainsOnlyEarlierAdoptedFieldsWithoutRollback() {
        var f = frame(0, 0x80); var before = f.state();
        var saved = suppliedSaved(0xFF1566); var port = new ActualPort(f, saved, 0, 2);
        assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> CdTimeWarpLoadData.load(saved, port));
        assertEquals(List.of(RESTORE_X, RESTORE_Y, RESTORE_FLAGS, RESTORE_GVEL), port.completed);
        assertEquals(-32768, f.state().motion().groundVelocity());
        assertEquals(before.motion().xVelocity(), f.state().motion().xVelocity());
        assertEquals(before.motion().yVelocity(), f.state().motion().yVelocity());
        assertEquals(0x3456, f.environment().movement().bounds().bottom()); assertFalse(f.closed());
    }

    @Test
    void sameFrameSurvivesEarlierLoadWaitAndLaterSavedStoresWithoutAnExtraPulse() {
        var f = frame(0, 0x80); var actorOwner = f.captureActor().owner(); var sharedOwner = f.captureShared().owner();
        f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
        var clock = f.vIntClock(); var held = f.environment().controls();
        CdTimeWarpLoadData.load(suppliedSaved(-1), new ActualPort(f, suppliedSaved(-1), 0, 2));
        assertSame(clock, f.vIntClock()); assertEquals(held, f.environment().controls());
        assertSame(actorOwner, f.captureActor().owner()); assertSame(sharedOwner, f.captureShared().owner());
        assertTrue(f.display().isEmpty()); assertEquals(0xA5, f.state().header().slotId());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test
    void portsRejectBusyClosedAndWrongSourceBeforeObservingArguments() {
        var f = frame(0, 0x80);
        f.serviceLevelLoad(0, () -> {
            assertThrows(IllegalStateException.class, () -> f.readTimeWarpLoadField(null));
            assertThrows(IllegalStateException.class, () -> f.applyTimeWarpLoadStore(null));
        }, NOTHING, NOTHING);
        var wrong = frame(0, 1);
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> wrong.readTimeWarpLoadField(null)).boundary());
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> wrong.applyTimeWarpLoadStore(null)).boundary());
        f.close();
        assertThrows(IllegalStateException.class, () -> f.readTimeWarpLoadField(null));
        assertThrows(IllegalStateException.class, () -> f.applyTimeWarpLoadStore(null));
    }

    @Test
    void sameValueAndRestoredEqualValuesRemainValueChecksRatherThanInvocationAuthority() {
        var f = frame(0, 0x80); var a = f.captureActor(); var s = f.captureShared();
        var value = call(f, RESTORE_X, 0x1234);
        f.applyTimeWarpLoadStore(value); f.applyTimeWarpLoadStore(value);
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared());
        var change = call(f, RESTORE_X, 0x9876); f.applyTimeWarpLoadStore(change);
        f.restoreActor(a); f.restoreShared(s); f.applyTimeWarpLoadStore(change);
        assertEquals(0x98765678, f.state().motion().centreX16_16());
        assertEquals(s, f.captureShared());
    }

    @Test
    void clearFlowersEstablishesCounterZeroAfterEarlierLoadPulseOnTheSameFrame() {
        var f = frame(0, 0x80); var a = f.captureActor(); var s = f.captureShared();
        f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
        assertEquals(124, f.vIntClock().absoluteValue().orElseThrow());
        var state = f.state(); var environment = f.environment(); var art = f.artState();
        f.clearLevelStartVIntCounter();
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 0), f.vIntClock());
        assertSame(a.owner(), f.captureActor().owner()); assertSame(s.owner(), f.captureShared().owner());
        assertSame(state, f.state()); assertSame(environment, f.environment()); assertSame(art, f.artState());
        assertSame(a.display(), f.display()); assertFalse(f.paused()); assertFalse(f.closed());
        for (int i = 0; i < 22; i++) f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
        assertEquals(22, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0xFFFF, f.environment().globals().levelFrames(), "ordinary levelFrames is a different writer");
    }

    @Test
    void clearFlowersCounterWriterReplacesUnknownSeedWithoutInventingEarlierAbsoluteTime() {
        var f = frame(0, 0x80); var s = f.captureShared();
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), s.paused(),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0xFFFFFFFFL)));
        assertTrue(f.vIntClock().absoluteValue().isEmpty());
        f.clearLevelStartVIntCounter();
        assertEquals(OptionalLong.of(0), f.vIntClock().absoluteValue());
        assertEquals(0, f.vIntClock().deltaSinceAdmittedCut());
        assertEquals(s.environment(), f.environment()); assertEquals(s.artState(), f.artState());
    }

    @Test
    void clearFlowersCounterWriterKeepsTheExistingClosedCutAndSourceGuards() {
        var f = frame(0, 0x80);
        f.serviceLevelLoad(0, () -> assertThrows(IllegalStateException.class, f::clearLevelStartVIntCounter), NOTHING, NOTHING);
        var wrong = frame(0, 1);
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, wrong::clearLevelStartVIntCounter).boundary());
        var clock = f.vIntClock(); f.close();
        assertThrows(IllegalStateException.class, f::clearLevelStartVIntCounter); assertSame(clock, f.vIntClock());
    }

    private static CdTimeWarpLoadData.StoreCall call(CdPlayerFrameRuntime f, CdTimeWarpLoadData.NativeStore instruction, int value) {
        return new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(f.readTimeWarpLoadField(instruction.target())), value);
    }
    private static CdPlayerBody.State expectedState(CdPlayerBody.State before, CdTimeWarpLoadData.StoreTarget target, int value) {
        var m = before.motion();
        var next = new CdPlayerMotionState(target == PRIMARY_X ? value << 16 | 0x5678 : m.centreX16_16(),
                target == PRIMARY_Y ? value << 16 | 0x6789 : m.centreY16_16(),
                target == PRIMARY_X_VELOCITY ? (short) value : m.xVelocity(),
                target == PRIMARY_Y_VELOCITY ? (short) value : m.yVelocity(),
                target == PRIMARY_GVEL ? (short) value : m.groundVelocity(),
                0x12, 0x34, target == PRIMARY_FLAGS ? value : 0x45, 0x56, 0x67, 0x78,
                0x89, 0x9A, 0xAB, 0xBC, -0x123, 0xCD, 0xDE, 0xEF, 0xF0);
        return new CdPlayerBody.State(next, before.animation(), before.header(), before.timers(), before.history(),
                before.rotationDistance(), before.objectLinks(), before.rotationAngle());
    }
    private static CdPlayerBody.Environment expectedEnvironment(CdPlayerBody.Environment before,
                                                               CdTimeWarpLoadData.StoreTarget target, int value) {
        var m = before.movement(); var b = m.bounds(); var g = m.gates();
        var bounds = new CdPlayerMovement.Bounds(target == LEFT_BOUND ? value : b.left(), b.right(),
                target == BOTTOM_BOUND ? value : b.bottom(), b.bossFight());
        var gates = new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), target == MINI_SONIC ? value : g.miniSonic(),
                g.waterSlide(), g.windTunnel(), g.speedShoes(), g.timeWarpDirection());
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), bounds, gates, m.globals()),
                before.controls(), before.globals(), before.dynamicSlots());
    }
    private static CdPlayerFrameRuntime frame(int zone, int period) {
        byte[] h = new byte[256]; for (int i = 0; i < h.length; i++) h[i] = (byte) (i * 37 + 11);
        var state = new CdPlayerBody.State(new CdPlayerMotionState(0x12345678, 0x23456789, -0x1234, 0x2345, -0x3456,
                0x12, 0x34, 0x45, 0x56, 0x67, 0x78, 0x89, 0x9A, 0xAB, 0xBC, -0x123, 0xCD, 0xDE, 0xEF, 0xF0),
                new CdPlayerBody.AnimationCursor(0xAB, 0xBC, 0xCD), new CdPlayerBody.Header(0xA5, 0x12345678, 0xF234, 0x67, 0x89),
                new CdPlayerBody.Timers(0xABCD, 0xBCDE, 0xCDEF, 0xDEF0), new CdPlayerBody.History(CdAssetBytes.copyOf(h), 0xBEEF),
                0xF1, new CdPlayerBody.ObjectLinks(0xE2, 0xD3), 0xC4);
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78),
                new CdPlayerMovement.Gates(zone, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, 0xFFFF, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 0xA8, 0xD0, 0xA5),
                new CdPlayerBody.Globals(0xFFFF, 0x2345, 0x12, 0xFEDC, 0x4567, 0xABCD, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return CdPlayerFrameRuntime.fromPastEntry(assets, state, environment,
                new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0xE7, true),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 123));
    }
    private static CdTimeWarpSaveData.State suppliedSaved(int unknownAddress) {
        byte[] data = new byte[42], mask = new byte[42]; java.util.Arrays.fill(mask, (byte) 1);
        // Packed saved42 offsets are source-backed existing value format, not a RAM image.
        int[][] rows = {{4,0x34},{5,0x56},{6,0xFE},{7,0xDC},{8,0xC7},{9,0x04},{10,0x80},
                {31,0x80},{32,0},{33,0xFF},{34,0xFF},{35,0x12},{36,0x34},{41,0xE1}};
        for (var row : rows) data[row[0]] = (byte) row[1];
        if (unknownAddress == 0xFF1566) mask[33] = 0;
        return CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(data), CdAssetBytes.copyOf(mask));
    }
    private static final class ActualPort implements CdTimeWarpLoadData.Port {
        final CdPlayerFrameRuntime frame; final CdTimeWarpSaveData.State saved; final int zone, spawn;
        final EnumMap<CdTimeWarpLoadData.StoreTarget, Integer> external = new EnumMap<>(CdTimeWarpLoadData.StoreTarget.class);
        final List<CdTimeWarpLoadData.NativeStore> completed = new ArrayList<>();
        ActualPort(CdPlayerFrameRuntime frame, CdTimeWarpSaveData.State saved, int zone, int spawn) {
            this.frame = frame; this.saved = saved; this.zone = zone; this.spawn = spawn;
        }
        public CdTimeWarpSaveData.State currentSaved() { return saved; }
        public OptionalInt read(CdTimeWarpLoadData.StoreTarget target) {
            if (OWNED.stream().anyMatch(i -> i.target() == target)) return OptionalInt.of(frame.readTimeWarpLoadField(target));
            var value = external.get(target); return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
        public OptionalInt readLive(CdTimeWarpLoadData.LiveRead source) {
            return OptionalInt.of(source == CdTimeWarpLoadData.LiveRead.ZONE ? zone : spawn);
        }
        public void commit(CdTimeWarpLoadData.StoreCall call) {
            if (OWNED.contains(call.instruction())) frame.applyTimeWarpLoadStore(call);
            else external.put(call.target(), call.value());
            completed.add(call.instruction());
        }
    }
}
