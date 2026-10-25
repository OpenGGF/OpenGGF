package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-FIRST SOURCE packet, ALL UNRUN (including RED, discovery and compilation).
 * Java pin b9816d16a656358840d547d47ec34887dc791d12; native pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Anton/Main.asm:7-103,
 * Object Time Check.asm:12-44, Object Animate.asm:14-95,
 * Collision Check.asm:157-179, Collision Floor.asm:685-856,
 * Object Spawner.asm:388-446, Object Functions.asm:137-196,228-237.
 * Real placement/pool, immutable terrain, shared helper, shared animator and CPU draw queue;
 * arbitrary constructed assets/terrain authenticate NO media, owner, route or native parity.
 * Angle observer is an actual semantic test byte with a CAS stand-in, never Frame authority.
 * Whole-image checks cover all 64 bytes. Dirty writes use accessible typed fields only;
 * opaque inaccessible bytes retain their genuine fresh-pool zero values. No reflection forging.
 * Root owns actual phase/full-pool/source/global CAS, helper3/floor7, execution and integration.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdAnton {
    static final int WHOLE = 40, MAP = 0x12345678;
    static final String ANI = "00040008030001ff070203ff";
    static final String MAP_HEX = "00080022003c0056"
            + "05ed050000f4fd080004f405040007fc04050009f8fc0000110e"
            + "05ec050000f4fc080004f404040007fc0405000df8fb00001112"
            + "05ed050012f4fd080004f405040007fc04050009f8fc0000110e"
            + "05ec050012f4fc080004f404040007fc0405000df8fb00001112";
    static final String TILES = "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
            + "23a023b024092370200024284334032000004000400004090374832800000000049003e40000";

    @Test void twoAliasesOverlapActualExistingRamWithoutAnotherStateOwner() {
        var h = ready(4);
        assertEquals(0x3F, CdAnton.OBJECT_ID); assertEquals(2, CdAnton.TILE_SELECTOR);
        assertEquals(0x2A, ANTON_X_DELTA.byteOffset()); assertEquals(0x2E, ANTON_ANCHOR_X.byteOffset());
        h.edit(s -> s.moveLong(ANTON_X_DELTA, 0x12345678).moveWord(ANTON_ANCHOR_X, 0x9ABC));
        assertEquals(0x1234, h.slot().word(PATA_BATA_ANCHOR_X));
        assertEquals(0x5678, h.slot().word(CHECKPOINT_BALL_Y));
        assertEquals(0x56, h.slot().unsignedByte(RAW_CONTROL_2C));
        assertEquals(0x9A, h.slot().unsignedByte(CHECKPOINT_ACTIVE));
        assertEquals(0x9A, h.slot().unsignedByte(RAMP_TIMER));
        assertEquals(64, h.slot().copyBytes().length);
    }

    @Test void constructedAssetsRequireFourFramesTwoScriptsAndTwentyTwoTileBound() {
        assertEquals(74, hex(TILES).length); assertEquals(112, hex(MAP_HEX).length);
        var a = assets(); assertEquals(MAP, a.mappingsAddress());
        assertEquals(4, a.visual().mappingFrames().size()); assertEquals(2, a.visual().animations().size());
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Assets(CdAssetBytes.copyOf(new byte[73]), a.visual(), MAP));
        assertThrows(NullPointerException.class, () -> new CdAnton.Assets(null, a.visual(), MAP));
        assertThrows(NullPointerException.class, () -> new CdAnton.Assets(a.objectTileIds(), null, MAP));
        byte[] bad = hex(MAP_HEX); bad[64] = 0x13; // frame 2 first piece tile low byte: 19+4 reaches 22.
        var tooWide = CdObjectVisualAssets.decode(bad, 4, hex(ANI), 2, 23);
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Assets(a.objectTileIds(), tooWide, MAP));
        var threeFrames = CdObjectVisualAssets.decode(hex("0006000c0012010000000000010000000000010000000000"),
                3, hex("00020300ff00"), 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Assets(a.objectTileIds(), threeFrames, MAP));
    }

    @Test void globalsAcceptRawBytesButRejectEveryWidthViolation() {
        assertEquals(255, new CdAnton.Globals(255, 255, 255, 65535, 255).timeZone());
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Globals(-1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Globals(0, 256, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Globals(0, 0, 256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Globals(0, 0, 0, 65536, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.Globals(0, 0, 0, 0, 256));
        var h = ready(4);
        assertThrows(IndexOutOfBoundsException.class, () -> h.body.run(31, h.globals()));
        assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, null));
        assertThrows(NullPointerException.class, () -> new CdAnton(null, assets(), new Terrain().build(), c -> { }, (p, i) -> { }));
        h.edit(s -> s.moveByte(ID, 0x3D));
        assertThrows(IllegalArgumentException.class, h::run);
    }

    @ParameterizedTest @ValueSource(ints = {1, 127, 128, 255})
    void everyNonzeroGoodFutureValueConvertsBeforeInvalidRoutineAndClearsFullImage(int good) {
        var h = ready(0xFF); h.good = good; h.time = 0;
        h.edit(s -> dirty(s).moveByte(ID, 0x3F).moveByte(ROUTINE, 255).moveByte(SPRITE_FLAGS, 255)
                .moveByte(SUBTYPE, 0).moveByte(SAVED_FLAGS_ID, 1)
                .moveLong(X_POSITION, 0x0B101234).moveLong(Y_POSITION, 0x0425ABCD));
        h.placement.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.LOADED);
        var r = h.run();
        byte[] expected = new byte[64]; b(expected, 0, 0x18); w(expected, 8, 0x0B10); w(expected, 12, 0x0425);
        assertArrayEquals(expected, r.state().copyBytes()); assertArrayEquals(expected, h.slot().copyBytes());
        assertEquals(0x80, h.placement.savedFlag(1, 0)); assertEquals(0x55, h.angle);
        assertTrue(r.deleted()); assertEquals(0, h.draws.size()); assertTrue(h.angleCalls.isEmpty());
        assertEquals(0, count(r, CdAnton.NativeCall.DRAW_OBJECT));
        assertEquals(0, count(r, CdAnton.NativeCall.CHECK_OBJ_DESPAWN));
        assertEquals(1, r.effects().stream().filter(CdAnton.ConvertedToExplosion.class::isInstance).count());
    }

    @Test void rawTimeZoneOneIsTheOnlyZeroSubtypeConversionExemption() {
        for (int time = 0; time < 256; time++) {
            var h = ready(255); h.good = 1; h.time = time; h.edit(s -> s.moveByte(SUBTYPE, 0));
            if (time == 1) {
                var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run); assertEquals(CdAnton.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
            } else {
                assertEquals(0x18, h.run().state().id(), "raw timeZone=" + time);
            }
            assertEquals(0, h.draws.size());
        }
    }

    @Test void allNonzeroSubtypeBytesConvertEvenAtPresentAndGoodZeroNeverConverts() {
        for (int subtype = 1; subtype < 256; subtype++) {
            var h = ready(255); h.good = 255; h.time = 1; final int value = subtype;
            h.edit(s -> s.moveByte(SUBTYPE, value)); assertEquals(0x18, h.run().state().id());
        }
        for (int time : new int[]{0, 1, 2, 0x81, 0xFF}) {
            var h = ready(255); h.time = time; h.good = 0;
            var before = h.slot().copyBytes();
            assertThrows(CdAnton.UnsupportedAnton.class, h::run); assertArrayEquals(before, h.slot().copyBytes());
        }
    }

    @Test void rawRoutineDirectorySupportsOnlyZeroTwoFourAfterTimeCheck() {
        for (int routine = 0; routine < 256; routine++) {
            if (routine == 0 || routine == 2 || routine == 4) continue;
            var h = ready(routine); var before = h.slot().copyBytes();
            var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
            assertEquals(CdAnton.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
            assertEquals(routine, e.value()); assertEquals(WHOLE, e.wholeIndex());
            assertArrayEquals(before, h.slot().copyBytes()); assertTrue(h.angleCalls.isEmpty()); assertTrue(h.draws.isEmpty());
        }
    }

    @ParameterizedTest @CsvSource({"0,0,-65536,0,9225", "1,0,-32768,1,9225", "128,1,-32768,1,9072", "255,0,-32768,1,9225"})
    void initPublishesOnlyNativeWritesThenPlaceWithoutAnimatorReset(int subtype, int subtype2, int delta, int animation, int tile) {
        var h = ready(0);
        h.edit(s -> dirty(s).moveByte(ID, 0x3F).moveByte(ROUTINE, 0).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, subtype2)
                .moveByte(SAVED_FLAGS_ID, 0).moveByte(SPRITE_FLAGS, 0xC1).moveByte(FLAGS, 0xA2)
                .moveLong(X_POSITION, 0x0271ABCD).moveLong(Y_POSITION, 0x0774BEEF)
                .moveByte(MAP_FRAME, 7).moveByte(ANIMATION_FRAME, 0xF8).moveByte(PREVIOUS_ANIMATION, 0xE9).moveByte(ANIMATION_TIME, 0xDA));
        byte[] expected = h.slot().copyBytes();
        b(expected, 1, 0x45); w(expected, 2, tile); l(expected, 4, MAP); l(expected, 12, 0x0775BEEF);
        b(expected, 0x16, 0x13); b(expected, 0x17, 0x18); b(expected, 0x18, 4); b(expected, 0x19, 0x18);
        b(expected, 0x1C, animation); b(expected, 0x20, 0x29); b(expected, 0x24, 2); l(expected, 0x2A, delta); w(expected, 0x2E, 0x271);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes());
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(7, h.draws.getFirst().unsignedByte(MAP_FRAME));
        assertEquals(0, count(r, CdAnton.NativeCall.ANIMATE_OBJECT)); assertEquals(List.of(0, 0x22), h.angleValues());
        assertEquals(0x0775BEEF, h.angleCalls.getFirst().expectedSource().longBits(Y_POSITION));
    }

    @Test void freshDamagedPlaceRetainsMapZeroUntilActualMainAnimation() {
        var h = fresh(new Terrain()); h.edit(s -> s.moveByte(ID, 0x3F).moveWord(X, 0x271).moveWord(Y, 0x774).moveByte(SUBTYPE, 255));
        var first = h.run(); assertEquals(2, first.state().routine()); assertEquals(0, first.state().unsignedByte(MAP_FRAME));
        h.nextPass(); var second = h.run(); assertEquals(4, second.state().routine()); assertEquals(0, second.state().unsignedByte(MAP_FRAME));
        h.nextPass(); var third = h.run(); assertEquals(2, third.state().unsignedByte(MAP_FRAME));
        assertEquals(7, third.state().unsignedByte(ANIMATION_TIME)); assertEquals(1, third.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(0x02708000, third.state().longBits(X_POSITION));
    }

    @Test void nonnegativePlaceAtRoutineZeroRepeatsInitOnNextDispatch() {
        var h = ready(0); h.edit(s -> s.moveWord(Y, 0x760));
        assertEquals(0, h.run().state().routine()); h.nextPass();
        h.edit(s -> s.moveWord(X, 0x272).moveByte(PRIORITY, 99).moveByte(ANIMATION, 99).moveLong(ANTON_X_DELTA, 123));
        var r = h.run(); assertEquals(0, r.state().routine()); assertEquals(0x272, r.state().word(ANTON_ANCHOR_X));
        assertEquals(4, r.state().unsignedByte(PRIORITY)); assertEquals(-65536, r.state().longBits(ANTON_X_DELTA));
        assertEquals(0, r.state().unsignedByte(ANIMATION)); assertEquals(0, count(r, CdAnton.NativeCall.ANIMATE_OBJECT));
    }

    @Test void routineTwoAddsLongYWithWrapRetainsFractionAndNeverRunsInitOrMain() {
        var h = ready(2); h.edit(s -> s.moveLong(Y_POSITION, 0xFFFFBEEF).moveByte(PRIORITY, 6).moveByte(MAP_FRAME, 3));
        byte[] expected = h.slot().copyBytes(); l(expected, 12, 0x0000BEEF);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes()); assertEquals(2, r.state().routine());
        assertEquals(List.of(0), h.angleValues()); assertEquals(0, count(r, CdAnton.NativeCall.ANIMATE_OBJECT));
    }

    @ParameterizedTest @ValueSource(ints = {2, 3, 128, 255})
    void unownedTileSubtypeStopsAfterInitAnchorBeforeDeltaAnimationYAndProbe(int subtype2) {
        var h = ready(0); h.edit(s -> s.moveByte(SUBTYPE2, subtype2).moveByte(SPRITE_FLAGS, 0x81)
                .moveLong(ANTON_X_DELTA, 0x11223344).moveByte(ANIMATION, 0x77));
        byte[] expected = h.slot().copyBytes(); b(expected, 1, 0x85); b(expected, 0x18, 4); l(expected, 4, MAP);
        b(expected, 0x17, 0x18); b(expected, 0x19, 0x18); b(expected, 0x16, 0x13); b(expected, 0x20, 0x29); w(expected, 0x2E, 0x271);
        var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
        assertEquals(CdAnton.UnsupportedBoundary.TILE_ENTRY, e.boundary()); assertEquals(subtype2, e.value());
        assertArrayEquals(expected, h.slot().copyBytes()); assertTrue(h.angleCalls.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,-65536,40894464", "1,-32768,40927232"})
    void mainAddsTheStoredLongDeltaRatherThanInferringSubtypeOrVelocity(int subtype, int delta, int expectedX) {
        var h = ready(4); h.edit(s -> s.moveByte(SUBTYPE, subtype).moveLong(ANTON_X_DELTA, delta)
                .moveWord(X_VELOCITY, 0x1234).moveWord(Y_VELOCITY, 0xFEDC).moveByte(ANIMATION_TIME, 1));
        byte[] expected = h.slot().copyBytes(); l(expected, 8, expectedX); b(expected, 0x1E, 0);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes()); assertEquals(List.of(0, 0x22), h.angleValues());
        assertEquals(1, count(r, CdAnton.NativeCall.ANIMATE_OBJECT));
    }

    @Test void longFractionBorrowAndNativeWordAbsolute8000DoNotTurn() {
        var terrain = new Terrain().block(6, 8, 0x2001);
        var h = ready(4, terrain); h.edit(s -> s.moveLong(X_POSITION, 0x02701000).moveLong(ANTON_X_DELTA, 0xFFFF8000));
        assertEquals(0x026F9000, h.run().state().longBits(X_POSITION));
        var a = ready(4); a.camera = 0x8280; a.edit(s -> s.moveWord(ANTON_ANCHOR_X, 0x8271).moveLong(ANTON_X_DELTA, 0));
        var r = a.run(); assertEquals(0x02710000, r.state().longBits(X_POSITION));
        assertEquals(0, r.state().flags()); assertEquals(4, r.state().spriteFlags()); assertEquals(2, a.angleCalls.size());
    }

    @Test void longXAdditionWrapsTheWholeThirtyTwoBitPositionAtZero() {
        var h = ready(4, new Terrain().cell(7, 0, 2).block(0, 8, 0x2001)); h.camera = 0x80;
        h.edit(s -> s.moveLong(X_POSITION, 0xFFFF8000).moveLong(ANTON_X_DELTA, 0x8000).moveWord(ANTON_ANCHOR_X, 0));
        var r = h.run(); assertEquals(0, r.state().longBits(X_POSITION)); assertEquals(0x8000, r.state().longBits(ANTON_X_DELTA));
        assertEquals(List.of(0, 0x22), h.angleValues()); assertEquals(0, r.state().flags());
    }

    @Test void terrainUsesOnlyTheObjectsOwnSpritePathBitOnFlaggedChunks() {
        var t = new Terrain().flagged(7, 2, 2); w(t.chunks, 1024 + (8 * 16 + 7) * 2, 0x2002);
        var h = ready(4, t); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x44));
        assertEquals(0x34, h.run().primaryAngle());
        var lower = ready(4, t); assertEquals(0x22, lower.run().primaryAngle());
    }

    @Test void rangeEquality128TurnsBeforeFloorAndAddsXAgainInTheSameCall() {
        var h = ready(4, new Terrain().block(14, 8, 0x2001));
        h.edit(s -> s.moveWord(X, 0x2EF).moveWord(ANTON_ANCHOR_X, 0x270).moveLong(ANTON_X_DELTA, 0x10000)
                .moveByte(SPRITE_FLAGS, 0xC4).moveByte(FLAGS, 0xA0));
        byte[] expected = h.slot().copyBytes(); b(expected, 1, 0x45); b(expected, 0x22, 0xA1); l(expected, 0x2A, 0xFFFF0000); b(expected, 0x1E, 0);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes()); assertEquals(0x2EF, h.angleCalls.getFirst().expectedSource().centreX());
        assertEquals(2, h.angleCalls.size(), "range turn does not probe the rejected endpoint");
    }

    @ParameterizedTest @CsvSource({"1901,7,1908", "1915,-7,1908"})
    void floorAcceptsBothInclusiveSevenEndpointsAndKeepsYFraction(int y, int distance, int resultY) {
        var h = ready(4); h.edit(s -> s.moveWord(Y, y)); byte[] expected = h.slot().copyBytes();
        l(expected, 8, 0x02700000); w(expected, 12, resultY); b(expected, 0x1E, 0);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes()); assertEquals(distance, h.floors.getFirst().distance());
        assertEquals(0xBEEF, r.state().word(Y_FRACTION));
    }

    @ParameterizedTest @CsvSource({"1916,8,7,-8,-7,34,52", "1901,7,8,8,7,34,52"})
    void rejectedEightTurnsAndSecondProbeCommitsItsOwnAngleBeforeAcceptance(int y, int firstHeight, int secondHeight,
                                                                                 int rejected, int accepted, int firstAngle, int secondAngle) {
        var t = new Terrain().shape(7, firstHeight, firstAngle).shape(8, secondHeight, secondAngle).block(8, 8, 0x2002);
        var h = ready(4, t); h.edit(s -> s.moveWord(X, 0x280).moveWord(ANTON_ANCHOR_X, 0x280).moveWord(Y, y));
        var r = h.run(); assertEquals(0x02800000, r.state().longBits(X_POSITION)); assertEquals(0x10000, r.state().longBits(ANTON_X_DELTA));
        assertEquals(1, r.state().flags()); assertEquals(5, r.state().spriteFlags()); assertEquals(y + accepted, r.state().centreY());
        assertEquals(List.of(0, firstAngle, 0, secondAngle), h.angleValues()); assertEquals(2, h.floors.size());
        assertEquals(rejected, h.floors.get(0).distance()); assertEquals(accepted, h.floors.get(1).distance());
        assertEquals(0x027F0000, h.angleCalls.get(0).expectedSource().longBits(X_POSITION));
        assertEquals(0x02800000, h.angleCalls.get(2).expectedSource().longBits(X_POSITION));
        assertEquals(1, count(r, CdAnton.NativeCall.DRAW_OBJECT));
    }

    @Test void repeatedActualMainCutStopsAsHostNonReturningCycleWithNoDrawOrSuccess() {
        var h = ready(4); h.edit(s -> s.moveWord(Y, 0x77C));
        var before = h.slot(); var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
        assertEquals(CdAnton.UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE, e.boundary());
        assertTrue(e.getMessage().contains("host")); assertEquals(WHOLE, e.wholeIndex()); assertTrue(h.draws.isEmpty());
        assertEquals(0x22, h.angle); assertEquals(6, h.angleCalls.size());
        byte[] expected = before.copyBytes(); l(expected, 8, 0x02700000); l(expected, 0x2A, 0x10000); b(expected, 1, 5); b(expected, 0x22, 1);
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(1, h.slot().unsignedByte(ANIMATION_TIME));
    }

    @ParameterizedTest @CsvSource({"8193,0:34", "10241,0:34:222", "12289,0:34:98:158:94:0", "14337,0:34:222:30:226:162:0"})
    void synchronousAngleObserverSeesEveryRawFlipAndForcedZeroStore(int word, String sequence) {
        var h = ready(2, new Terrain().block(7, 8, word)); h.edit(s -> s.moveWord(Y, 0x774));
        var r = h.run(); assertEquals(parse(sequence), h.angleValues()); assertEquals(0x775, r.state().centreY());
        assertEquals(4, r.state().routine());
        for (var call : h.angleCalls) assertEquals(0x0775BEEF, call.expectedSource().longBits(Y_POSITION));
        assertEquals(h.angle, r.primaryAngle()); assertEquals(1, count(r, CdAnton.NativeCall.OBJ_GET_FLOOR_DIST));
    }

    @Test void sameValueAngleStoresAreNotElidedAndOddRawAngleIsNeverClearedAfterReturn() {
        var h = ready(2, new Terrain().shape(7, 8, 0)); h.angle = 0; h.edit(s -> s.moveWord(Y, 0x773));
        h.run(); assertEquals(List.of(0, 0), h.angleValues());
        var odd = ready(2, new Terrain().shape(7, 8, 0x23)); odd.edit(s -> s.moveWord(Y, 0x773));
        var r = odd.run(); assertEquals(List.of(0, 0x23), odd.angleValues()); assertEquals(0x23, r.primaryAngle());
        assertEquals(0, odd.floors.getFirst().angleResult()); assertEquals(0x23, odd.floors.getFirst().primaryAngle());
    }

    @Test void firstAndAdjacentSamplesEachPublishTheirAngleInSourceOrder() {
        var t = new Terrain().shape(7, 16, 0x22).shape(8, 0, 0x35).block(7, 7, 0x2002);
        var h = ready(2, t); h.edit(s -> s.moveWord(Y, 0x773));
        h.run(); assertEquals(List.of(0, 0x22, 0x35), h.angleValues()); assertEquals(0x35, h.angle);
    }

    @Test void unavailableFirstTerrainWordKeepsPlaceYAndPrezeroAndPropagatesSameFailure() {
        var t = new Terrain().flagged(7, 2, 121); var h = ready(2, t);
        h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x44)); byte[] expected = h.slot().copyBytes(); l(expected, 12, 0x0775BEEF);
        var e = assertThrows(IllegalStateException.class, h::run); assertTrue(e.getMessage().contains("Unavailable native collision word"));
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(List.of(0), h.angleValues()); assertEquals(0, h.angle); assertTrue(h.draws.isEmpty());
    }

    @Test void unavailableAdjacentWordRetainsFirstSampleAngleAndNoRoutineIncrement() {
        var t = new Terrain().cell(6, 2, 2).flagged(7, 2, 121).block(7, 15, 0x2001).shape(7, 0, 0x25);
        var h = ready(2, t); h.edit(s -> s.moveWord(Y, 0x6E4).moveByte(SPRITE_FLAGS, 0x44));
        byte[] expected = h.slot().copyBytes(); w(expected, 12, 0x6E5);
        assertThrows(IllegalStateException.class, h::run); assertArrayEquals(expected, h.slot().copyBytes());
        assertEquals(List.of(0, 0x25), h.angleValues()); assertEquals(0x25, h.angle); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void callbackFailurePropagatesIdentityImmediatelyAndRetainsEarlierAngleAndPoolPrefix(int failureAt) {
        var h = ready(2, new Terrain().block(7, 8, 0x3801)); h.edit(s -> s.moveWord(Y, 0x773)); h.rejectAngle = failureAt;
        byte[] expected = h.slot().copyBytes(); w(expected, 12, 0x774);
        assertSame(h.fault, assertThrows(Fault.class, h::run)); assertArrayEquals(expected, h.slot().copyBytes());
        assertEquals(failureAt, h.angleCalls.size()); assertTrue(h.draws.isEmpty());
        int[] previous = {0x55, 0, 0x22, 0xDE, 0x1E, 0xE2, 0xA2}; assertEquals(previous[failureAt - 1], h.angle);
    }

    @Test void callbackFailureOnRetryKeepsTurnAndSecondXAndEarlierSuccessfulProbe() {
        var t = new Terrain().shape(7, 8, 0x22).shape(8, 7, 0x34).block(8, 8, 0x2002);
        var h = ready(4, t); h.edit(s -> s.moveWord(X, 0x280).moveWord(ANTON_ANCHOR_X, 0x280).moveWord(Y, 0x77C)); h.rejectAngle = 3;
        assertSame(h.fault, assertThrows(Fault.class, h::run)); assertEquals(List.of(0, 0x22, 0), h.angleValues());
        assertEquals(0x22, h.angle); assertEquals(0x02800000, h.slot().longBits(X_POSITION));
        assertEquals(0x10000, h.slot().longBits(ANTON_X_DELTA)); assertEquals(1, h.slot().flags()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void changedSourceOrOtherSlotDuringAngleCallbackIsDetectedWithoutStaleOverwrite(int mutation) {
        var h = ready(2); h.mutateAngle = mutation;
        assertThrows(IllegalStateException.class, h::run); assertEquals(List.of(0), h.angleValues()); assertEquals(0, h.angle);
        if (mutation == 1) assertEquals(0xA5, h.slot().flags());
        else assertEquals(0xA6, h.pool().state(41).id());
        assertEquals(0x775, h.slot().centreY()); assertTrue(h.draws.isEmpty());
    }

    @Test void foreignPoolImageAndSynchronousReentryAreRejectedAtTheirActualCallback() {
        var foreign = ready(2); foreign.foreignAngle = true;
        assertThrows(IllegalArgumentException.class, foreign::run); assertEquals(0, foreign.angle); assertEquals(0x775, foreign.slot().centreY());
        var nested = ready(2); nested.reenterAngle = true;
        assertThrows(IllegalStateException.class, nested::run); assertEquals(0x775, nested.slot().centreY());
        assertEquals(0x55, nested.angle); assertTrue(nested.draws.isEmpty());
    }

    @Test void callRecordCarriesActualSamePoolCutAndExpectedByteButCannotGrantPublicationAuthority() {
        var h = ready(2); h.edit(s -> s.moveWord(Y, 0x773)); h.run();
        assertEquals(0x55, h.angleCalls.get(0).expectedPrimaryAngle()); assertEquals(0, h.angleCalls.get(1).expectedPrimaryAngle());
        for (var call : h.angleCalls) {
            h.pool().validateRestore(call.callCut());
            assertEquals(call.expectedSource(), call.callCut().slots().get(WHOLE - 32));
            assertEquals(0x0774BEEF, call.expectedSource().longBits(Y_POSITION));
        }
        var before = h.pool().capture(); var source = h.slot();
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.PrimaryAngleCall(before, source, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnton.PrimaryAngleCall(before, source.moveByte(FLAGS, 99), 0, 0));
        var other = new CdObjectPool(); assertThrows(IllegalArgumentException.class, () -> h.pool().validateRestore(other.capture()));
        var cas = ready(2); cas.casMismatch = true;
        assertSame(cas.fault, assertThrows(Fault.class, cas::run)); assertEquals(0x55, cas.angle); assertEquals(0x775, cas.slot().centreY());
    }

    @Test void aNoOpPortIsOnlyAnUntrustedValuePacketAndNextSemanticCasDetectsIt() {
        var h = ready(2); h.noOpAngle = true;
        assertSame(h.fault, assertThrows(Fault.class, h::run));
        assertEquals(0x55, h.angle); assertEquals(List.of(0, 0x22), h.angleValues()); assertTrue(h.draws.isEmpty());
        // A callback returning from the sole blank-terrain pre-zero cannot be authenticated by
        // body-side value equality. Root's actual semantic publication owner is mandatory.
        var blank = ready(2, new Terrain().clear()); blank.noOpAngle = true;
        var metadata = blank.run(); assertEquals(0, metadata.primaryAngle()); assertEquals(0x55, blank.angle);
    }

    @Test void animationIndexFaultKeepsAcceptedMotionAngleAndNativeSpeedPrefix() {
        var h = ready(4); h.edit(s -> s.moveWord(Y, 0x76D).moveByte(ANIMATION_FRAME, 250).moveByte(ANIMATION_TIME, 0).moveByte(MAP_FRAME, 3));
        byte[] expected = h.slot().copyBytes(); l(expected, 8, 0x02700000); w(expected, 12, 0x774); b(expected, 0x1E, 3);
        var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
        assertEquals(CdAnton.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, e.boundary()); assertEquals(255, e.value());
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(0x22, h.angle); assertTrue(h.draws.isEmpty());
    }

    @Test void animationDirectoryFaultOccursOnlyAfterReachedResetAndTimerDecrement() {
        var h = ready(4); h.edit(s -> s.moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 0).moveByte(ANIMATION_FRAME, 99).moveByte(ANIMATION_TIME, 77));
        byte[] expected = h.slot().copyBytes(); l(expected, 8, 0x02700000); b(expected, 0x1D, 255); b(expected, 0x1B, 0); b(expected, 0x1E, 255);
        var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
        assertEquals(CdAnton.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY, e.boundary()); assertEquals(510, e.value());
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(0x22, h.angle); assertTrue(h.draws.isEmpty());
    }

    @Test void lazyTimerBranchDoesNotPrevalidateBadAnimationOrScriptIndex() {
        var h = ready(4); h.edit(s -> s.moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_FRAME, 255)
                .moveByte(ANIMATION_TIME, 0x80).moveByte(MAP_FRAME, 3));
        var r = h.run(); assertEquals(0x7F, r.state().unsignedByte(ANIMATION_TIME)); assertEquals(255, r.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(3, r.state().unsignedByte(MAP_FRAME)); assertEquals(1, h.draws.size());
    }

    @ParameterizedTest @CsvSource({"0,0,0,3,0,1", "0,0,1,3,1,2", "0,0,2,3,0,1", "1,0,250,7,2,1", "1,1,1,7,3,2", "1,1,2,7,2,1"})
    void sharedAnimationReadsExactNormalAndDamagedScriptsAndFfLoop(int animation, int previous, int index, int timer, int frame, int nextIndex) {
        var h = ready(4); h.edit(s -> s.moveByte(ANIMATION, animation).moveByte(PREVIOUS_ANIMATION, previous)
                .moveByte(ANIMATION_FRAME, index).moveByte(ANIMATION_TIME, 0));
        var r = h.run(); assertEquals(timer, r.state().unsignedByte(ANIMATION_TIME)); assertEquals(frame, r.state().unsignedByte(MAP_FRAME));
        assertEquals(nextIndex, r.state().unsignedByte(ANIMATION_FRAME)); assertEquals(animation, r.state().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(1, count(r, CdAnton.NativeCall.ANIMATE_OBJECT));
    }

    @Test void drawFailureRetainsAnimationAngleAndActualDrawClearPrefixWithoutTail() {
        var h = ready(4); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x84).moveByte(ANIMATION_TIME, 0)); h.rejectDraw = true;
        h.camera = 0x800; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1)); h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertSame(h.fault, assertThrows(Fault.class, h::run));
        assertEquals(0x02700000, h.slot().longBits(X_POSITION)); assertEquals(3, h.slot().unsignedByte(ANIMATION_TIME));
        assertEquals(4, h.slot().spriteFlags()); assertEquals(0x3F, h.slot().id()); assertEquals(0x80, h.placement.savedFlag(1, 1));
        assertEquals(0x22, h.angle); assertEquals(1, h.draws.size());
    }

    @Test void realDrawQueueClearsBitSevenAtEnqueueAndSetsItOnlyWhenLaterDrained() {
        var h = ready(4); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x84));
        var r = h.run(); assertEquals(4, r.state().spriteFlags()); assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, h.enqueues.getFirst());
        var sat = h.finishQueue(); assertEquals(5, sat.spriteCount()); assertEquals(0x84, h.slot().spriteFlags());
        assertEquals(WHOLE, sat.provenance().getFirst().slotRef().wholeIndex());
        assertEquals(4, r.state().spriteFlags(), "Result is an immutable completed-body image, not a later SAT image");
        assertThrows(UnsupportedOperationException.class, () -> r.effects().add(new CdAnton.Deleted()));
    }

    @Test void wrapperReloadsActualDrawOutputAndLiveAnchorBeforeDespawn() {
        var h = ready(4); h.edit(s -> s.moveWord(ANTON_ANCHOR_X, 0x270));
        h.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x1000).moveByte(FLAGS, 0xA5);
        assertTrue(h.run().deleted()); assertArrayEquals(new byte[64], h.slot().copyBytes());
        var sign = ready(4); sign.drawRewrite = s -> s.moveByte(SPRITE_FLAGS, 0x84).moveWord(ANTON_ANCHOR_X, 0x1000).moveByte(FLAGS, 0xA5);
        var r = sign.run(); assertEquals(0x3F, r.state().id()); assertEquals(0xA5, r.state().flags()); assertEquals(0x1000, r.state().word(ANTON_ANCHOR_X));
    }

    @Test void anchorWindowEqualitySurvivesAndNextChunkDeletesEvenThoughMovedXIsVisible() {
        var h = ready(4); h.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x400); // camera $200 -> base $180, difference $280.
        assertFalse(h.run().deleted());
        var gone = ready(4); gone.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x480);
        assertTrue(gone.run().deleted()); assertEquals(0x270, gone.draws.getFirst().centreX());
    }

    @Test void savedIdZeroAvoidsEvenAnOtherwiseInvalidSavedAddressAndDeletesFullImage() {
        var h = ready(4); h.time = 0x7F; h.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x1000).moveByte(SAVED_FLAGS_ID, 0);
        var r = h.run(); assertTrue(r.deleted()); assertArrayEquals(new byte[64], r.state().copyBytes());
        assertEquals(0, r.effects().stream().filter(CdAnton.SavedFlagChanged.class::isInstance).count());
    }

    @ParameterizedTest @CsvSource({"3,2,0", "4,2,1", "127,43,1"})
    void rawNonwarpPeriodAliasesTheActualLinearBankByte(int rawPeriod, int canonicalId, int canonicalPeriod) {
        var h = ready(4); h.time = rawPeriod; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1));
        for (var bit : CdObjectPlacementRuntime.SavedBit.values()) h.placement.bsetSavedFlag(canonicalId, canonicalPeriod, bit);
        h.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x1000);
        var r = h.run(); assertTrue(r.deleted()); assertEquals(0x7F, h.placement.savedFlag(canonicalId, canonicalPeriod));
        var effect = r.effects().stream().filter(CdAnton.SavedFlagChanged.class::isInstance).map(CdAnton.SavedFlagChanged.class::cast).findFirst().orElseThrow();
        assertEquals(5 + rawPeriod, effect.byteIndex()); assertEquals(255, effect.previousValue()); assertEquals(127, effect.value());
    }

    @ParameterizedTest @CsvSource({"128,1,0", "128,255,1", "129,255,2", "255,128,2", "128,127,0"})
    void warpUsesSignedDirectionWordSubtractionAndClampBeforeSavedLookup(int time, int direction, int period) {
        var h = ready(4); h.time = time; h.direction = direction; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1));
        h.placement.bsetSavedFlag(1, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.drawRewrite = s -> s.moveByte(SPRITE_FLAGS, 0x84); // sign bypasses X only; warp still deletes.
        var r = h.run(); assertTrue(r.deleted()); assertEquals(0, h.placement.savedFlag(1, period));
        assertEquals(1, r.effects().stream().filter(CdAnton.SavedFlagChanged.class::isInstance).count());
    }

    @Test void savedAddressLastOwnedByteWorksAndFirstUnownedStopsAfterDraw() {
        var valid = ready(4); valid.time = 1; valid.edit(s -> s.moveByte(SAVED_FLAGS_ID, 254));
        valid.placement.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        valid.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x1000);
        assertTrue(valid.run().deleted()); assertEquals(0, valid.placement.savedFlag(254, 1));
        var h = ready(4); h.time = 2; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 254).moveByte(SPRITE_FLAGS, 0x84));
        h.drawRewrite = s -> s.moveWord(ANTON_ANCHOR_X, 0x1000).moveByte(FLAGS, 0xA5);
        var e = assertThrows(CdAnton.UnsupportedAnton.class, h::run);
        assertEquals(CdAnton.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, e.boundary()); assertEquals(766, e.value());
        assertEquals(0x3F, h.slot().id()); assertEquals(4, h.slot().spriteFlags()); assertEquals(0x1000, h.slot().word(ANTON_ANCHOR_X));
        assertEquals(0xA5, h.slot().flags()); assertEquals(0x22, h.angle); assertEquals(1, h.draws.size());
    }

    @Test void untouchedOtherSlotsAndSavedBankRemainExactOnNormalDispatch() {
        var h = ready(4); h.pool().write(dirty(h.pool().state(41)).moveByte(ID, 0x57));
        var before = h.pool().capture(); var bank = h.placement.capture().savedFlags().copy(); h.run();
        for (int whole = 32; whole < 128; whole++) {
            if (whole != WHOLE) assertArrayEquals(before.slots().get(whole - 32).copyBytes(), h.pool().state(whole).copyBytes());
        }
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
    }

    static CdAnton.Assets assets() {
        return new CdAnton.Assets(CdAssetBytes.copyOf(hex(TILES)), CdObjectVisualAssets.decode(hex(MAP_HEX), 4, hex(ANI), 2, 22), MAP);
    }
    static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    static List<Integer> parse(String value) { return Arrays.stream(value.split(":")).map(Integer::valueOf).toList(); }
    static void b(byte[] image, int at, int value) { image[at] = (byte) value; }
    static void w(byte[] image, int at, int value) { b(image, at, value >>> 8); b(image, at + 1, value); }
    static void l(byte[] image, int at, int value) { w(image, at, value >>> 16); w(image, at + 2, value); }
    static long count(CdAnton.Result r, CdAnton.NativeCall call) {
        return r.effects().stream().filter(CdAnton.CallReturned.class::isInstance).map(CdAnton.CallReturned.class::cast).filter(e -> e.call() == call).count();
    }
    static CdObjectPlacements layout() {
        return CdObjectPlacements.decodeLayout(hex("000c0014ffff000000000000ffff000000000000ffff00000000"));
    }
    static CdObjectState dirty(CdObjectState s) {
        for (var field : CdObjectState.ByteField.values()) s = s.moveByte(field, 0xA5);
        for (var field : CdObjectState.WordField.values()) s = s.moveWord(field, 0xB6C7);
        for (var field : CdObjectState.LongField.values()) s = s.moveLong(field, 0xD8E9FA0B);
        return s;
    }
    static Harness fresh(Terrain terrain) { return new Harness(terrain); }
    static Harness ready(int routine) { return ready(routine, new Terrain()); }
    static Harness ready(int routine, Terrain terrain) {
        var h = fresh(terrain); h.edit(s -> s.moveByte(ID, 0x3F).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 4)
                .moveWord(TILE, 0x2409).moveLong(MAPPINGS, MAP).moveLong(X_POSITION, 0x02710000).moveLong(Y_POSITION, 0x0774BEEF)
                .moveByte(X_RADIUS, 0x18).moveByte(WIDTH, 0x18).moveByte(Y_RADIUS, 0x13).moveByte(PRIORITY, 4)
                .moveByte(COLLISION_TYPE, 0x29).moveLong(ANTON_X_DELTA, 0xFFFF0000).moveWord(ANTON_ANCHOR_X, 0x271).moveByte(ANIMATION_TIME, 1));
        return h;
    }

    /** Real immutable terrain format; IDs 1/2 select shapes 7/8. No stubbed distance producer. */
    static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50], ids = new byte[900];
        final byte[] angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            cell(7, 2, 2); ids[1] = 7; ids[2] = 8; shape(7, 8, 0x22); shape(8, 8, 0x34); block(7, 8, 0x2001);
        }
        Terrain clear() { Arrays.fill(chunks, (byte) 0); return this; }
        Terrain cell(int row, int column, int value) { fg[2 + row * 42 + column] = (byte) value; return this; }
        Terrain flagged(int row, int column, int chunk) { return cell(row, column, chunk | 0x80); }
        Terrain block(int column, int row, int word) { w(chunks, 512 + (row * 16 + column) * 2, word); return this; }
        Terrain shape(int id, int height, int angle) {
            angles[id] = (byte) angle; Arrays.fill(heights, 16 * id, 16 * id + 16, (byte) height); return this;
        }
        CdPlayerTerrain build() { return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths)); }
    }
    static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }

    static final class Harness {
        final CdObjectPlacementRuntime placement = new CdObjectPlacementRuntime(layout());
        final List<CdAnton.PrimaryAngleCall> angleCalls = new ArrayList<>();
        final List<CdObjectCollisionCheck.FloorDistance> floors = new ArrayList<>();
        final List<CdObjectState> draws = new ArrayList<>();
        final List<CdObjectDrawQueue.EnqueueResult> enqueues = new ArrayList<>();
        final Fault fault = new Fault(); final CdObjectDrawQueue queue; final CdAnton body;
        int good, time = 1, direction, camera = 0x200, angle = 0x55, rejectAngle, mutateAngle;
        boolean rejectDraw, reenterAngle, foreignAngle, noOpAngle, casMismatch;
        UnaryOperator<CdObjectState> drawRewrite = UnaryOperator.identity();
        Harness(Terrain terrain) {
            placement.resetSavedObjectFlags();
            queue = new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
                @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                    var s = pool().state(ref.wholeIndex());
                    return new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(), s.word(X_FRACTION), s.longBits(MAPPINGS), s.unsignedByte(MAP_FRAME), s.word(TILE));
                }
                @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                    var s = pool().state(ref.wholeIndex()); pool().write(s.moveByte(SPRITE_FLAGS, value ? s.spriteFlags() | 128 : s.spriteFlags() & 127));
                }
            }, (address, staticPiece, frame) -> {
                assertEquals(MAP, address); assertFalse(staticPiece);
                var pieces = assets().visual().mappingFrames().get(frame).pieces().stream()
                        .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
                return new CdObjectDrawQueue.FrameMapping(pieces.size(), pieces);
            }, new byte[640]);
            queue.beginPass(); body = new CdAnton(placement, assets(), terrain.build(), this::angle, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> change) { pool().write(change.apply(slot())); }
        CdAnton.Globals globals() { return new CdAnton.Globals(good, time, direction, camera, angle); }
        CdAnton.Result run() {
            var r = body.run(WHOLE, globals());
            for (var e : r.effects()) if (e instanceof CdAnton.FloorReturned f) floors.add(f.result());
            return r;
        }
        void nextPass() { finishQueue(); queue.beginPass(); }
        List<Integer> angleValues() { return angleCalls.stream().map(CdAnton.PrimaryAngleCall::value).toList(); }
        void angle(CdAnton.PrimaryAngleCall call) {
            pool().validateRestore(call.callCut()); assertEquals(pool().capture().slots(), call.callCut().slots());
            assertEquals(slot(), call.expectedSource()); angleCalls.add(call);
            if (casMismatch || angle != call.expectedPrimaryAngle()) throw fault;
            if (reenterAngle) run(); if (angleCalls.size() == rejectAngle) throw fault;
            if (!noOpAngle) angle = call.value();
            if (mutateAngle == 1) edit(s -> s.moveByte(FLAGS, 0xA5));
            if (mutateAngle == 2) pool().write(pool().state(41).moveByte(ID, 0xA6));
            if (foreignAngle) pool().write(new CdObjectPool().state(WHOLE));
        }
        void draw(CdObjectPool actual, CdObjectState.Identity identity) {
            assertSame(pool(), actual); assertEquals(WHOLE, identity.wholeIndex()); var s = slot(); draws.add(s);
            enqueues.add(queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(WHOLE), s.spriteFlags(), s.centreX(), s.centreY(),
                    s.unsignedByte(Y_RADIUS), s.unsignedByte(WIDTH), s.unsignedByte(PRIORITY)), new CdObjectDrawQueue.CameraWords(camera, 0x700)));
            if (rejectDraw) throw fault; edit(drawRewrite);
        }
        CdObjectDrawQueue.CpuSat finishQueue() {
            var c = new CdObjectDrawQueue.CameraWords(camera, 0x700); return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(c, c, c));
        }
    }
}
