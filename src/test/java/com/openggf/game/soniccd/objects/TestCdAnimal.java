package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectPlacements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdAnimal.DespawnEntry.*;
import static com.openggf.game.soniccd.objects.CdAnimal.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdAnimal.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.LOADED;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * ObjAnimal (Palmtree Panic/Objects/Animal/Main.asm:7-19) with CheckAnimalPrescence
 * (Level/Object Time Check.asm:55-84) and CheckObjDespawn/DespawnObject (Level/Object Spawner.asm:385-446)
 * at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. The admitted paths read no asset bytes:
 * the Present/projector-intact path discards the body's return address, and the bad future deletes.
 * No native comparison row, runtime disassembly read or route coordinate drives the body.
 */
class TestCdAnimal {
    /** Common row 22: X $468, Y $6E0, ID $24, subtype 0, Present only, no saved flag. */
    private static final int ROW_X = 0x468;
    private static final int ROW_Y = 0x6E0;

    @Test
    void presentProjectorIntactIsInertAndWritesNothing() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var before = h.slot(34);
        var result = h.run(34, present(0x200));
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), call(CHECK_OBJ_DESPAWN)), result.effects());
        assertFalse(result.deleted());
        assertEquals(before, result.state(), "the popped return address skips Init: no map, tile, flag or routine write");
        assertEquals(before, h.slot(34));
        assertEquals(0x24, result.state().id());
        assertEquals(0, result.state().routine());
        assertEquals(0, result.state().spriteFlags(), "never drawn: bit 7 stays clear so it is never touch-selectable");
        assertEquals(0, result.state().longBits(MAPPINGS));
        assertEquals(0, result.state().word(TILE));
    }

    @Test
    void lowSubtypeBitsDoNotSelectTheGroundBodyWhileTheProjectorStands() {
        var h = new Harness();
        h.place(34, 0x34, 0x1E6, 0x01, 0); // common row 0's ground-animal subtype in a Present world
        var before = h.slot(34);
        var result = h.run(34, present(0));
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), call(CHECK_OBJ_DESPAWN)), result.effects());
        assertEquals(before, result.state(), "only bit 7 of oSubtype is tested before the projector gate");
    }

    @Test
    void dirtySlotBytesSurviveTheInertDispatch() {
        var h = new Harness();
        h.write(h.slot(34).moveWord(X_VELOCITY, 0x1234).moveWord(Y_VELOCITY, 0xFEDC).moveByte(ROUTINE, 6)
                .moveByte(COLLISION_TYPE, 0x2B).moveByte(PRIORITY, 3).moveByte(MAP_FRAME, 0x1F)
                .moveWord(X_FRACTION, 0x8000).moveLong(MAPPINGS, 0x20D240).moveWord(TILE, 0x388));
        h.place(34, ROW_X, 0x46E0, 0, 0); // packed Y bit 14 supplies flip bit 0 to both flag bytes
        var s = h.run(34, present(0x200)).state();
        assertEquals(0x1234, s.word(X_VELOCITY));
        assertEquals(0xFEDC, s.word(Y_VELOCITY));
        assertEquals(6, s.routine(), "no routine dispatch happens, so even a dirty routine is untouched");
        assertEquals(0x2B, s.unsignedByte(COLLISION_TYPE), "dirty colType retained; touch needs bit 7, not an Animal rule");
        assertEquals(3, s.unsignedByte(PRIORITY));
        assertEquals(0x1F, s.unsignedByte(MAP_FRAME));
        assertEquals(0x04688000, s.longBits(X_POSITION));
        assertEquals(0x6E0, s.centreY());
        assertEquals(0x20D240, s.longBits(MAPPINGS));
        assertEquals(0x388, s.word(TILE));
        assertEquals(1, s.spriteFlags());
        assertEquals(1, s.flags());
    }

    @ParameterizedTest(name = "cameraX {0} keeps={1}")
    @CsvSource({
            "0x200, true",   // ((cameraX-$80)&$FF80)=$180; $400-$180=$280: BLS keeps the equality
            "0x27F, true",   // same camera chunk
            "0x4FF, true",   // $400-$400=0
            "0x1FF, false",  // $100 chunk: $300 exceeds $280
            "0x180, false",
            "0x500, false",  // $400-$480 wraps to $FF80: unsigned above $280
            "0x680, false",  // $400-$600 wraps to $FE00
            "0x0, false"     // SUBI.W borrows to $FF80: $400-$FF80 = $480, above $280
    })
    void currentXWindowUsesChunkArithmeticWithUnsignedInclusiveEdge(String cameraHex, boolean keeps) {
        int cameraX = Integer.decode(cameraHex);
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, present(cameraX));
        if (keeps) {
            assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), call(CHECK_OBJ_DESPAWN)), result.effects());
            assertFalse(result.deleted());
            assertEquals(0x24, h.slot(34).id());
        } else {
            assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 0, -1),
                    call(CHECK_OBJ_DESPAWN)), result.effects());
            assertTrue(result.deleted());
            assertArrayEquals(new byte[64], result.state().copyBytes(), "DespawnObject's DeleteObject zeroes all 64 bytes");
            assertEquals(0, h.slot(34).id());
        }
    }

    @Test
    void dirtySavedIdClearsThatBankBitExactlyAsNativeDespawnObjectWould() {
        var h = new Harness();
        h.runtime.bsetSavedFlag(7, 1, LOADED);
        h.runtime.bsetSavedFlag(7, 0, LOADED);
        h.write(h.slot(34).moveByte(SAVED_FLAGS_ID, 7)); // SpawnObject writes no saved ID for an unflagged record
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, present(0x180));
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 7, 1),
                call(CHECK_OBJ_DESPAWN)), result.effects());
        assertEquals(0, h.runtime.savedFlag(7, 1) & 0x80, "loaded bit 7 cleared at the Present entry");
        assertEquals(0x80, h.runtime.savedFlag(7, 0) & 0x80, "other periods untouched");
        assertEquals(0, h.slot(34).id());
    }

    @Test
    void onscreenBitSkipsTheWindowTestOutsideATimeWarp() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.write(h.slot(34).moveByte(SPRITE_FLAGS, 0x80)); // a dirty bit 7: TST.B oSprFlags / BMI DespawnObjTimeWarp
        var result = h.run(34, present(0));
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), call(CHECK_OBJ_DESPAWN)), result.effects());
        assertEquals(0x24, h.slot(34).id());
        assertEquals(0x80, h.slot(34).spriteFlags(), "this body never writes the sprite flags");
    }

    @ParameterizedTest(name = "timeZone {0} warpDir {1} savedId {2} -> period {3}")
    @CsvSource({
            "0x81, 0x01, 7, 0",   // Present warp towards the past: old period 0
            "0x81, 0xFF, 7, 2",   // Present warp towards the future: old period 2
            "0x80, 0x01, 7, 0",   // Past warp: 0 - 1 underflows and caps at TIME_PAST
            "0x82, 0xFF, 7, 2",   // Future warp: 2 + 1 overflows and caps at TIME_FUTURE
            "0x81, 0x01, 0, -1"   // no saved ID: no bank write
    })
    void timeWarpBitTakesTheInertPathThroughTheSignedCompareAndDespawnsOnTheOldPeriod(
            String timeZoneHex, String warpDirHex, int savedId, int period) {
        // CMPI.B #2,timeZone / BGE is a signed byte compare: $80..$82 are negative and never reach .ChkGoodFuture.
        var h = new Harness();
        if (savedId != 0) {
            for (int p = 0; p < 3; p++) { h.runtime.bsetSavedFlag(savedId, p, LOADED); }
            h.write(h.slot(34).moveByte(SAVED_FLAGS_ID, savedId));
        }
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.write(h.slot(34).moveByte(SPRITE_FLAGS, 0x80)); // onscreen: the window test is skipped, the warp test is not
        var globals = new CdAnimal.Globals(Integer.decode(timeZoneHex), 1, 0, Integer.decode(warpDirHex), 0x200);
        var result = h.run(34, globals);
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Despawn(TIME_WARP, savedId, period),
                call(CHECK_OBJ_DESPAWN)), result.effects());
        assertTrue(result.deleted());
        if (savedId != 0) {
            for (int p = 0; p < 3; p++) {
                assertEquals(p == period ? 0 : 0x80, h.runtime.savedFlag(savedId, p) & 0x80, "period " + p);
            }
        }
    }

    @Test
    void badFutureDeletesWithoutReadingTheSavedBank() {
        var h = new Harness();
        h.runtime.bsetSavedFlag(7, 2, LOADED);
        h.write(h.slot(34).moveByte(SAVED_FLAGS_ID, 7));
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, new CdAnimal.Globals(2, 0, 0, 0, 0x200));
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Deleted(), call(DELETE_OBJECT)), result.effects());
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], h.slot(34).copyBytes());
        assertEquals(0x80, h.runtime.savedFlag(7, 2) & 0x80, "JMP DeleteObject is not DespawnObject: no bank write");
    }

    @Test
    void badFutureDeleteIgnoresTheProjectorFlagAndSubtypeLowBits() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0x01, 0);
        var result = h.run(34, new CdAnimal.Globals(2, 0, 1, 0, 0));
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Deleted(), call(DELETE_OBJECT)), result.effects());
    }

    @Test
    void hologramSubtypeStopsBeforeAnyWrite() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0x80, 0);
        var before = h.slot(34);
        var failure = assertThrows(CdAnimal.UnsupportedAnimal.class, () -> h.run(34, present(0x200)));
        assertEquals(HOLOGRAM_BODY, failure.boundary());
        assertEquals(34, failure.wholeIndex());
        assertEquals(before, h.slot(34));
        var future = assertThrows(CdAnimal.UnsupportedAnimal.class,
                () -> h.run(34, new CdAnimal.Globals(2, 0, 0, 0, 0)));
        assertEquals(HOLOGRAM_BODY, future.boundary(), "BMI .End precedes the time zone compare");
    }

    @Test
    void destroyedProjectorInThePresentStopsBeforeTheRoamingBody() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var before = h.slot(34);
        var failure = assertThrows(CdAnimal.UnsupportedAnimal.class,
                () -> h.run(34, new CdAnimal.Globals(1, 0, 1, 0, 0x200)));
        assertEquals(PROJECTOR_DESTROYED_BODY, failure.boundary());
        assertEquals(before, h.slot(34));
        var past = assertThrows(CdAnimal.UnsupportedAnimal.class,
                () -> h.run(34, new CdAnimal.Globals(0, 0, 0xFF, 0, 0x200)));
        assertEquals(PROJECTOR_DESTROYED_BODY, past.boundary(), "TST.B projDestroyed: any nonzero byte");
    }

    @Test
    void goodFutureRoamingBodyStopsBeforeAnyWrite() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var before = h.slot(34);
        var failure = assertThrows(CdAnimal.UnsupportedAnimal.class,
                () -> h.run(34, new CdAnimal.Globals(2, 1, 0, 0, 0x200)));
        assertEquals(GOOD_FUTURE_BODY, failure.boundary());
        assertEquals(before, h.slot(34));
    }

    @Test
    void wrongSlotIdAndInvalidGlobalsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.write(h.slot(34).moveByte(ID, 0x3D));
        assertThrows(IllegalArgumentException.class, () -> h.run(34, present(0)));
        assertEquals(0x3D, h.slot(34).id());
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(3, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(0x83, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0x100, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, 0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, 0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, 0, 0, 0x10000));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 5, -1));
        assertThrows(NullPointerException.class, () -> new CdAnimal(null));
        assertThrows(NullPointerException.class, () -> h.animal.run(34, null));
    }

    @Test
    void resultEffectsAreImmutableAndTheObjectIdIsNative() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, present(0x200));
        assertThrows(UnsupportedOperationException.class, () -> result.effects().clear());
        assertEquals(0x24, CdAnimal.OBJECT_ID);
    }

    private static CdAnimal.Effect call(CdAnimal.NativeCall nativeCall) { return new CdAnimal.CallReturned(nativeCall); }
    private static CdAnimal.Globals present(int cameraX) { return new CdAnimal.Globals(1, 0, 0, 0, cameraX); }

    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] r : records) {
            bytes.putShort((short) r[0]).putShort((short) r[1]).put((byte) r[2]).put((byte) r[3])
                    .put((byte) r[4]).put((byte) r[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(ROW_X, ROW_Y, 0x24, 0x00, 0x40, 0)));
        final CdAnimal animal = new CdAnimal(runtime);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        CdAnimal.Result run(int whole, CdAnimal.Globals globals) { return animal.run(whole, globals); }

        /** SpawnObject's writes only (Object Spawner.asm:267-319); an unflagged record writes no saved ID. */
        void place(int whole, int x, int packedY, int subtype, int subtype2) {
            int rotated = ((packedY << 2) | (packedY >>> 14)) & 0xFFFF;
            int flips = rotated & 3;
            var state = slot(whole).moveWord(X, x).moveWord(Y, packedY & 0xFFF)
                    .moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips);
            write(state.moveByte(ID, 0x24).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, subtype2));
        }
    }
}
