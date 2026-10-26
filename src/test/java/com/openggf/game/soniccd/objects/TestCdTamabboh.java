package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests FIRST; every test, discovery, RED and compilation is UNRUN.
 * J 62607759799bf53906fffdcb3f2896038d126418; N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Tamabboh/Main.asm:7-282; Object Animate.asm:14-95; Object Time Check.asm:12-44;
 * Collision Check.asm:157-179; Collision Floor.asm:685-856; Object Spawner.asm:329-446;
 * Object Functions.asm:137-196,228-365. No FixBugs repair.
 * Real placement/pool, immutable terrain, shared floor/animator/mapping/draw/touch components.
 * Constructed formats authenticate NO media, Frame, runtime invocation, route or native parity.
 * The semantic test player is stable per invocation. Root owns that actual Frame read/CAS binding,
 * phase/full96/source authenticity, FM DEV ledger and failure closure. FM callbacks below are
 * ordered metadata observations, never the native three-byte queue or playback.
 * Full64 dirty checks use public typed fields; inaccessible bytes remain genuine fresh-pool zero.
 * C29/$440 picture admission, runtime joins and actual call3222 continuation are outside this file.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdTamabboh {
    static final int WHOLE = 40, MAP = 0x8123B968, DAMAGED_MAP = MAP + 22,
            MISSILE_MAP = 0x9223BA70, NORMAL_ANI = 0xA123B942, DAMAGED_ANI = 0xB123B94E;
    static final String TILES = "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
            + "23a023b024092370200024284334032000004000400004090374832800000000049003e40000";
    static final String BODY_MAP = "00060022003203f10d0000f0010c0008f00908000cf80006000c001c01f00f002bf0"
            + "03f20d0000f0020c000ff00a080013f802f0080016f7f80e0019ef00";
    static final String MISSILE_MAP_BYTES = "0008000e0014001f01fc000025fc01fc000026fc02f8040027f800041027f8"
            + "02f8040029f800041029f8";
    static final String NORMAL_SCRIPT = "000400081d0001ff3b02fc00";
    static final String DAMAGED_SCRIPT = "000400160500010001000100010001000100010001fc0b02fc00";
    static final String MISSILE_SCRIPT = "00020200010203ff";

    @Test void nineStorageAliasesWriteOnlyTheirNativeBytesIncludingOverlaps() {
        var h = ready(8); byte[] expected = h.slot().copyBytes();
        h.edit(s -> s.moveWord(TAMABBOH_ANCHOR_X, 0x1234).moveLong(TAMABBOH_X_DELTA, 0x56789ABC)
                .moveLong(TAMABBOH_ANIMATION_SCRIPT, 0xDEF01234).moveWord(TAMABBOH_COOLDOWN, 0x5678)
                .moveLong(TAMABBOH_MISSILE_X_DELTA, 0x11223344).moveLong(TAMABBOH_MISSILE_Y_DELTA, 0x55667788)
                .moveLong(TAMABBOH_MISSILE_X_ACCEL, 0x99AABBCC).moveLong(TAMABBOH_MISSILE_Y_ACCEL, 0xDDEEFF00)
                .moveByte(TAMABBOH_MISSILE_DIRECTION, 0xA5));
        w(expected, 0x2A, 0x1234); l(expected, 0x2C, 0x56789ABC); l(expected, 0x30, 0xDEF01234);
        w(expected, 0x34, 0x5678); l(expected, 0x2A, 0x11223344); l(expected, 0x2E, 0x55667788);
        l(expected, 0x32, 0x99AABBCC); l(expected, 0x36, 0xDDEEFF00); b(expected, 0x3F, 0xA5);
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(0x1122, h.slot().word(TAMABBOH_ANCHOR_X));
        assertEquals(0x778899AA, h.slot().longBits(TAMABBOH_ANIMATION_SCRIPT));
        assertEquals(0xBBCC, h.slot().word(TAMABBOH_COOLDOWN));
    }

    @Test void constructedAssetsRetainBothFullRawBodyViewsAndAllMissileAttributesWithoutAuthentication() {
        var a = assets(); assertEquals(74, a.objectTileIds().length());
        assertArrayEquals(hex(BODY_MAP), a.normal().mappings().copy());
        assertArrayEquals(hex(BODY_MAP), a.damaged().mappings().copy());
        assertEquals(0, a.normal().mappingTableOffset()); assertEquals(22, a.damaged().mappingTableOffset());
        assertArrayEquals(hex(NORMAL_SCRIPT), a.normal().animationTable().copy());
        assertArrayEquals(hex(DAMAGED_SCRIPT), a.damaged().animationTable().copy());
        assertArrayEquals(hex(MISSILE_SCRIPT), a.missile().animationTable().copy());
        assertEquals(0x1027, a.missile().mappingFrames().get(2).pieces().get(1).rawTileWord());
        assertEquals(31, a.missile().mappingFrames().get(3).relativeOffset());
        assertEquals(0x2428, CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(), 4, 0));
        assertThrows(RuntimeException.class, () -> CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(), 4, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdTamabboh.Assets(CdAssetBytes.copyOf(new byte[73]),
                a.normal(), a.damaged(), a.missile(), MAP, DAMAGED_MAP, MISSILE_MAP, NORMAL_ANI, DAMAGED_ANI));
        byte[] tooWide = hex(MISSILE_MAP_BYTES); tooWide[12] = 59;
        var visual = CdObjectVisualAssets.decode(tooWide, 4, hex(MISSILE_SCRIPT), 1, 60);
        assertThrows(IllegalArgumentException.class, () -> new CdTamabboh.Assets(a.objectTileIds(), a.normal(),
                a.damaged(), visual, MAP, DAMAGED_MAP, MISSILE_MAP, NORMAL_ANI, DAMAGED_ANI));
    }

    @ParameterizedTest @CsvSource({"0,0,0", "0,255,0", "1,1,0", "1,0,255", "1,2,0", "1,1,255"})
    void goodFutureRunsBeforeBodyDispatchAndConversionRestoresOnlyCoordinateWords(int good, int time, int subtype) {
        var h = ready(255); h.good = good; h.time = time;
        h.edit(s -> dirty(s).moveByte(ID, 0x22).moveByte(ROUTINE, 255).moveByte(SUBTYPE, subtype)
                .moveByte(SPRITE_FLAGS, 0xFF).moveLong(X_POSITION, 0x1234ABCD).moveLong(Y_POSITION, 0x5678EF90));
        byte[] bank = h.placement.capture().savedFlags().copy(); byte[] before = h.slot().copyBytes();
        boolean converts = good != 0 && !(time == 1 && subtype == 0);
        if (converts) {
            byte[] expected = new byte[64]; b(expected, 0, 0x18); w(expected, 8, 0x1234); w(expected, 12, 0x5678);
            var r = h.run(); assertArrayEquals(expected, r.state().copyBytes()); assertTrue(r.deleted());
            assertTrue(r.effects().stream().anyMatch(CdTamabboh.ConvertedToExplosion.class::isInstance));
        } else {
            var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, h::run);
            assertEquals(CdTamabboh.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary()); assertArrayEquals(before, h.slot().copyBytes());
        }
        assertTrue(h.draws.isEmpty()); assertTrue(h.fmCalls.isEmpty()); assertTrue(h.angleCalls.isEmpty());
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 5, 7, 9, 11, 12, 128, 255})
    void unknownBodyRoutineStopsAtRawDispatchAfterReturningTimeHelper(int routine) {
        var h = ready(routine); byte[] before = h.slot().copyBytes();
        var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, h::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
        assertEquals(routine, e.value()); assertEquals(WHOLE, e.wholeIndex());
        assertArrayEquals(before, h.slot().copyBytes()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, 128, 255})
    void bodyInitDirty64PublishesNativePrefixThenPositionWithoutAnimatorReset(int subtype) {
        var h = ready(0);
        h.edit(s -> dirty(s).moveByte(ID, 0x22).moveByte(ROUTINE, 0).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, 0)
                .moveByte(SAVED_FLAGS_ID, 0).moveByte(SPRITE_FLAGS, 0x81).moveByte(FLAGS, 0xA2)
                .moveLong(X_POSITION, 0x0271ABCD).moveLong(Y_POSITION, 0x0777BEEF));
        byte[] expected = h.slot().copyBytes(); b(expected, 1, 5); w(expected, 2, 0x2428);
        l(expected, 4, subtype == 0 ? MAP : DAMAGED_MAP); l(expected, 12, 0x0778BEEF);
        b(expected, 0x16, 15); b(expected, 0x17, 16); b(expected, 0x18, 4); b(expected, 0x19, 16);
        b(expected, 0x20, 0x2C); b(expected, 0x24, 2); w(expected, 0x2A, 0x271);
        l(expected, 0x2C, subtype == 0 ? -0xA000 : -0x5000); l(expected, 0x30, subtype == 0 ? NORMAL_ANI : DAMAGED_ANI);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes());
        assertEquals(List.of(0, 0x22), h.angleValues()); assertEquals(0, count(r, CdTamabboh.NativeCall.ANIMATE_OBJECT));
        assertEquals(0x0778BEEF, h.angleCalls.getFirst().expectedSource().longBits(Y_POSITION));
        assertEquals(1, count(r, CdTamabboh.NativeCall.DRAW_OBJECT));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 255})
    void unsupportedSecondaryStopsAfterBodyInitPrefixBeforeTileMapScriptMotionOrFloor(int secondary) {
        var h = ready(0); h.edit(s -> s.moveByte(SUBTYPE2, secondary).moveByte(SPRITE_FLAGS, 0x81));
        byte[] expected = h.slot().copyBytes(); b(expected, 1, 0x85); b(expected, 0x18, 4); b(expected, 0x20, 0x2C);
        b(expected, 0x17, 16); b(expected, 0x19, 16); b(expected, 0x16, 15); b(expected, 0x24, 2); w(expected, 0x2A, 0x271);
        var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, h::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.TILE_ENTRY, e.boundary());
        assertArrayEquals(expected, h.slot().copyBytes()); assertTrue(h.angleCalls.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @CsvSource({"1911,2", "1912,4"})
    void positionAddsWholeLongYAndOnlyNegativeFloorAdvancesRoutine(int yBefore, int routine) {
        var h = ready(2); h.edit(s -> s.moveWord(Y, yBefore));
        var r = h.run(); assertEquals(yBefore + 1, r.state().centreY()); assertEquals(0xBEEF, r.state().word(Y_FRACTION));
        assertEquals(routine, r.state().routine()); assertEquals(0, count(r, CdTamabboh.NativeCall.ANIMATE_OBJECT));
        assertEquals(0x02710000, r.state().longBits(X_POSITION));
    }

    @Test void positionLongYWrapsWithoutSnappingAndPreservesFraction() {
        var h = ready(2); h.edit(s -> s.moveLong(Y_POSITION, 0xFFFFBEEF)); byte[] expected = h.slot().copyBytes();
        l(expected, 12, 0x0000BEEF); assertArrayEquals(expected, h.run().state().copyBytes()); assertEquals(List.of(0), h.angleValues());
    }

    @ParameterizedTest @CsvSource({"-81,0,false,1", "-80,0,true,2", "79,0,true,2", "80,0,false,1",
            "0,-81,false,2", "0,-80,true,2", "0,79,true,2", "0,80,false,2"})
    void rangeUsesWordWrapAndReachedYBeforeConditionalX(int dy, int dx, boolean inside, int reads) {
        var h = ready(4); h.debug = 0; h.py = (h.slot().centreY() + dy) & 0xFFFF; h.px = (h.slot().centreX() + dx) & 0xFFFF;
        byte[] before = h.slot().copyBytes(); var r = h.run();
        assertEquals(reads, h.playerCalls.size()); assertEquals(CdTamabboh.PlayerField.Y_WORD, h.playerCalls.getFirst().field());
        if (reads == 2) assertEquals(CdTamabboh.PlayerField.X_WORD, h.playerCalls.get(1).field());
        assertEquals(inside ? 6 : 4, r.state().routine());
        if (inside) {
            b(before, 0x24, 6); assertArrayEquals(before, r.state().copyBytes());
            assertTrue(h.angleCalls.isEmpty()); assertEquals(0, count(r, CdTamabboh.NativeCall.ANIMATE_OBJECT));
        }
    }

    @Test void rangeCoordinatesWrapAcrossZeroAndPlayerFailureRetainsReachedPrefix() {
        var h = ready(4); h.debug = 0; h.edit(s -> s.moveWord(X, 0xFFF0).moveWord(Y, 0xFFF0));
        h.px = 0x003F; h.py = 0xFFA0; assertEquals(6, h.run().state().routine());
        var fail = ready(4); fail.debug = 0; fail.py = fail.slot().centreY(); fail.rejectPlayer = 2;
        byte[] before = fail.slot().copyBytes(); assertSame(fail.fault, assertThrows(Fault.class, fail::run));
        assertArrayEquals(before, fail.slot().copyBytes()); assertEquals(2, fail.playerCalls.size()); assertTrue(fail.draws.isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,0,1,0", "1,0,7,7", "0,255,7,7"})
    void cooldownOneSkipsRangeAfterDecrementAndDebugOrDamagedLeavesItUntouched(int debug, int subtype, int old, int after) {
        var h = ready(4); h.debug = debug; h.edit(s -> s.moveByte(SUBTYPE, subtype).moveWord(TAMABBOH_COOLDOWN, old));
        var r = h.run(); assertEquals(after, r.state().word(TAMABBOH_COOLDOWN)); assertTrue(h.playerCalls.isEmpty());
        assertEquals(4, r.state().routine()); assertEquals(1, count(r, CdTamabboh.NativeCall.OBJ_GET_FLOOR_DIST));
    }

    @Test void anchorEqualityTurnsBeforeFloorAndBackedgeRepeatsCooldownAndRange() {
        var h = ready(4); h.debug = 0; h.edit(s -> s.moveWord(X, 0x2EF).moveWord(TAMABBOH_ANCHOR_X, 0x270)
                .moveLong(TAMABBOH_X_DELTA, 0x10000).moveWord(TAMABBOH_COOLDOWN, 1));
        h.py = h.slot().centreY(); h.px = 0x2F0;
        var r = h.run(); assertEquals(6, r.state().routine()); assertEquals(0x02F00000, r.state().longBits(X_POSITION));
        assertEquals(-0x10000, r.state().longBits(TAMABBOH_X_DELTA)); assertEquals(1, r.state().flags());
        assertEquals(2, h.playerCalls.size()); assertTrue(h.angleCalls.isEmpty());
        var twice = ready(4); twice.debug = 0; twice.edit(s -> s.moveWord(X, 0x2EF).moveWord(TAMABBOH_ANCHOR_X, 0x270)
                .moveLong(TAMABBOH_X_DELTA, 0x10000).moveWord(TAMABBOH_COOLDOWN, 2));
        assertEquals(0, twice.run().state().word(TAMABBOH_COOLDOWN)); assertTrue(twice.playerCalls.isEmpty());
    }

    @Test void negWord8000RemainsNegativeAndStoredLongDeltaIsUsedWithoutSubtypeInference() {
        var h = ready(4); h.edit(s -> s.moveWord(TAMABBOH_ANCHOR_X, 0x8271).moveLong(TAMABBOH_X_DELTA, 0));
        h.drawRewrite = s -> s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 128); // retain distance-independent wrapper path
        var r = h.run(); assertEquals(0, r.state().flags()); assertEquals(2, h.angleCalls.size());
        var delta = ready(4); delta.edit(s -> s.moveByte(SUBTYPE, 255).moveLong(TAMABBOH_X_DELTA, -0xA000));
        assertEquals(0x02706000, delta.run().state().longBits(X_POSITION));
    }

    @ParameterizedTest @CsvSource({"1905,7", "1919,-7"})
    void inclusiveSevenFloorCorrectionChangesYWordOnly(int y, int distance) {
        var h = ready(4); h.edit(s -> s.moveWord(Y, y)); var r = h.run();
        assertEquals(distance, h.floors.getFirst().distance()); assertEquals(1912, r.state().centreY());
        assertEquals(0xBEEF, r.state().word(Y_FRACTION));
    }

    @ParameterizedTest @CsvSource({"1920,8,7,-8,-7", "1905,7,8,8,7"})
    void rejectedEightTurnsThenAcceptsSecondFloorAndPublishesAnglesInOrder(int y, int firstHeight, int secondHeight, int rejected, int accepted) {
        var t = new Terrain().shape(7, firstHeight, 0x22).shape(8, secondHeight, 0x34).block(8, 8, 0x2002);
        var h = ready(4, t); h.edit(s -> s.moveWord(X, 0x280).moveWord(TAMABBOH_ANCHOR_X, 0x280)
                .moveWord(Y, y).moveLong(TAMABBOH_X_DELTA, -0x10000));
        var r = h.run(); assertEquals(0x02800000, r.state().longBits(X_POSITION)); assertEquals(y + accepted, r.state().centreY());
        assertEquals(0x10000, r.state().longBits(TAMABBOH_X_DELTA)); assertEquals(1, r.state().flags());
        assertEquals(List.of(0, 0x22, 0, 0x34), h.angleValues());
        assertEquals(rejected, h.floors.get(0).distance()); assertEquals(accepted, h.floors.get(1).distance());
    }

    @Test void trueRangeCycleIsExplicitHostNonReturnWithFullImageAngleAndReadContextAndNoDraw() {
        var h = ready(4); h.edit(s -> s.moveLong(X_POSITION, 0x1080A000).moveWord(TAMABBOH_ANCHOR_X, 0x1000));
        var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, h::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE, e.boundary());
        assertTrue(e.getMessage().contains("HOST")); assertTrue(e.getMessage().contains(HexFormat.of().formatHex(h.slot().copyBytes())));
        assertTrue(e.getMessage().contains("primaryAngle")); assertTrue(e.getMessage().contains("reachedPlayerReads"));
        assertTrue(h.draws.isEmpty()); assertTrue(h.angleCalls.isEmpty()); assertTrue(h.playerCalls.isEmpty());
    }

    @ParameterizedTest @CsvSource({"8193,0:34", "10241,0:34:222", "12289,0:34:98:158:94:0", "14337,0:34:222:30:226:162:0"})
    void sharedFloorForwardsEveryReachedAngleStoreSynchronously(int word, String values) {
        var h = ready(2, new Terrain().block(7, 8, word)); h.edit(s -> s.moveWord(Y, 0x777));
        h.run(); assertEquals(parse(values), h.angleValues());
        for (var call : h.angleCalls) assertEquals(0x0778BEEF, call.expectedSource().longBits(Y_POSITION));
    }

    @Test void sameValueAngleStoresAndOddAngleReturnArePreserved() {
        var zero = ready(2, new Terrain().shape(7, 8, 0)); zero.angle = 0; zero.edit(s -> s.moveWord(Y, 0x777));
        zero.run(); assertEquals(List.of(0, 0), zero.angleValues());
        var odd = ready(2, new Terrain().shape(7, 8, 0x23)); odd.edit(s -> s.moveWord(Y, 0x777));
        odd.run(); assertEquals(List.of(0, 0x23), odd.angleValues()); assertEquals(0x23, odd.angle);
        assertEquals(0, odd.floors.getFirst().angleResult());
    }

    @Test void terrainFirstAndAdjacentFaultsKeepMotionAndReachedGlobalPrefixes() {
        var first = ready(2, new Terrain().flagged(7, 2, 121)); first.edit(s -> s.moveByte(SPRITE_FLAGS, 0x44).moveWord(Y, 0x777));
        byte[] expected = first.slot().copyBytes(); w(expected, 12, 0x778);
        assertThrows(IllegalStateException.class, first::run); assertArrayEquals(expected, first.slot().copyBytes());
        assertEquals(List.of(0), first.angleValues()); assertTrue(first.draws.isEmpty());
        var adjacent = ready(2, new Terrain().cell(6, 2, 2).flagged(7, 2, 121).block(7, 15, 0x2001).shape(7, 0, 0x25));
        adjacent.edit(s -> s.moveByte(SPRITE_FLAGS, 0x44).moveWord(Y, 0x6E8));
        assertThrows(IllegalStateException.class, adjacent::run); assertEquals(0x6E9, adjacent.slot().centreY());
        assertEquals(List.of(0, 0x25), adjacent.angleValues()); assertEquals(2, adjacent.slot().routine()); assertTrue(adjacent.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void angleFaultAtEachReachedStoreRetainsOnlyEarlierStoresAndNativeSlotPrefix(int at) {
        var h = ready(2, new Terrain().block(7, 8, 0x3801)); h.edit(s -> s.moveWord(Y, 0x777)); h.rejectAngle = at;
        byte[] expected = h.slot().copyBytes(); w(expected, 12, 0x778);
        assertSame(h.fault, assertThrows(Fault.class, h::run)); assertArrayEquals(expected, h.slot().copyBytes());
        assertEquals(at, h.angleCalls.size()); int[] previous = {0x55, 0, 0x22, 0xDE, 0x1E, 0xE2, 0xA2};
        assertEquals(previous[at - 1], h.angle); assertTrue(h.draws.isEmpty());
    }

    @Test void waitOneFallsIntoWaitTwoSameCallAndFcAdvancesWithoutRedispatch() {
        var h = ready(6); h.edit(s -> s.moveByte(ANIMATION, 0).moveByte(PREVIOUS_ANIMATION, 0)
                .moveByte(ANIMATION_FRAME, 0xFF).moveByte(ANIMATION_TIME, 77));
        var r = h.run(); assertEquals(8, r.state().routine()); assertEquals(1, r.state().unsignedByte(ANIMATION));
        assertEquals(1, r.state().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(1, r.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(59, r.state().unsignedByte(ANIMATION_TIME)); assertEquals(2, r.state().unsignedByte(MAP_FRAME));
        h.edit(s -> s.moveByte(ANIMATION_TIME, 0)); var fc = h.run(); assertEquals(10, fc.state().routine());
        assertEquals(59, fc.state().unsignedByte(ANIMATION_TIME)); assertTrue(h.fmCalls.isEmpty());
        assertEquals(0, count(fc, CdTamabboh.NativeCall.FIND_OBJ_SLOT));
    }

    @Test void damagedWalkFcThenWaitAndFireUseSharedAnimatorAndNeverAllocate() {
        var h = ready(4); h.edit(s -> s.moveByte(SUBTYPE, 255).moveLong(TAMABBOH_ANIMATION_SCRIPT, DAMAGED_ANI)
                .moveLong(TAMABBOH_X_DELTA, 0).moveByte(ANIMATION_TIME, 0).moveByte(ANIMATION_FRAME, 16));
        assertEquals(6, h.run().state().routine()); assertEquals(5, h.slot().unsignedByte(ANIMATION_TIME));
        assertEquals(8, h.run().state().routine()); assertEquals(11, h.slot().unsignedByte(ANIMATION_TIME));
        h.edit(s -> s.moveByte(ANIMATION_TIME, 0)); assertEquals(10, h.run().state().routine());
        var fire = h.run(); assertEquals(4, fire.state().routine()); assertEquals(0x78, fire.state().word(TAMABBOH_COOLDOWN));
        assertTrue(h.fmCalls.isEmpty()); assertEquals(0, count(fire, CdTamabboh.NativeCall.FIND_OBJ_SLOT));
    }

    @Test void liveFullScriptPointerSelectsNormalEvenForDamagedSubtypeAndRejectsUnownedOnlyAtReachedRead() {
        var h = ready(8); h.edit(s -> s.moveByte(SUBTYPE, 255).moveByte(ANIMATION_TIME, 0));
        assertEquals(29, h.run().state().unsignedByte(ANIMATION_TIME));
        var held = ready(8); held.edit(s -> s.moveLong(TAMABBOH_ANIMATION_SCRIPT, NORMAL_ANI ^ 0x01000000).moveByte(ANIMATION_TIME, 1));
        assertEquals(0, held.run().state().unsignedByte(ANIMATION_TIME));
        var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, held::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.ANIMATION_POINTER, e.boundary());
        assertEquals(255, held.slot().unsignedByte(ANIMATION_TIME));
        var reset = ready(8); reset.edit(s -> s.moveLong(TAMABBOH_ANIMATION_SCRIPT, 0).moveByte(ANIMATION, 1)
                .moveByte(PREVIOUS_ANIMATION, 0).moveByte(ANIMATION_FRAME, 231).moveByte(ANIMATION_TIME, 77));
        assertThrows(CdTamabboh.UnsupportedTamabboh.class, reset::run);
        assertEquals(1, reset.slot().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0, reset.slot().unsignedByte(ANIMATION_FRAME));
        assertEquals(255, reset.slot().unsignedByte(ANIMATION_TIME)); assertTrue(reset.draws.isEmpty());
    }

    @Test void invalidDirectoryAndFrameIndexFailAfterTheirExactResetSubqOrSpeedPrefixes() {
        var directory = ready(8); directory.edit(s -> s.moveByte(ANIMATION, 2));
        var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, directory::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY, e.boundary());
        assertEquals(2, directory.slot().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(255, directory.slot().unsignedByte(ANIMATION_TIME));
        var frame = ready(8); frame.edit(s -> s.moveByte(ANIMATION_FRAME, 255).moveByte(ANIMATION_TIME, 0));
        var f = assertThrows(CdTamabboh.UnsupportedTamabboh.class, frame::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, f.boundary());
        assertEquals(29, frame.slot().unsignedByte(ANIMATION_TIME)); assertEquals(255, frame.slot().unsignedByte(ANIMATION_FRAME));
        assertTrue(frame.draws.isEmpty());
    }

    @Test void ffLoopAndHeldTimerMatchSharedTransitionFromOriginalImageOnce() {
        var h = ready(8); h.edit(s -> s.moveByte(ANIMATION_FRAME, 2).moveByte(ANIMATION_TIME, 0).moveByte(FLAGS, 3));
        var old = h.slot(); var expected = CdObjectAnimation.step(animation(old), new CdObjectAnimation.AnimationTable(assets().normal().animationTable(), 0, 2));
        var r = h.run(); assertAnimation(expected, r.state()); assertEquals(1, r.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(0, r.state().unsignedByte(MAP_FRAME)); assertEquals(3, r.state().spriteFlags() & 3);
        var hold = ready(8); hold.edit(s -> s.moveByte(ANIMATION_TIME, 1).moveByte(MAP_FRAME, 0xE7));
        assertEquals(0xE7, hold.run().state().unsignedByte(MAP_FRAME)); assertEquals(0, hold.slot().unsignedByte(ANIMATION_TIME));
    }

    @ParameterizedTest @CsvSource({"0,0", "1,1", "2,2"})
    void fireCapacityAndNegativePriorSignRequestOneFmBeforeFirstChildWrite(int free, int children) {
        var h = ready(10); h.fill(); if (free > 0) h.pool().objNull(33); if (free > 1) h.pool().objNull(41);
        h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x84)); var before = h.pool().capture(); var r = h.run();
        assertEquals(4, r.state().routine()); assertEquals(0, r.state().unsignedByte(ANIMATION)); assertEquals(0x78, r.state().word(TAMABBOH_COOLDOWN));
        assertEquals(free == 0 ? 0 : 1, h.fmCalls.size());
        assertEquals(children, r.effects().stream().filter(CdTamabboh.MissileCreated.class::isInstance).count());
        if (free > 0) {
            var call = h.fmCalls.getFirst(); assertEquals(33, call.firstFreeWholeIndex()); assertEquals(CdTamabboh.FmSound.FM_A0, call.sound());
            assertEquals(0, call.callCut().slots().get(1).id()); assertEquals(4, call.expectedSource().routine());
            assertEquals(0x78, call.expectedSource().word(TAMABBOH_COOLDOWN)); assertEquals(0, h.pool().state(33).unsignedByte(TAMABBOH_MISSILE_DIRECTION));
        }
        if (free > 1) assertEquals(255, h.pool().state(41).unsignedByte(TAMABBOH_MISSILE_DIRECTION));
        for (int whole = 32; whole < 128; whole++) if (whole != WHOLE && !(free > 0 && whole == 33) && !(free > 1 && whole == 41))
            assertArrayEquals(before.slots().get(whole - 32).copyBytes(), h.pool().state(whole).copyBytes());
    }

    @Test void positivePriorSignSkipsOnlySoundAndDrawCannotRetroactivelyCauseIt() {
        var h = ready(10); h.fill(); h.pool().objNull(33); h.pool().objNull(41);
        h.drawRewrite = s -> s.moveByte(SPRITE_FLAGS, 0x84); var r = h.run();
        assertTrue(h.fmCalls.isEmpty()); assertEquals(0x22, h.pool().state(33).id()); assertEquals(0x22, h.pool().state(41).id());
        assertEquals(2, count(r, CdTamabboh.NativeCall.FIND_OBJ_SLOT));
    }

    @Test void firstFmFaultLeavesFirePrefixAndDirtyFreeChildCompletelyUntouched() {
        var h = ready(10); h.fill(); h.pool().write(dirty(h.pool().state(33)).moveByte(ID, 0)); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x84));
        byte[] child = h.pool().state(33).copyBytes(); h.rejectFm = true;
        assertSame(h.fault, assertThrows(Fault.class, h::run)); assertEquals(4, h.slot().routine());
        assertEquals(0x78, h.slot().word(TAMABBOH_COOLDOWN)); assertEquals(0, h.slot().unsignedByte(ANIMATION));
        assertArrayEquals(child, h.pool().state(33).copyBytes()); assertTrue(h.draws.isEmpty()); assertEquals(1, h.fmCalls.size());
    }

    @Test void dirtyIdZeroChildrenPreserveEveryUnwrittenByteFractionRoutineAndSavedId() {
        var h = ready(10); h.fill(); h.edit(s -> s.moveByte(PRIORITY, 255).moveWord(Y, 5));
        for (int whole : new int[]{33, 41}) h.pool().write(dirty(h.pool().state(whole)).moveByte(ID, 0));
        byte[] first = h.pool().state(33).copyBytes(), second = h.pool().state(41).copyBytes();
        for (byte[] expected : new byte[][]{first, second}) {
            b(expected, 0, 0x22); b(expected, 0x28, 1); w(expected, 2, 0x2428); b(expected, 0x18, 0);
            w(expected, 8, 0x271); w(expected, 12, 0xFFFB);
        }
        b(first, 0x3F, 0); b(second, 0x3F, 255); h.run();
        assertArrayEquals(first, h.pool().state(33).copyBytes()); assertArrayEquals(second, h.pool().state(41).copyBytes());
        assertNotEquals(0, h.pool().state(33).routine()); assertNotEquals(0, h.pool().state(33).savedFlagsId());
    }

    @Test void publicLivePoolWalkVisitsLaterChildThisPassAndLowerChildOnlyNextPass() {
        var h = ready(10); h.fill(); h.pool().deleteObject(33); h.pool().deleteObject(41);
        for (int whole = 32; whole < 128; whole++) if (h.pool().state(whole).id() == 0x22) h.run(whole);
        assertEquals(0, h.pool().state(33).routine()); assertEquals(2, h.pool().state(41).routine());
        assertEquals(1, h.pool().state(33).subtype()); assertEquals(1, h.pool().state(41).subtype());
        assertEquals(MISSILE_MAP, h.pool().state(41).longBits(MAPPINGS));
        h.run(33); assertEquals(2, h.pool().state(33).routine());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 128, 255})
    void subtypeOneMissileInitBypassesGoodFutureAndTailRetainingDirtyFieldsWithoutFallthrough(int direction) {
        var h = ready(0); h.good = 1; h.time = 0xFF; h.edit(s -> dirty(s).moveByte(ID, 0x22).moveByte(SUBTYPE, 1)
                .moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 0x81).moveByte(TAMABBOH_MISSILE_DIRECTION, direction));
        byte[] expected = h.slot().copyBytes(); b(expected, 1, 5); b(expected, 0x24, 2); b(expected, 0x20, 0xAD);
        b(expected, 0x16, 8); b(expected, 0x17, 8); b(expected, 0x19, 8); l(expected, 4, MISSILE_MAP);
        l(expected, 0x32, 0); l(expected, 0x36, 0x2000); l(expected, 0x2A, direction == 0 ? 0x20000 : -0x20000); l(expected, 0x2E, -0x40000);
        var r = h.run(); assertArrayEquals(expected, r.state().copyBytes()); assertTrue(h.angleCalls.isEmpty());
        assertEquals(0, count(r, CdTamabboh.NativeCall.DESTROY_ON_GOOD_FUTURE));
        assertEquals(0, count(r, CdTamabboh.NativeCall.CHECK_OBJ_DESPAWN)); assertEquals(0, count(r, CdTamabboh.NativeCall.ANIMATE_OBJECT));
    }

    @ParameterizedTest @ValueSource(ints = {4, 6, 255})
    void missileRawRoutineDirectoryRejectsUnownedEntryWithoutBodyTimeCheck(int routine) {
        var h = ready(routine); h.good = 1; h.time = 0; h.edit(s -> s.moveByte(SUBTYPE, 1));
        var before = h.slot().copyBytes(); var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, h::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary()); assertArrayEquals(before, h.slot().copyBytes());
    }

    @ParameterizedTest @ValueSource(ints = {0, 255})
    void missileBallisticsUseFourLongsAndExistingDrawVisibilityBetweenVisits(int direction) {
        var h = ready(0, new Terrain().clear()); h.camera = 0x1200; h.cameraY = 0x200;
        h.edit(s -> s.moveByte(SUBTYPE, 1).moveByte(TAMABBOH_MISSILE_DIRECTION, direction)
                .moveLong(X_POSITION, 0x12100000).moveLong(Y_POSITION, 0x02070000));
        h.run(); assertEquals(0x02070000, h.slot().longBits(Y_POSITION)); h.nextPass();
        var first = h.run(); assertEquals(direction == 0 ? 0x12120000 : 0x120E0000, first.state().longBits(X_POSITION));
        assertEquals(0x02030000, first.state().longBits(Y_POSITION)); assertEquals(0xFFFC2000, first.state().longBits(TAMABBOH_MISSILE_Y_DELTA));
        h.nextPass(); var second = h.run(); assertEquals(direction == 0 ? 0x12140000 : 0x120C0000, second.state().longBits(X_POSITION));
        assertEquals(0x01FF2000, second.state().longBits(Y_POSITION)); assertEquals(0xFFFC4000, second.state().longBits(TAMABBOH_MISSILE_Y_DELTA));
    }

    @Test void missileWrapsBothPositionLongsAndBothAccelerationsWithoutVelocityOrFloorCorrection() {
        var h = ready(2, new Terrain().clear()); h.edit(s -> s.moveByte(SUBTYPE, 1).moveByte(SPRITE_FLAGS, 0x84)
                .moveLong(X_POSITION, 0xFFFFFFF0).moveLong(Y_POSITION, 0xFFFFFFE0)
                .moveLong(TAMABBOH_MISSILE_X_DELTA, 0x20).moveLong(TAMABBOH_MISSILE_Y_DELTA, 0x40)
                .moveLong(TAMABBOH_MISSILE_X_ACCEL, 0x7FFFFFF0).moveLong(TAMABBOH_MISSILE_Y_ACCEL, 0x7FFFFFE0)
                .moveWord(X_VELOCITY, 0x1234).moveWord(Y_VELOCITY, 0x5678));
        var r = h.run(); assertEquals(0x10, r.state().longBits(X_POSITION)); assertEquals(0x20, r.state().longBits(Y_POSITION));
        assertEquals(0x80000010, r.state().longBits(TAMABBOH_MISSILE_X_DELTA)); assertEquals(0x80000020, r.state().longBits(TAMABBOH_MISSILE_Y_DELTA));
        assertEquals(0x1234, r.state().word(X_VELOCITY)); assertEquals(0x5678, r.state().word(Y_VELOCITY));
    }

    @ParameterizedTest @CsvSource({"4,false", "132,true"})
    void missileDeletesBeforeMotionForPriorVisibilityOrNegativeFloorButAlwaysDrawsActualIdZero(int flags, boolean floor) {
        var h = ready(2); h.edit(s -> s.moveByte(SUBTYPE, 1).moveByte(SPRITE_FLAGS, flags).moveByte(Y_RADIUS, 8)
                .moveWord(Y, 0x780).moveByte(SAVED_FLAGS_ID, 101));
        byte[] bank = h.placement.capture().savedFlags().copy(); var r = h.run();
        assertTrue(r.deleted()); assertArrayEquals(new byte[64], r.state().copyBytes()); assertEquals(1, h.draws.size());
        assertEquals(0, h.draws.getFirst().id()); assertEquals(floor ? 2 : 0, h.angleCalls.size());
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
        assertEquals(0, count(r, CdTamabboh.NativeCall.CHECK_OBJ_DESPAWN));
    }

    @Test void allThreeRawMappingRootsUseExistingResolverWithFullPointersAliasesAndNativeFlipWords() {
        var mappings = mappings();
        assertEquals(3, mappings.resolve(MAP, false, 0).pieces().size());
        assertEquals(1, mappings.resolve(DAMAGED_MAP, false, 0).pieces().size());
        assertEquals(mappings.resolve(MAP, false, 1), mappings.resolve(DAMAGED_MAP, false, 1));
        assertEquals(0x1027, mappings.resolve(MISSILE_MAP, false, 2).pieces().get(1).rawTileWord());
        assertEquals(0x1029, mappings.resolve(MISSILE_MAP, false, 3).pieces().get(1).rawTileWord());
        assertThrows(RuntimeException.class, () -> mappings.resolve(MAP & 0xFFFFFF, false, 0));
        assertThrows(RuntimeException.class, () -> mappings.resolve(MAP, false, 3));
        assertThrows(RuntimeException.class, () -> mappings.resolve(MISSILE_MAP, false, 4));
        assertThrows(RuntimeException.class, () -> mappings.resolve(MAP, true, 0));
    }

    @ParameterizedTest @CsvSource({"640,false", "768,true"})
    void bodyDespawnUsesLiveAnchorAfterDrawAndUnsignedStrictDistance(int difference, boolean deleted) {
        var h = ready(8); h.camera = 0x80;
        h.drawRewrite = s -> s.moveWord(TAMABBOH_ANCHOR_X, difference).moveByte(SPRITE_FLAGS, 4);
        assertEquals(deleted, h.run().deleted()); assertEquals(1, h.draws.size());
    }

    @Test void negativeDrawSignSkipsDistanceButWarpStillDespawnsAndSavedIdZeroSkipsBank() {
        var h = ready(8); h.drawRewrite = s -> s.moveWord(TAMABBOH_ANCHOR_X, 0xF000).moveByte(SPRITE_FLAGS, 0x84);
        assertFalse(h.run().deleted()); h.time = 0x80; assertTrue(h.run().deleted());
        assertArrayEquals(new byte[64], h.slot().copyBytes());
    }

    @ParameterizedTest @CsvSource({"3,2,0,8", "127,43,1,132", "254,1,2,7"})
    void savedRawPeriodUsesInBankLinearAliasesBeforeCanonicalAdapter(int raw, int id, int period, int index) {
        var h = ready(8); h.time = raw; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1));
        for (var bit : CdObjectPlacementRuntime.SavedBit.values()) h.placement.bsetSavedFlag(id, period, bit);
        int previous = h.placement.savedFlag(id, period); h.drawRewrite = s -> s.moveWord(TAMABBOH_ANCHOR_X, 0x1000);
        var r = h.run(); assertTrue(r.deleted()); assertEquals(previous & 127, h.placement.savedFlag(id, period));
        var effect = r.effects().stream().filter(CdTamabboh.SavedFlagChanged.class::isInstance).map(CdTamabboh.SavedFlagChanged.class::cast).findFirst().orElseThrow();
        assertEquals(index, effect.byteIndex()); assertEquals(previous, effect.previousValue());
    }

    @ParameterizedTest @CsvSource({"128,1,0", "128,255,1", "129,255,2", "255,128,2", "128,127,0"})
    void warpUsesSignedDirectionAndWordClampBeforeSavedAddress(int time, int direction, int period) {
        var h = ready(8); h.time = time; h.direction = direction; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1));
        h.placement.bsetSavedFlag(1, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.drawRewrite = s -> s.moveByte(SPRITE_FLAGS, 0x84);
        assertTrue(h.run().deleted()); assertEquals(0, h.placement.savedFlag(1, period));
    }

    @Test void savedLastOwnedByteWorksAndOutOfBoundsRetainsDrawAndObjectWithoutDelete() {
        var valid = ready(8); valid.time = 1; valid.edit(s -> s.moveByte(SAVED_FLAGS_ID, 254));
        valid.placement.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        valid.drawRewrite = s -> s.moveWord(TAMABBOH_ANCHOR_X, 0x1000); assertTrue(valid.run().deleted());
        assertEquals(0, valid.placement.savedFlag(254, 1));
        var bad = ready(8); bad.time = 2; bad.edit(s -> s.moveByte(SAVED_FLAGS_ID, 254));
        bad.drawRewrite = s -> s.moveWord(TAMABBOH_ANCHOR_X, 0x1000).moveByte(FLAGS, 0xA5);
        var e = assertThrows(CdTamabboh.UnsupportedTamabboh.class, bad::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, e.boundary()); assertEquals(766, e.value());
        assertEquals(0x22, bad.slot().id()); assertEquals(0xA5, bad.slot().flags()); assertEquals(1, bad.draws.size());
    }

    @Test void actualSharedTouchScannerSelectsEnemy2cAndHazardAdInPhysicalOrderWithoutFakeHandler() {
        var h = ready(8); var sizes = new ArrayList<CdR11aObjectAssets.TouchSize>();
        for (int i = 1; i <= 63; i++) sizes.add(new CdR11aObjectAssets.TouchSize(i == 44 ? 16 : i == 45 ? 4 : 0, i == 44 ? 16 : i == 45 ? 4 : 0));
        var scanner = new CdPlayerObjectTouch(sizes); var player = new CdPlayerObjectTouch.PlayerCut(0x271, 0x778, 19, 0, 0);
        h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x84).moveByte(COLLISION_TYPE, 0x2C));
        var body = scanner.scan(player, h.pool().capture()); assertEquals(CdPlayerObjectTouch.Kind.ENEMY, body.kind());
        assertTrue(body.changedSlot().isEmpty()); assertEquals(WHOLE, body.selectedSlot().orElseThrow().identity().wholeIndex());
        h.pool().write(h.pool().state(33).moveByte(ID, 0x22).moveByte(SUBTYPE, 1).moveByte(SPRITE_FLAGS, 0x84)
                .moveByte(COLLISION_TYPE, 0xAD).moveWord(X, 0x271).moveWord(Y, 0x778));
        var missile = scanner.scan(player, h.pool().capture()); assertEquals(CdPlayerObjectTouch.Kind.HAZARD, missile.kind());
        assertEquals(33, missile.selectedSlot().orElseThrow().identity().wholeIndex()); assertTrue(missile.changedSlot().isEmpty());
        h.pool().write(h.pool().state(33).moveByte(SPRITE_FLAGS, 4)); h.edit(s -> s.moveByte(COLLISION_TYPE, 0));
        assertEquals(CdPlayerObjectTouch.Kind.NONE, scanner.scan(player, h.pool().capture()).kind());
    }

    @ParameterizedTest @CsvSource({"ANGLE,1", "ANGLE,2", "PLAYER,1", "PLAYER,2", "FM,1", "FM,2"})
    void eachReadOnlyPortDetectsSourceOrOtherSlotMutationAndNeverOverwritesIt(String port, int mutation) {
        var h = portHarness(port); h.attackPort = port; h.mutation = mutation;
        assertThrows(IllegalStateException.class, h::run);
        if (mutation == 1) assertEquals(0xA5, h.slot().flags()); else assertEquals(0xA6, h.pool().state(42).id());
        assertTrue(h.draws.isEmpty()); if (port.equals("FM")) assertEquals(0, h.pool().state(33).id());
    }

    @ParameterizedTest @ValueSource(strings = {"ANGLE", "PLAYER", "FM"})
    void eachPortRejectsForeignImageAndReentryAtActualReachedCut(String port) {
        var foreign = portHarness(port); foreign.attackPort = port; foreign.foreign = true;
        assertThrows(IllegalArgumentException.class, foreign::run); assertTrue(foreign.draws.isEmpty());
        var nested = portHarness(port); nested.attackPort = port; nested.reenter = true;
        assertThrows(IllegalStateException.class, nested::run); assertTrue(nested.draws.isEmpty());
    }

    @Test void actualCutsAreFreshPerPortAndStaleForeignOrDetachedPublicValuesConferNoAuthority() {
        var h = ready(2); h.edit(s -> s.moveWord(Y, 0x777)); h.run();
        var first = h.angleCalls.get(0); var second = h.angleCalls.get(1);
        assertNotSame(first.callCut(), second.callCut()); assertEquals(96, first.callCut().slots().size());
        assertThrows(IllegalArgumentException.class, () -> h.pool().validateRestore(new CdObjectPool().capture()));
        var stale = h.pool().capture(); h.edit(s -> s.moveByte(FLAGS, s.flags() ^ 1));
        assertNotEquals(stale.slots(), h.pool().capture().slots());
        // Plain records can describe an old or equal cut. Only Root can prove invocation/Frame authority.
        var detached = new CdTamabboh.PrimaryAngleCall(first.callCut(), first.expectedSource(), first.expectedPrimaryAngle(), first.value());
        assertEquals(first, detached);
        assertThrows(IllegalArgumentException.class, () -> new CdTamabboh.PrimaryAngleCall(first.callCut(), new CdObjectPool().state(WHOLE), 0, 0));
    }

    @Test void noOpAnglePortCannotBeProvedToPublishByBodyValuesAndLaterMismatchFailsOnlyAtAdapter() {
        var same = ready(2, new Terrain().shape(7, 8, 0)); same.angle = 0; same.noOpAngle = true;
        same.edit(s -> s.moveWord(Y, 0x777)); same.run(); assertEquals(2, same.angleCalls.size());
        var mismatch = ready(2); mismatch.noOpAngle = true; mismatch.edit(s -> s.moveWord(Y, 0x777));
        assertSame(mismatch.fault, assertThrows(Fault.class, mismatch::run)); assertEquals(0x55, mismatch.angle);
        assertEquals(2, mismatch.angleCalls.size()); assertTrue(mismatch.draws.isEmpty());
    }

    @Test void portMutationOnThrowIsStillDetectedAndDrawFaultKeepsChildrenAndOwnCompletedWrites() {
        var h = portHarness("FM"); h.attackPort = "FM"; h.mutation = 2; h.throwAfterAttack = true;
        assertThrows(IllegalStateException.class, h::run); assertEquals(0xA6, h.pool().state(42).id()); assertEquals(0, h.pool().state(33).id());
        var draw = portHarness("FM"); draw.rejectDraw = true;
        assertSame(draw.fault, assertThrows(Fault.class, draw::run)); assertEquals(0x22, draw.pool().state(33).id());
        assertEquals(0x22, draw.pool().state(41).id()); assertEquals(4, draw.slot().spriteFlags()); assertEquals(1, draw.fmCalls.size());
    }

    @Test void ordinaryRunPreservesOther95SlotsSavedBankAndExistingCaptureRestoreBytes() {
        var h = ready(4); h.pool().write(dirty(h.pool().state(41)).moveByte(ID, 0x57));
        var cut = h.placement.capture(); var bank = cut.savedFlags().copy(); var r = h.run();
        for (int whole = 32; whole < 128; whole++) if (whole != WHOLE)
            assertArrayEquals(cut.pool().slots().get(whole - 32).copyBytes(), h.pool().state(whole).copyBytes());
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy()); h.placement.restore(cut);
        assertEquals(cut.pool().slots(), h.pool().capture().slots()); assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
    }

    static CdTamabboh.Assets assets() {
        var normal = CdObjectVisualAssets.decode(hex(BODY_MAP), 3, hex(NORMAL_SCRIPT), 2, 59, Map.of(0, 12));
        var damaged = CdObjectVisualAssets.decodeIndexed(hex(BODY_MAP), 22, 3, 28, 62, hex(DAMAGED_SCRIPT), 2, 59);
        var missile = CdObjectVisualAssets.decode(hex(MISSILE_MAP_BYTES), 4, hex(MISSILE_SCRIPT), 1, 59);
        return new CdTamabboh.Assets(CdAssetBytes.copyOf(hex(TILES)), normal, damaged, missile, MAP, DAMAGED_MAP, MISSILE_MAP, NORMAL_ANI, DAMAGED_ANI);
    }
    static CdObjectMappings mappings() {
        return CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(MAP, CdAssetBytes.copyOf(hex(BODY_MAP)), 0, 3),
                new CdObjectMappings.DeclaredTable(MAP, CdAssetBytes.copyOf(hex(BODY_MAP)), 22, 3),
                new CdObjectMappings.DeclaredTable(MISSILE_MAP, CdAssetBytes.copyOf(hex(MISSILE_MAP_BYTES)), 0, 4)), List.of());
    }
    static CdObjectAnimation.State animation(CdObjectState s) {
        return new CdObjectAnimation.State(s.unsignedByte(ANIMATION), s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME),
                s.unsignedByte(ANIMATION_TIME), s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2));
    }
    static void assertAnimation(CdObjectAnimation.State a, CdObjectState s) { assertEquals(a, animation(s)); }
    static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    static List<Integer> parse(String s) { return Arrays.stream(s.split(":" )).map(Integer::valueOf).toList(); }
    static void b(byte[] bytes, int at, int value) { bytes[at] = (byte) value; }
    static void w(byte[] bytes, int at, int value) { b(bytes, at, value >>> 8); b(bytes, at + 1, value); }
    static void l(byte[] bytes, int at, int value) { w(bytes, at, value >>> 16); w(bytes, at + 2, value); }
    static long count(CdTamabboh.Result r, CdTamabboh.NativeCall call) {
        return r.effects().stream().filter(CdTamabboh.CallReturned.class::isInstance).map(CdTamabboh.CallReturned.class::cast).filter(e -> e.call() == call).count();
    }
    static CdObjectState dirty(CdObjectState s) {
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0xA5);
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xB6C7);
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xD8E9FA0B);
        return s;
    }
    static Harness ready(int routine) { return ready(routine, new Terrain()); }
    static Harness ready(int routine, Terrain terrain) {
        var h = new Harness(terrain); h.edit(s -> s.moveByte(ID, 0x22).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 4)
                .moveWord(TILE, 0x2428).moveLong(MAPPINGS, MAP).moveLong(X_POSITION, 0x02710000).moveLong(Y_POSITION, 0x0778BEEF)
                .moveByte(X_RADIUS, 16).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 15).moveByte(PRIORITY, 4).moveByte(COLLISION_TYPE, 0x2C)
                .moveWord(TAMABBOH_ANCHOR_X, 0x271).moveLong(TAMABBOH_X_DELTA, -0xA000).moveLong(TAMABBOH_ANIMATION_SCRIPT, NORMAL_ANI)
                .moveByte(ANIMATION_TIME, 1)); return h;
    }
    static Harness portHarness(String port) {
        var h = ready(port.equals("ANGLE") ? 2 : port.equals("PLAYER") ? 4 : 10);
        if (port.equals("PLAYER")) { h.debug = 0; h.py = h.slot().centreY(); h.px = h.slot().centreX(); }
        if (port.equals("FM")) { h.fill(); h.pool().deleteObject(33); h.pool().deleteObject(41); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x84)); }
        return h;
    }
    static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50], ids = new byte[900];
        final byte[] angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() { fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; cell(7, 2, 2); ids[1] = 7; ids[2] = 8;
            shape(7, 8, 0x22); shape(8, 8, 0x34); block(7, 8, 0x2001); block(6, 8, 0x2001); block(14, 8, 0x2001); }
        Terrain clear() { Arrays.fill(chunks, (byte) 0); return this; }
        Terrain cell(int row, int column, int chunk) { fg[2 + row * 42 + column] = (byte) chunk; return this; }
        Terrain flagged(int row, int column, int chunk) { return cell(row, column, chunk | 128); }
        Terrain block(int column, int row, int word) { w(chunks, 512 + (row * 16 + column) * 2, word); return this; }
        Terrain shape(int id, int height, int angle) { angles[id] = (byte) angle; Arrays.fill(heights, 16 * id, 16 * id + 16, (byte) height); return this; }
        CdPlayerTerrain build() { return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths)); }
    }
    static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }
    static final class Harness {
        final CdObjectPlacementRuntime placement = new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(hex("000c0014ffff000000000000ffff000000000000ffff00000000")));
        final List<CdTamabboh.PrimaryAngleCall> angleCalls = new ArrayList<>();
        final List<CdTamabboh.PlayerReadCall> playerCalls = new ArrayList<>();
        final List<CdTamabboh.FmCall> fmCalls = new ArrayList<>();
        final List<CdObjectCollisionCheck.FloorDistance> floors = new ArrayList<>();
        final List<CdObjectState> draws = new ArrayList<>();
        final Fault fault = new Fault(); final CdObjectDrawQueue queue; final CdTamabboh body;
        int good, time = 1, direction, debug = 1, camera = 0x200, cameraY = 0x700, angle = 0x55, px, py, activeWhole = WHOLE;
        int rejectAngle, rejectPlayer, mutation; boolean rejectFm, rejectDraw, foreign, reenter, noOpAngle, throwAfterAttack;
        String attackPort = ""; UnaryOperator<CdObjectState> drawRewrite = UnaryOperator.identity();
        Harness(Terrain terrain) {
            placement.resetSavedObjectFlags();
            queue = new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
                @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                    var s = pool().state(ref.wholeIndex()); return new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(),
                            s.word(X_FRACTION), s.longBits(MAPPINGS), s.unsignedByte(MAP_FRAME), s.word(TILE));
                }
                @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                    var s = pool().state(ref.wholeIndex()); pool().write(s.moveByte(SPRITE_FLAGS, value ? s.spriteFlags() | 128 : s.spriteFlags() & 127));
                }
            }, mappings(), new byte[640]);
            queue.beginPass(); body = new CdTamabboh(placement, assets(), terrain.build(), this::angle, this::player, this::fm, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> edit) { pool().write(edit.apply(slot())); }
        void fill() { for (int i = 32; i < 128; i++) if (i != WHOLE) pool().write(pool().state(i).moveByte(ID, 2)); }
        CdTamabboh.Globals globals() { return new CdTamabboh.Globals(good, time, direction, debug, camera, angle); }
        CdTamabboh.Result run() { return run(WHOLE); }
        CdTamabboh.Result run(int whole) {
            activeWhole = whole; var r = body.run(whole, globals());
            for (var e : r.effects()) if (e instanceof CdTamabboh.FloorReturned f) floors.add(f.result()); return r;
        }
        void nextPass() { var c = new CdObjectDrawQueue.CameraWords(camera, cameraY); queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(c, c, c)); queue.beginPass(); }
        List<Integer> angleValues() { return angleCalls.stream().map(CdTamabboh.PrimaryAngleCall::value).toList(); }
        void check(CdObjectPool.Snapshot cut, CdObjectState source) {
            pool().validateRestore(cut); assertEquals(pool().capture().slots(), cut.slots()); assertEquals(pool().state(activeWhole), source);
        }
        void attack(String port) {
            if (!attackPort.equals(port)) return;
            if (reenter) run(activeWhole);
            if (foreign) pool().write(new CdObjectPool().state(activeWhole));
            if (mutation == 1) pool().write(pool().state(activeWhole).moveByte(FLAGS, 0xA5));
            if (mutation == 2) pool().write(pool().state(42).moveByte(ID, 0xA6));
            if (throwAfterAttack) throw fault;
        }
        void angle(CdTamabboh.PrimaryAngleCall call) {
            check(call.callCut(), call.expectedSource()); angleCalls.add(call);
            if (angle != call.expectedPrimaryAngle() || angleCalls.size() == rejectAngle) throw fault;
            attack("ANGLE"); if (!noOpAngle) angle = call.value();
        }
        int player(CdTamabboh.PlayerReadCall call) {
            check(call.callCut(), call.expectedSource()); playerCalls.add(call); if (playerCalls.size() == rejectPlayer) throw fault;
            attack("PLAYER"); return call.field() == CdTamabboh.PlayerField.Y_WORD ? py : px;
        }
        void fm(CdTamabboh.FmCall call) {
            check(call.callCut(), call.expectedSource()); assertEquals(pool().findObjSlot().orElseThrow(), call.firstFreeWholeIndex());
            assertEquals(0, pool().state(call.firstFreeWholeIndex()).id()); fmCalls.add(call); attack("FM"); if (rejectFm) throw fault;
        }
        void draw(CdObjectPool actual, CdObjectState.Identity identity) {
            assertSame(pool(), actual); assertEquals(activeWhole, identity.wholeIndex()); var s = pool().state(identity.wholeIndex()); draws.add(s);
            queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(identity.wholeIndex()), s.spriteFlags(), s.centreX(), s.centreY(),
                    s.unsignedByte(Y_RADIUS), s.unsignedByte(WIDTH), s.unsignedByte(PRIORITY)), new CdObjectDrawQueue.CameraWords(camera, cameraY));
            if (rejectDraw) throw fault; pool().write(drawRewrite.apply(pool().state(identity.wholeIndex())));
        }
    }
}
