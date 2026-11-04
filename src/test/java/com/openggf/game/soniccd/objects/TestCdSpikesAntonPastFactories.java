package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual-USA B factory/component witnesses; native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * _Objects/Spikes/Main.asm:19-83; Palmtree Panic/Objects/Anton/Main.asm:24-94;
 * Object Tile ID Set.asm:7-17. Explicit component pool/player ingress and public callback stand-ins
 * are not a live Frame, producer authority, route, art or pixel proof. Assets and terrain come from B.
 * Present constructors/records retain their A74 contract; shared legacy tests remain unchanged.
 * Spikes retains its existing completed-Solid/Hurt ports: stopped cross-owner publication is not added here.
 */
class TestCdSpikesAntonPastFactories {
    private static final int SLOT = 40, X0 = 0x1000, Y0 = 0x300;
    private static CdR11bAssets retained;

    @Test void spikesNamedFactoryPreservesPublicPresentShapeWithoutPublishing() throws Exception {
        assertNotNull(CdSpikes.class.getMethod("fromPastAssets", CdObjectPlacementRuntime.class, CdR11bAssets.class,
                CdSolidObject.class, CdSpikes.SolidCommit.class, CdSpikes.YRewindCommit.class,
                CdSpikes.HurtCommit.class, CdSpikes.DrawObjectCall.class));
        assertEquals(1, CdSpikes.class.getConstructors().length); assertEquals(7, CdSpikes.class.getConstructors()[0].getParameterCount());
        assertEquals(3, CdSpikes.BodyAssets.class.getRecordComponents().length);
        var h = new S(); assertEquals(h.initial, h.state()); assertTrue(h.draws.isEmpty()); assertTrue(h.solids.isEmpty());
        var b = past(); var visual = CdObjectVisualAssets.decodeStatic(b.view(CdR11bAssets.Asset.MAP_SPR_SPIKES).bytes().copy(), 1, 8);
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.BodyAssets(b.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes(), visual, 0x20C048));
        assertThrows(NullPointerException.class, () -> CdSpikes.fromPastAssets(h.placement, null, h.solid, c -> {}, c -> {}, c -> {}, (pool, id) -> {}));
    }

    @Test void spikesActualBInitPreservesEveryNonwriteAndVerticalFlipAcrossAllFourFlags() throws Exception {
        var h = new S();
        for (int flip = 0; flip < 4; flip++) {
            h.put(h.initial.moveByte(SPRITE_FLAGS, flip)); var before = h.state(); var cut = h.placement.capture();
            var result = h.run(0, 0);
            assertArrayEquals(spikesInit(before, true, true), result.state().copyBytes());
            assertEquals(0x320, result.state().word(TILE)); assertEquals(0x20C048, result.state().longBits(MAPPINGS));
            assertEquals(flip < 2 ? 0xB7 : 0x83, result.state().unsignedByte(COLLISION_TYPE));
            assertOtherSlots(cut, h.placement); assertTrue(h.solids.isEmpty()); assertFalse(result.deleted());
        }
    }

    @Test void spikesEveryNonzeroSecondaryKeepsTheReachedInitPrefixBeforeItsExistingBoundary() throws Exception {
        var h = new S();
        for (int secondary = 1; secondary <= 255; secondary++) {
            h.put(h.initial.moveByte(SUBTYPE2, secondary)); var before = h.state();
            var failure = assertThrows(CdSpikes.UnsupportedSpikes.class, () -> h.body.run(SLOT, sGlobals(), null));
            assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2, failure.boundary());
            assertArrayEquals(spikesInit(before, false, false), h.state().copyBytes());
        }
        assertTrue(h.solids.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void spikesPureBWordReadUsesSignedDirectoryAndActualBoundsWithoutPadding() throws Exception {
        var bytes = past().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
        assertEquals(72, bytes.length()); assertEquals(0x320, CdSpikes.readPastTileWord(bytes, 0));
        assertEquals(0x381, CdSpikes.readPastTileWord(bytes, 1)); // Format read only; body admission still rejects1.
        assertMalformed(bytes, 20, b -> CdSpikes.readPastTileWord(b, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> CdSpikes.readPastTileWord(bytes, 255));
        assertThrows(IllegalArgumentException.class, () -> CdSpikes.readPastTileWord(bytes, -1));
        assertThrows(IllegalArgumentException.class, () -> CdSpikes.readPastTileWord(bytes, 256));
    }

    @Test void spikesUnsupportedRoutineRejectsBeforeEveryInitWriteOrCallback() throws Exception {
        var h = new S();
        for (int routine : new int[]{1, 3, 4, 255}) {
            h.put(h.initial.moveByte(ROUTINE, routine)); var before = h.state();
            var failure = assertThrows(CdSpikes.UnsupportedSpikes.class, () -> h.body.run(SLOT, sGlobals(), null));
            assertEquals(CdSpikes.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary()); assertEquals(before, h.state());
        }
        assertTrue(h.draws.isEmpty()); assertTrue(h.solids.isEmpty());
    }

    @Test void spikesMainDoesNotEagerlyReadSecondaryOrMappingBytes() throws Exception {
        var h = new S(); h.put(h.initial.moveByte(ROUTINE, 2).moveByte(SUBTYPE2, 255).moveLong(MAPPINGS, 0x89ABCDEF));
        var before = h.state(); var result = h.run(0, 0);
        assertEquals(before, result.state()); assertTrue(h.solids.isEmpty()); assertEquals(1, h.draws.size());
    }

    @Test void spikesReachedDebugBranchUsesGenuineSharedBMovementAndCompletedPublication() throws Exception {
        var h = new S(); h.put(h.initial.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveByte(SOLID_TYPE, 0xA5));
        var before = h.placement.capture(); var result = h.run(Y0, 0x100);
        assertEquals(1, h.solids.size()); var call = h.solids.getFirst();
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, call.result().entry());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE), call.result().getOffReason());
        assertEquals(0, result.state().unsignedByte(SOLID_TYPE)); assertOtherSlots(before, h.placement);
        assertEquals(0, h.rewinds); assertEquals(0, h.hurts);
    }

    @Test void spikesDrawFailureAndReentryKeepActualPrefixAndRetireTheRunGuard() throws Exception {
        var h = new S(); h.reenter = true; h.drawFault = new Fault(); h.rewriteX = true;
        var caught = assertThrows(Fault.class, () -> h.run(0, 0)); assertSame(h.drawFault, caught);
        assertEquals(0x20C048, h.state().longBits(MAPPINGS)); assertEquals(0x320, h.state().word(TILE)); assertEquals(X0 + 1, h.state().centreX());
        h.drawFault = null; h.reenter = false; h.rewriteX = false;
        assertDoesNotThrow(() -> h.run(0, 0)); assertEquals(2, h.draws.size());
    }

    @Test void antonNamedFactoryPreservesPresentShapeWithoutReadingTerrainOrPublishing() throws Exception {
        assertNotNull(CdAnton.class.getMethod("fromPastAssets", CdObjectPlacementRuntime.class, CdR11bAssets.class,
                CdPlayerTerrain.class, CdAnton.PrimaryAngleCommit.class, CdAnton.DrawObjectCall.class));
        assertEquals(1, CdAnton.class.getConstructors().length); assertEquals(5, CdAnton.class.getConstructors()[0].getParameterCount());
        assertEquals(3, CdAnton.Assets.class.getRecordComponents().length);
        var h = new A(); assertEquals(h.initial, h.state()); assertTrue(h.draws.isEmpty()); assertTrue(h.angles.isEmpty());
        var b = past(); var visual = CdObjectVisualAssets.decode(b.view(CdR11bAssets.Asset.MAP_SPR_ANTON).bytes().copy(), 4,
                b.view(CdR11bAssets.Asset.ANI_ANTON).bytes().copy(), 2, 22);
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Assets(b.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes(), visual, 0x20B202));
        assertThrows(NullPointerException.class, () -> CdAnton.fromPastAssets(h.placement, null, h.terrain, c -> {}, (pool, id) -> {}));
    }

    @Test void antonActualBNormalAndDamagedInitKeepAllRawFlagsFractionsAndNonwrites() throws Exception {
        var h = new A();
        for (int subtype : new int[]{0, 1, 255}) for (int flip = 0; flip < 4; flip++) {
            h.angles.clear();
            h.put(h.initial.moveByte(SUBTYPE, subtype).moveByte(SPRITE_FLAGS, flip)); var before = h.state(); var cut = h.placement.capture();
            var result = h.run(); assertArrayEquals(antonInit(before, 0x23ED, true, true), result.state().copyBytes());
            assertEquals(0x20B202, result.state().longBits(MAPPINGS)); assertEquals(0, result.state().routine());
            assertOtherSlots(cut, h.placement);
            // Place falls through Init and ObjGetFloorDist unconditionally pre-zeroes the owned byte.
            assertFalse(h.angles.isEmpty()); assertEquals(0x55, h.angles.getFirst().expectedPrimaryAngle());
            assertEquals(0, h.angles.getFirst().value());
            int expectedAngle = 0x55;
            for (var call : h.angles) {
                assertEquals(expectedAngle, call.expectedPrimaryAngle()); expectedAngle = call.value();
            }
            assertEquals(expectedAngle, result.primaryAngle());
        }
    }

    @Test void antonRawSecondaryOneReadsTheFollowingBWordAndHigherValuesKeepExistingBoundary() throws Exception {
        var h = new A(); h.put(h.initial.moveByte(SUBTYPE2, 1));
        assertEquals(0x2000, h.run().state().word(TILE));
        for (int secondary = 2; secondary <= 255; secondary++) {
            h.put(h.initial.moveByte(SUBTYPE2, secondary)); var before = h.state(); int draws = h.draws.size();
            var failure = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
            assertEquals(CdAnton.UnsupportedBoundary.TILE_ENTRY, failure.boundary()); assertEquals(secondary, failure.value());
            assertArrayEquals(antonInit(before, 0, false, false), h.state().copyBytes()); assertEquals(draws, h.draws.size());
        }
    }

    @Test void antonPureBWordReadsKeepTheSignedAddressAndFiniteOriginalExtent() throws Exception {
        var bytes = past().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
        assertEquals(0x23ED, CdAnton.readPastTileWord(bytes, 0)); assertEquals(0x2000, CdAnton.readPastTileWord(bytes, 1));
        assertMalformed(bytes, 4, b -> CdAnton.readPastTileWord(b, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> CdAnton.readPastTileWord(bytes, 255));
        assertThrows(IllegalArgumentException.class, () -> CdAnton.readPastTileWord(bytes, -1));
        assertThrows(IllegalArgumentException.class, () -> CdAnton.readPastTileWord(bytes, 256));
    }

    @Test void antonUnsupportedRoutineStopsBeforeAnyBodyCallbackOrInitWrite() throws Exception {
        var h = new A();
        for (int routine : new int[]{1, 3, 5, 255}) {
            h.put(h.initial.moveByte(ROUTINE, routine)); var before = h.state();
            var failure = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
            assertEquals(CdAnton.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary()); assertEquals(before, h.state());
        }
        assertTrue(h.draws.isEmpty()); assertTrue(h.angles.isEmpty());
    }

    @Test void antonGoodFuturePrefixRunsBeforeMalformedTileAndRoutineDispatch() throws Exception {
        var h = new A(); h.good = 1; h.put(h.initial.moveByte(SUBTYPE2, 255).moveByte(ROUTINE, 255));
        var result = h.run(); byte[] expected = new byte[64]; expected[0] = 0x18;
        ByteBuffer.wrap(expected).putShort(8, (short) X0).putShort(12, (short) Y0);
        assertArrayEquals(expected, result.state().copyBytes()); assertTrue(h.draws.isEmpty()); assertTrue(h.angles.isEmpty());
        // Explicit component globals only: no B runtime goodFuture writer or route is claimed.
    }

    @Test void antonPlacePublishesWholeYIncrementBeforeFirstActualAngleCallbackFault() throws Exception {
        var h = new A(); h.put(h.initial.moveByte(ROUTINE, 2)); var before = h.state(); h.angleFault = new Fault();
        var caught = assertThrows(Fault.class, h::run); assertSame(h.angleFault, caught);
        assertEquals(1, h.angles.size()); var call = h.angles.getFirst();
        assertEquals(0, call.value()); assertEquals(before.longBits(Y_POSITION) + 0x10000, h.state().longBits(Y_POSITION));
        assertEquals(h.state(), call.expectedSource()); assertTrue(h.draws.isEmpty());
        h.angleFault = null; h.put(h.initial); assertDoesNotThrow(h::run);
    }

    @Test void antonDrawReloadAndReentryKeepActualPublishedBPrefixOnFailure() throws Exception {
        var h = new A(); h.drawFault = new Fault(); h.reenter = true; h.rewriteX = true;
        var caught = assertThrows(Fault.class, h::run); assertSame(h.drawFault, caught);
        assertEquals(0x20B202, h.state().longBits(MAPPINGS)); assertEquals(0x23ED, h.state().word(TILE)); assertEquals(X0 + 1, h.state().centreX());
        h.drawFault = null; h.reenter = false; h.rewriteX = false;
        assertDoesNotThrow(h::run); assertEquals(2, h.draws.size());
    }

    private static CdSpikes.Globals sGlobals() { return new CdSpikes.Globals(0, 0, X0); }
    private static byte[] spikesInit(CdObjectState before, boolean tile, boolean finish) {
        byte[] b = before.copyBytes(); b[0x24] += 2; b[1] |= 4; b[0x18] = 3; ByteBuffer.wrap(b).putInt(4, 0x20C048);
        if (tile) ByteBuffer.wrap(b).putShort(2, (short) 0x320);
        if (finish) { b[0x19] = (byte) ((b[1] & 2) == 0 ? 0x10 : 0x12); b[0x16] = 8; if ((b[1] & 2) != 0) b[0x20] = (byte) 0x83; b[1] &= 0x7F; }
        return b;
    }
    private static byte[] antonInit(CdObjectState before, int tile, boolean finish, boolean draw) {
        byte[] b = before.copyBytes(); b[1] |= 4; b[0x18] = 4; var bytes = ByteBuffer.wrap(b); bytes.putInt(4, 0x20B202);
        b[0x17] = 0x18; b[0x19] = 0x18; b[0x16] = 0x13; b[0x20] = 0x29; bytes.putShort(0x2E, (short) before.centreX());
        if (finish) { bytes.putShort(2, (short) tile); bytes.putInt(0x2A, before.subtype() == 0 ? 0xFFFF0000 : 0xFFFF8000); b[0x1C] = (byte) (before.subtype() == 0 ? 0 : 1); }
        if (draw) { bytes.putInt(12, before.longBits(Y_POSITION) + 0x10000); b[1] &= 0x7F; }
        return b;
    }
    private interface Read { int get(CdAssetBytes bytes); }
    private static void assertMalformed(CdAssetBytes original, int directoryAt, Read read) {
        for (int L : new int[]{0, directoryAt, directoryAt + 1})
            assertThrows(IndexOutOfBoundsException.class, () -> read.get(CdAssetBytes.copyOf(new byte[L])));
        for (int target : new int[]{-2, -1, 71, 72, 0x7FFE}) {
            byte[] b = original.copy(); ByteBuffer.wrap(b).putShort(directoryAt, (short) target);
            assertThrows(IndexOutOfBoundsException.class, () -> read.get(CdAssetBytes.copyOf(b)));
        }
    }
    private static void assertOtherSlots(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime placement) {
        for (var slot : before.pool().slots()) if (slot.identity().wholeIndex() != SLOT)
            assertEquals(slot, placement.pool().state(slot.identity().wholeIndex()));
        assertEquals(before.status(), placement.status()); assertArrayEquals(before.savedFlags().copy(), placement.capture().savedFlags().copy());
    }
    private static CdObjectState initial(CdObjectPool pool, int id) {
        return pool.state(SLOT).moveByte(ID, id).moveWord(X, X0).moveWord(Y, Y0).moveWord(X_FRACTION, 0x1357)
                .moveWord(Y_FRACTION, 0x2468).moveWord(TILE, 0xBEEF).moveLong(MAPPINGS, 0x89ABCDEF)
                .moveByte(COLLISION_TYPE, 0xB7).moveByte(MAP_FRAME, 0xA5);
    }
    private static CdSolidObject.Player player(int y) {
        var motion = new CdPlayerMotionState(X0 << 16, y << 16, 0, 0, 0, 2, 0, 2, 4, 9, 0x13, 0, 0, 0, 0, 0, 0, 0, 0x12, 0x34);
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion, 0x780, 2, 0, 0), 1, 0, 0);
    }
    private static final class S {
        final CdObjectPlacementRuntime placement; final CdObjectState initial; final CdSolidObject solid; final CdSpikes body;
        final List<CdSpikes.SolidCall> solids = new ArrayList<>(); final List<CdObjectState> draws = new ArrayList<>();
        int rewinds, hurts; boolean reenter, rewriteX; Fault drawFault;
        S() throws Exception {
            placement = new CdObjectPlacementRuntime(past().placements()); initial = initial(placement.pool(), 0x26); put(initial);
            solid = new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromPast(past())));
            body = CdSpikes.fromPastAssets(placement, past(), solid, call -> {
                solids.add(call); assertEquals(call.callCut().slots(), placement.pool().capture().slots());
                call.result().otherSlots().forEach(placement.pool()::write); placement.pool().write(call.result().object());
            }, call -> { rewinds++; fail("Unexpected rewind"); }, call -> { hurts++; fail("Unexpected Hurt"); }, this::draw);
        }
        CdObjectState state() { return placement.pool().state(SLOT); } void put(CdObjectState state) { placement.pool().write(state); }
        CdSpikes.Result run(int y, int debug) { return body.run(SLOT, sGlobals(), new CdSpikes.Inputs(player(y),
                new CdSolidObject.Gates(debug, 0), new CdSolidObject.Globals(0, 0, 0x12, 0x34),
                new CdPlayerTouchResponse.PlayerGlobals(0, 0, 0, 0, 0, debug, 0, 0),
                new CdPlayerTouchResponse.WorldGlobals(0, 3, 0, 0, 5000, 0))); }
        void draw(CdObjectPool pool, CdObjectState.Identity id) {
            draws.add(pool.state(id.wholeIndex()));
            if (reenter) assertThrows(IllegalStateException.class, () -> run(0, 0));
            var current = pool.state(id.wholeIndex()).moveByte(SPRITE_FLAGS, pool.state(id.wholeIndex()).spriteFlags() & 0x7F);
            if (rewriteX) current = current.moveWord(X, X0 + 1); pool.write(current); if (drawFault != null) throw drawFault;
        }
    }
    private static final class A {
        final CdObjectPlacementRuntime placement; final CdObjectState initial; final CdPlayerTerrain terrain; final CdAnton body;
        final List<CdAnton.PrimaryAngleCall> angles = new ArrayList<>(); final List<CdObjectState> draws = new ArrayList<>();
        int good; boolean reenter, rewriteX; Fault drawFault, angleFault;
        A() throws Exception {
            placement = new CdObjectPlacementRuntime(past().placements()); initial = initial(placement.pool(), 0x3F); put(initial);
            terrain = CdPlayerTerrain.fromStageTerrain(past().terrain());
            body = CdAnton.fromPastAssets(placement, past(), terrain, call -> {
                angles.add(call); assertEquals(call.callCut().slots(), placement.pool().capture().slots());
                if (angleFault != null) throw angleFault;
            }, this::draw);
        }
        CdObjectState state() { return placement.pool().state(SLOT); } void put(CdObjectState state) { placement.pool().write(state); }
        CdAnton.Result run() { return body.run(SLOT, new CdAnton.Globals(good, 0, 0, X0, 0x55)); }
        void draw(CdObjectPool pool, CdObjectState.Identity id) {
            draws.add(pool.state(id.wholeIndex())); if (reenter) assertThrows(IllegalStateException.class, this::run);
            var current = pool.state(id.wholeIndex()).moveByte(SPRITE_FLAGS, pool.state(id.wholeIndex()).spriteFlags() & 0x7F);
            if (rewriteX) current = current.moveWord(X, X0 + 1); pool.write(current); if (drawFault != null) throw drawFault;
        }
    }
    private static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }
    private static CdR11bAssets past() throws Exception {
        if (retained != null) return retained;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", retained.identity().mmdSha256());
        return retained;
    }
}
