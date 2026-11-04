package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Boulder/Main.asm:7-51,
 * Object Solid.asm:15-146 and Object Tile ID Set.asm:7-17. Actual USA B assets
 * supply map2094AC and selector11 WORD0346; no art, PLC or Frame is constructed.
 * Component Solid publications below are explicit public-format owner stand-ins,
 * not an actual Frame/World or private producer-authority proof. Solid uses its
 * real shared algorithms and real format-decoder probes; expected values never
 * reexecute Boulder/Solid. The small malformed operand test exercises the pure
 * package format seam, not a forged authenticated CdR11bAssets or a World visit.
 */
class TestCdBoulderPastFactory {
    private static final int SLOT = 40, MAP = 0x2094AC, B_TILE = 0x346;
    private static CdR11bAssets bAssets;
    private static CdR11aAssets aAssets;

    @Test void pastFactoryAddsOnlyTheNamedFiveArgumentPrefixPathAndRetainsLegacyShape() throws Exception {
        assertNotNull(CdBoulder.class.getMethod("fromPastAssets", CdObjectPlacementRuntime.class,
                CdR11bAssets.class, CdSolidObject.class, CdBoulder.PrefixSolidCommit.class, CdBoulder.DrawObjectCall.class));
        assertEquals(1, CdBoulder.class.getConstructors().length);
        assertEquals(5, CdBoulder.class.getConstructors()[0].getParameterCount());
        assertEquals(3, CdBoulder.Assets.class.getRecordComponents().length);
        assertEquals(4, CdBoulder.SolidCall.class.getRecordComponents().length);
        assertEquals(4, CdBoulder.PrefixSolidCall.class.getRecordComponents().length);
        assertNotNull(CdBoulder.class.getMethod("run", int.class, CdBoulder.Globals.class, CdBoulder.SolidInputs.class));
        assertEquals("placement", assertThrows(NullPointerException.class,
                () -> new CdBoulder(null, null, null, null, null)).getMessage());
        var placement = new CdObjectPlacementRuntime(past().placements());
        assertEquals("assets", assertThrows(NullPointerException.class,
                () -> new CdBoulder(placement, null, null, null, null)).getMessage());
        var a = CdBoulder.Assets.fromObjectAssets(present().openingObjects());
        assertEquals(74, a.objectTileIds().length());
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Assets(
                past().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes(), a.visual(), a.mappingsAddress()));
        assertThrows(NullPointerException.class,
                () -> CdBoulder.fromPastAssets(placement, null, null, null, null));
    }

    @Test void actualB72SelectorAndMappingProduceTheLiteralCompleteDirtyInitImage() throws Exception {
        var h = new H(); h.put(h.state().moveByte(SPRITE_FLAGS, 0x63).moveByte(FLAGS, 0xA5)
                .moveByte(COLLISION_TYPE, 0x77).moveByte(SOLID_TYPE, 0x91).moveByte(MAP_FRAME, 7)
                .moveWord(X_FRACTION, 0x1234).moveWord(Y_FRACTION, 0xFEDC)
                .moveWord(X_VELOCITY, 0x3456).moveWord(Y_VELOCITY, 0x9876).moveLong(TAMABBOH_X_DELTA, 0x89ABCDEF));
        var before = h.placement.capture(); var expected = init(h.state(), true);
        var result = h.body.run(SLOT, world(), null);
        assertArrayEquals(expected, result.state().copyBytes()); assertTrue(h.calls.isEmpty());
        assertEquals(MAP, result.state().longBits(MAPPINGS)); assertEquals(B_TILE, result.state().word(TILE));
        assertEquals(List.of(new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.INIT),
                new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.MAIN),
                new CdBoulder.CallReturned(CdBoulder.NativeCall.DRAW_OBJECT),
                new CdBoulder.DespawnChecked(false)), result.effects());
        assertEquals(1, h.draws.size()); assertOtherSlots(before, h, SLOT);
        assertArrayEquals(before.savedFlags().copy(), h.placement.capture().savedFlags().copy());
        assertEquals(72, past().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes().length());
        assertEquals(14, past().view(CdR11bAssets.Asset.MAP_SPR_BOULDER).bytes().length());
    }

    @Test void everyNonzeroSecondaryStopsAfterInitHeaderAndBeforeTileSolidDrawOrTail() throws Exception {
        var h = new H();
        for (int secondary = 1; secondary <= 255; secondary++) {
            h.put(h.state().moveByte(ROUTINE, 0).moveByte(SUBTYPE2, secondary).moveByte(SPRITE_FLAGS, 0x84)
                    .moveWord(TILE, 0xBEEF).moveLong(MAPPINGS, 0x89ABCDEF));
            byte[] expected = init(h.state(), false); var cut = h.placement.capture();
            var failure = assertThrows(CdBoulder.UnsupportedBoulder.class, h::run);
            assertEquals(CdBoulder.UnsupportedBoundary.TILE_ID_SUBTYPE2, failure.boundary());
            assertEquals(SLOT, failure.wholeIndex()); assertArrayEquals(expected, h.state().copyBytes());
            assertTrue(h.calls.isEmpty()); assertTrue(h.draws.isEmpty()); assertOtherSlots(cut, h, SLOT);
        }
    }

    @Test void reachedBFormatReadUsesSignedRelativeWordAndExactTwoByteBoundsWithoutPadding() throws Exception {
        var original = past().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
        assertEquals(0x3A, original.unsignedWord(22)); assertEquals(B_TILE, CdBoulder.readPastTileWord(original, 0));
        for (int size : new int[]{0, 21, 22, 23})
            assertThrows(IndexOutOfBoundsException.class,
                    () -> CdBoulder.readPastTileWord(CdAssetBytes.copyOf(new byte[size]), 0));
        for (int target : new int[]{-2, -1, 71, 72, 0x7FFE}) {
            byte[] bytes = original.copy(); word(bytes, 22, target);
            assertThrows(IndexOutOfBoundsException.class,
                    () -> CdBoulder.readPastTileWord(CdAssetBytes.copyOf(bytes), 0));
        }
        byte[] shortBody = new byte[24]; word(shortBody, 22, 24);
        assertThrows(IndexOutOfBoundsException.class,
                () -> CdBoulder.readPastTileWord(CdAssetBytes.copyOf(shortBody), 0));
        assertThrows(IndexOutOfBoundsException.class, () -> CdBoulder.readPastTileWord(original, 255));
        assertThrows(IllegalArgumentException.class, () -> CdBoulder.readPastTileWord(original, -1));
        assertThrows(IllegalArgumentException.class, () -> CdBoulder.readPastTileWord(original, 256));
        assertEquals(B_TILE, CdBoulder.readPastTileWord(original, 0));
    }

    @Test void nonnegativeMainStillSkipsEverySolidInputAndBothCallbackKinds() throws Exception {
        var h = new H(); h.main(); h.put(h.state().moveByte(SPRITE_FLAGS, 0x63).moveByte(SOLID_TYPE, 0xE7));
        var before = h.state(); var result = h.body.run(SLOT, world(), null);
        assertEquals(before, result.state()); assertEquals(1, h.draws.size()); assertTrue(h.calls.isEmpty());
    }

    @Test void actualBMovementOwnerDebugBranchPublishesTheSameCurrentCutWithoutTouchingOtherSlots() throws Exception {
        var h = new H(new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromPast(past()))));
        h.main(); h.debug = 0x100; h.put(h.state().moveByte(SOLID_TYPE, 0xA5)); var before = h.placement.capture();
        var result = h.run(); var call = only(h);
        var solid = assertInstanceOf(CdSolidObject.Completed.class, call.outcome()).completedResult();
        assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE), solid.getOffReason());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, solid.entry()); assertEquals(0, result.state().unsignedByte(SOLID_TYPE));
        assertSame(solid, assertInstanceOf(CdBoulder.Solid.class, result.effects().get(1)).result());
        assertEquals(call.expectedPlayer(), h.livePlayer); assertEquals(call.expectedGlobals(), h.liveGlobals);
        assertEquals(before.pool().slots(), call.callCut().slots()); assertOtherSlots(before, h, SLOT);
    }

    @Test void genuineCompletedSidePushHasLiteralPlayerAndSourceWritesThenExactlyOneDraw() throws Exception {
        var h = new H(); h.main(); h.p.x = 0x0FE78000; h.p.y = 0x02001234;
        h.p.flags = 0; h.p.xv = 0x600; h.p.yv = 0; h.p.gv = 0x333;
        var before = h.placement.capture(); var result = h.run();
        var solid = assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()).completedResult();
        var expected = h.p.copy(); expected.x = 0x0FE68000; expected.xv = 0; expected.gv = 0;
        expected.flags = 0x20; expected.push = SLOT;
        assertEquals(expected.player(), h.livePlayer); assertEquals(CdSolidObject.Path.HALT_ON_X, solid.path());
        assertEquals(OptionalInt.of(0), solid.d0());
        assertEquals(before.pool().slots().get(SLOT - 32).moveByte(FLAGS, 0x20), solid.object());
        assertEquals(solid.object().moveByte(SPRITE_FLAGS, 4), result.state());
        assertEquals(1, h.calls.size()); assertEquals(List.of(solid.object()), h.draws);
        assertOtherSlots(before, h, SLOT);
    }

    @Test void genuineCompletedTopLandingCarriesLiteralPlayerAndGlobalNonwrites() throws Exception {
        var h = new H(); h.main(); var before = h.placement.capture(); var result = h.run();
        var solid = assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()).completedResult();
        var expected = h.p.copy(); expected.y = 0x01DD1234; expected.yv = 0; expected.gv = h.p.xv;
        expected.angle = 0; expected.flags = 8; expected.jump = 0; expected.stand = SLOT;
        assertEquals(expected.player(), h.livePlayer); assertEquals(CdSolidObject.Path.STAND_ON, solid.path());
        assertEquals(OptionalInt.of(1), solid.d0()); assertEquals(h.globals(), h.liveGlobals);
        assertEquals(before.pool().slots().get(SLOT - 32).moveByte(FLAGS, 8), solid.object());
        assertEquals(solid.object().moveByte(SPRITE_FLAGS, 4), result.state());
        assertEquals(List.of(new CdSolidObject.FloorDistance(31, OptionalInt.empty(), OptionalInt.empty()),
                new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT)), solid.calls());
        assertOtherSlots(before, h, SLOT);
    }

    @Test void completedDynamicStandReleasePublishesOnlyItsGenuineSecondImage() throws Exception {
        var h = new H(); h.main(); h.p.flags = 8; h.p.stand = 41; h.p.yv = 0;
        h.put(h.pool().state(41).moveByte(ID, 0).moveByte(FLAGS, 8).moveByte(SUBTYPE, 0xC7));
        var before = h.placement.capture(); h.run();
        var solid = assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()).completedResult();
        assertEquals(1, solid.otherSlots().size());
        assertEquals(before.pool().slots().get(9).moveByte(FLAGS, 0), solid.otherSlots().getFirst());
        assertEquals(solid.otherSlots().getFirst(), h.pool().state(41)); assertEquals(SLOT, h.livePlayer.standObject());
        assertOtherSlots(before, h, SLOT, 41);
    }

    @Test void genuineStoppedStandReleaseIsCommittedBeforeTheIdenticalOriginalExceptionAndHasNoLaterWork() throws Exception {
        for (int stand : new int[]{1, 5, 31, 128, 255}) {
            var h = new H(); h.main(); h.p.flags = 8; h.p.stand = stand; h.p.yv = 0;
            h.put(h.state().moveByte(SOLID_TYPE, 2)); var before = h.placement.capture();
            var failure = assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
            var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
            assertSame(stopped.stop().originalException(), failure); assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stopped.stop().site());
            assertEquals(stand, assertInstanceOf(CdSolidObject.SlotAddress.class, stopped.stop().detail()).slotByte());
            assertEquals(stopped.prefix().object(), h.state()); assertEquals(8, h.state().flags());
            assertEquals(0, h.state().unsignedByte(SOLID_TYPE)); assertEquals(0x84, h.state().spriteFlags());
            var expected = h.p.copy(); expected.jump = 0;
            assertEquals(expected.player(), h.livePlayer); assertEquals(h.globals(), h.liveGlobals);
            assertTrue(h.draws.isEmpty()); assertOtherSlots(before, h, SLOT);
        }
    }

    @Test void genuineUnavailableFloorCommitsItsRealPrefixAndPreservesAllUnwrittenImages() throws Exception {
        var terrain = new Terrain(); terrain.fg[2 + 42 + 16] = (byte) 0xF9;
        var h = new H(terrain.owner()); h.main(); h.p.spr = 0x44;
        h.put(h.state().moveByte(SOLID_TYPE, 0xA7)); var before = h.placement.capture();
        var failure = assertThrows(IllegalStateException.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
        assertSame(stopped.stop().originalException(), failure); assertEquals(CdSolidObject.StopSite.FLOOR_PRIMARY, stopped.stop().site());
        assertEquals(before.pool().slots().get(SLOT - 32), stopped.prefix().object());
        assertEquals(stopped.prefix().object(), h.state()); assertEquals(stopped.prefix().player(), h.livePlayer);
        assertEquals(stopped.prefix().globals(), h.liveGlobals); assertTrue(stopped.prefix().calls().isEmpty());
        assertTrue(h.draws.isEmpty()); assertOtherSlots(before, h, SLOT);
    }

    @Test void returningNoPublicationOrAnUnrelatedPoolWriteCannotPassTheFull96OutputCheck() throws Exception {
        var noop = new H(); noop.main(); noop.debug = 0x100;
        noop.put(noop.state().moveByte(SOLID_TYPE, 0xA5)); noop.publish = false;
        assertThrows(IllegalStateException.class, noop::run); assertEquals(1, noop.calls.size()); assertTrue(noop.draws.isEmpty());
        var corrupt = new H(); corrupt.main(); corrupt.debug = 0x100;
        corrupt.before = call -> corrupt.pool().write(corrupt.pool().state(69).moveByte(SUBTYPE, 0xA5));
        assertThrows(IllegalStateException.class, corrupt::run); assertEquals(1, corrupt.calls.size()); assertTrue(corrupt.draws.isEmpty());
    }

    @Test void callbackReentryRejectsAndAnOriginalCallbackFaultRetainsOnlyItsPublishedPrefix() throws Exception {
        var h = new H(); h.main(); h.debug = 0x100; h.put(h.state().moveByte(SOLID_TYPE, 0xA5));
        h.before = call -> assertThrows(IllegalStateException.class, () -> h.body.run(SLOT, world(), h.inputs()));
        h.run(); assertEquals(1, h.calls.size()); assertEquals(1, h.draws.size());
        var failed = new H(); failed.main(); failed.debug = 0x100; failed.put(failed.state().moveByte(SOLID_TYPE, 0xA5));
        var fault = new Fault(); failed.before = call -> {
            var result = assertInstanceOf(CdSolidObject.Completed.class, call.outcome()).completedResult();
            failed.pool().write(result.object()); throw fault;
        };
        assertSame(fault, assertThrows(Fault.class, failed::run)); assertEquals(0, failed.state().unsignedByte(SOLID_TYPE));
        assertTrue(failed.draws.isEmpty());
        failed.before = call -> { }; failed.run(); assertEquals(2, failed.calls.size()); assertEquals(1, failed.draws.size());
    }

    @Test void throwingCallbackPoolCorruptionIsReportedWithoutReplacingItsOriginalFailure() throws Exception {
        var h = new H(); h.main(); h.debug = 0x100; var fault = new Fault();
        h.before = call -> { h.pool().write(h.pool().state(69).moveByte(SUBTYPE, 0xA5)); throw fault; };
        assertSame(fault, assertThrows(Fault.class, h::run));
        assertEquals(1, fault.getSuppressed().length); assertInstanceOf(IllegalStateException.class, fault.getSuppressed()[0]);
        assertTrue(h.draws.isEmpty());
    }

    @Test void stoppedCallbackFailureWinsWithoutDrawingOrSubstitutingANativeReturn() throws Exception {
        var h = new H(); h.main(); h.p.flags = 8; h.p.stand = 1; h.p.yv = 0;
        var fault = new Fault(); h.before = call -> { throw fault; };
        assertSame(fault, assertThrows(Fault.class, h::run));
        assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome()); assertTrue(h.draws.isEmpty());
    }

    @Test void drawFailureKeepsTheRealCompletedSolidPublicationAndRetiresBodyReentry() throws Exception {
        var h = new H(); h.main(); var fault = new Fault(); h.drawFault = fault;
        assertSame(fault, assertThrows(Fault.class, h::run));
        var solid = assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()).completedResult();
        assertEquals(solid.player(), h.livePlayer); assertEquals(solid.object().moveByte(SPRITE_FLAGS, 4), h.state());
        h.drawFault = null; h.debug = 0x100; h.run(); assertEquals(1, h.calls.size()); // BCLR7 now bypasses Solid.
    }

    @Test void actualPresentFactoryAndLegacyCompletedCallbackKeepTheA74OperandAndSemantics() throws Exception {
        var a = CdBoulder.Assets.fromObjectAssets(present().openingObjects());
        var placement = new CdObjectPlacementRuntime(present().placements());
        var pool = placement.pool(); pool.write(pool.state(SLOT).moveByte(ID, 0x1B).moveWord(X, 0x1000).moveWord(Y, 0x200));
        var calls = new ArrayList<CdBoulder.SolidCall>();
        var body = new CdBoulder(placement, a, CdSolidObject.fromAssets(present()), call -> {
                    calls.add(call); pool.write(call.result().object()); call.result().otherSlots().forEach(pool::write);
                },
                (p, identity) -> p.write(p.state(identity.wholeIndex()).moveByte(SPRITE_FLAGS, 4)));
        var result = body.run(SLOT, new CdBoulder.Globals(1, 0, 0x1080), null);
        assertEquals(74, a.objectTileIds().length()); assertEquals(0x209654, result.state().longBits(MAPPINGS));
        assertEquals(0x374, result.state().word(TILE)); assertTrue(calls.isEmpty());
        pool.write(pool.state(SLOT).moveByte(SPRITE_FLAGS, 0x84).moveByte(SOLID_TYPE, 0xA5));
        var completed = body.run(SLOT, new CdBoulder.Globals(1, 0, 0x1080), new CdBoulder.SolidInputs(
                new P().player(), new CdSolidObject.Gates(0x100, 0), new CdSolidObject.Globals(0, 4, 0x12, 0x34)));
        assertEquals(1, calls.size()); assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE), calls.getFirst().result().getOffReason());
        assertEquals(0, completed.state().unsignedByte(SOLID_TYPE)); assertEquals(4, completed.state().spriteFlags());
    }

    private static CdBoulder.Globals world() { return new CdBoulder.Globals(0, 0, 0x1080); }
    private static CdBoulder.PrefixSolidCall only(H h) { assertEquals(1, h.calls.size()); return h.calls.getFirst(); }
    private static byte[] init(CdObjectState before, boolean tile) {
        byte[] b = before.copyBytes(); b[0x24] += 2; b[1] |= 4; b[0x18] = 4;
        ByteBuffer.wrap(b).putInt(4, MAP); b[0x19] = 0x10; b[0x16] = 0x10; b[0x1A] = 0;
        if (tile) word(b, 2, B_TILE); return b;
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void assertOtherSlots(CdObjectPlacementRuntime.Snapshot before, H h, int... writes) {
        for (var source : before.pool().slots()) {
            if (Arrays.stream(writes).noneMatch(index -> index == source.identity().wholeIndex()))
                assertEquals(source, h.pool().state(source.identity().wholeIndex()));
        }
        assertEquals(before.status(), h.placement.status());
        assertArrayEquals(before.savedFlags().copy(), h.placement.capture().savedFlags().copy());
    }
    private static CdR11bAssets past() throws Exception {
        if (bAssets == null) try (var source = source()) { bAssets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)); }
        return bAssets;
    }
    private static CdR11aAssets present() throws Exception {
        if (aAssets == null) try (var source = source()) { aAssets = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A")); }
        return aAssets;
    }
    private static CdProgramAssets source() throws Exception {
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Actual-USA cases require soniccd.media.root");
        assertFalse(root.isBlank()); Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        return CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty());
    }
    private static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }

    private static final class H {
        final CdObjectPlacementRuntime placement;
        final CdBoulder body;
        final P p = new P();
        final List<CdBoulder.PrefixSolidCall> calls = new ArrayList<>();
        final List<CdObjectState> draws = new ArrayList<>();
        final List<CdPlayerTouchResponse.FmSound> sounds = new ArrayList<>();
        CdSolidObject.Player livePlayer;
        CdSolidObject.Globals liveGlobals;
        int debug;
        boolean publish = true;
        Fault drawFault;
        Consumer<CdBoulder.PrefixSolidCall> before = call -> { };
        H() throws Exception { this(new Terrain().owner()); }
        H(CdSolidObject solid) throws Exception {
            placement = new CdObjectPlacementRuntime(past().placements());
            put(state().moveByte(ID, 0x1B).moveWord(X, 0x1000).moveWord(Y, 0x200).moveByte(SPRITE_FLAGS, 3));
            body = CdBoulder.fromPastAssets(placement, past(), solid, this::commit, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState state() { return pool().state(SLOT); }
        void put(CdObjectState state) { pool().write(state); }
        void main() { put(state().moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveByte(WIDTH, 0x10)
                .moveByte(Y_RADIUS, 0x10).moveLong(MAPPINGS, MAP).moveWord(TILE, B_TILE).moveByte(PRIORITY, 4)); }
        CdSolidObject.Globals globals() { return new CdSolidObject.Globals(0, 4, 0x12, 0x34); }
        CdBoulder.SolidInputs inputs() { return new CdBoulder.SolidInputs(p.player(), new CdSolidObject.Gates(debug, 0), globals()); }
        CdBoulder.Result run() {
            var inputs = inputs(); livePlayer = inputs.player(); liveGlobals = inputs.globals(); return body.run(SLOT, world(), inputs);
        }
        void commit(CdBoulder.PrefixSolidCall call) {
            calls.add(call); pool().validateRestore(call.callCut());
            assertEquals(call.callCut().slots(), pool().capture().slots(), "Real pure helper is nonmutating before callback");
            assertEquals(livePlayer, call.expectedPlayer()); assertEquals(liveGlobals, call.expectedGlobals());
            before.accept(call); if (!publish) return;
            CdObjectState object; List<CdObjectState> others;
            if (call.outcome() instanceof CdSolidObject.Completed completed) {
                var r = completed.completedResult(); object = r.object(); others = r.otherSlots(); livePlayer = r.player(); liveGlobals = r.globals(); sounds.addAll(r.fmRequests());
            } else {
                var r = ((CdSolidObject.NativeStopped) call.outcome()).prefix(); object = r.object(); others = r.otherSlots(); livePlayer = r.player(); liveGlobals = r.globals(); sounds.addAll(r.fmRequests());
            }
            var all = new ArrayList<CdObjectState>(); all.add(object); all.addAll(others);
            var ready = CdObjectPool.overlay(call.callCut(), all); pool().validateRestore(ready);
            all.forEach(pool()::write);
        }
        void draw(CdObjectPool pool, CdObjectState.Identity identity) {
            assertSame(pool(), pool); var state = pool.state(identity.wholeIndex()); draws.add(state);
            pool.write(state.moveByte(SPRITE_FLAGS, state.spriteFlags() & 0x7F));
            if (drawFault != null) throw drawFault;
        }
    }
    /** Explicit component player projection, copied from the existing Boulder test format. */
    private static final class P {
        int x = 0x10008000, y = 0x01DD1234, xv = 0x123, yv = 0x100, gv, flags = 2, jump = 1, stand, push, angle = 0x10;
        int spr = 4;
        P copy() { var p = new P(); p.x = x; p.y = y; p.xv = xv; p.yv = yv; p.gv = gv; p.flags = flags;
            p.jump = jump; p.stand = stand; p.push = push; p.spr = spr; p.angle = angle; return p; }
        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                    2, angle, flags, spr, 9, 0x13, 0, 0, jump, 0, 0, 0, 0, 0x56, 0x78), 0x780, 2, 0, 0), 1, push, stand);
        }
    }
    /** Real existing A-format collision fixture, not media or a replacement for actual B terrain. */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0;
        }
        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }
}
