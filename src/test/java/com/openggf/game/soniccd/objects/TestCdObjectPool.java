package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native dynamic-slot storage and allocation behavior. */
class TestCdObjectPool {
    @Test
    void fixedNinetySixPhysicalIdentitiesAndIdOnlyAllocationThroughLastSlot() {
        var pool = new CdObjectPool();
        for (int whole = 32; whole < 128; whole++) {
            assertEquals(whole, pool.findObjSlot().orElseThrow());
            var state = pool.state(whole);
            assertEquals(whole, state.identity().wholeIndex());
            assertEquals(whole - 32, state.identity().dynamicIndex());
            assertEquals(0xFFD000 + whole * 64, state.identity().nativeAddress24());
            pool.write(state.moveByte(ID, 0x10));
        }
        assertTrue(pool.findObjSlot().isEmpty());
        pool.objNull(127);
        assertEquals(127, pool.findObjSlot().orElseThrow());
        assertThrows(IndexOutOfBoundsException.class, () -> pool.state(31));
        assertThrows(IndexOutOfBoundsException.class, () -> pool.state(128));
    }

    @Test
    void findNextTakesWholeIndexAndSearchesOnlyDynamicStrictlyLaterWithoutWrap() {
        var pool = new CdObjectPool();
        for (int reservedCaller = 0; reservedCaller < 31; reservedCaller++) {
            int caller = reservedCaller;
            assertThrows(IllegalArgumentException.class,
                    () -> pool.findNextObjSlotAfterWholeIndex(caller),
                    "Reserved callers can search slots this dynamic-only owner cannot observe");
        }
        assertEquals(32, pool.findNextObjSlotAfterWholeIndex(31).orElseThrow());
        assertEquals(33, pool.findNextObjSlotAfterWholeIndex(32).orElseThrow());
        assertEquals(127, pool.findNextObjSlotAfterWholeIndex(126).orElseThrow());
        assertTrue(pool.findNextObjSlotAfterWholeIndex(127).isEmpty());
        pool.write(pool.state(127).moveByte(ID, 1));
        assertTrue(pool.findNextObjSlotAfterWholeIndex(126).isEmpty());
        assertEquals(32, pool.findObjSlot().orElseThrow());
        assertThrows(IndexOutOfBoundsException.class, () -> pool.findNextObjSlotAfterWholeIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> pool.findNextObjSlotAfterWholeIndex(128));
    }

    @Test
    void freeIdDoesNotClearDirtyDataAndNullDiffersFromDelete() {
        var pool = new CdObjectPool();
        var dirty = pool.state(32).moveLong(X_POSITION, 0x1234ABCD).moveLong(Y_POSITION, 0x5678FEDC)
                .moveLong(MAPPINGS, 0x20ABCD).moveWord(TILE, 0xA7AE).moveByte(ID, 0x10)
                .moveByte(SPRITE_FLAGS, 0x84).moveByte(COLLISION_TYPE, 0x47).moveByte(ROUTINE, 6);
        pool.write(dirty);
        pool.objNull(32);
        assertEquals(32, pool.findObjSlot().orElseThrow());
        assertEquals(dirty.moveByte(ID, 0), pool.state(32));
        assertEquals(0xABCD, pool.state(32).word(X_FRACTION));
        pool.deleteObject(32);
        assertArrayEquals(new byte[64], pool.state(32).copyBytes());
        assertEquals(32, pool.state(32).identity().wholeIndex());
    }

    @Test
    void nativeSolidAndLayerAliasesShareSecondaryBytesWithoutChangingRoutineOrSubtype() {
        // oSolidType / oLayer (Level/_Definitions.inc:59-66) are zero-size
        // declarations AFTER the primary byte; they alias the following byte.
        var pool = new CdObjectPool();
        var state = pool.state(32).moveByte(ROUTINE, 0x14).moveByte(ROUTINE_2, 0xA3)
                .moveByte(SUBTYPE, 0x62).moveByte(SUBTYPE2, 0xD5)
                .moveLong(X_POSITION, 0x12345678);
        assertEquals(0xA3, state.unsignedByte(SOLID_TYPE));
        assertEquals(0xD5, state.unsignedByte(LAYER));
        assertEquals(0x56, state.unsignedByte(SCREEN_Y));
        var changed = state.moveByte(SOLID_TYPE, 7).moveByte(LAYER, 9);
        var expected = state.copyBytes();
        expected[0x25] = 7;
        expected[0x29] = 9;
        assertArrayEquals(expected, changed.copyBytes());
        assertEquals(0x14, changed.routine());
        assertEquals(0x62, changed.unsignedByte(SUBTYPE));
        assertEquals(7, changed.unsignedByte(ROUTINE_2));
        assertEquals(9, changed.unsignedByte(SUBTYPE2));
    }

    @Test
    void typedNativeWidthsAreBigEndianAndOldStatesAreImmutable() {
        var pool = new CdObjectPool();
        var zero = pool.state(32);
        var state = zero.moveLong(X_POSITION, 0xFFFF1234).moveWord(X, 0x10001)
                .moveLong(Y_POSITION, 0x4321ABCD).moveByte(ROUTINE, 0x1FE).addByte(ROUTINE, 4)
                .moveWord(X_VELOCITY, -0x100).moveByte(SAVED_FLAGS_ID, 7).moveByte(SUBTYPE2, 0xFF);
        assertEquals(1, state.centreX());
        assertEquals(0x1234, state.word(X_FRACTION));
        assertEquals(0x4321, state.centreY());
        assertEquals(0xABCD, state.word(Y_FRACTION));
        assertEquals(2, state.routine());
        assertEquals(-0x100, state.signedWord(X_VELOCITY));
        byte[] bytes = state.copyBytes();
        assertEquals(0x12, Byte.toUnsignedInt(bytes[0x0A]));
        assertEquals(0x34, Byte.toUnsignedInt(bytes[0x0B]));
        assertEquals(7, Byte.toUnsignedInt(bytes[0x23]));
        assertEquals(0xFF, Byte.toUnsignedInt(bytes[0x29]));
        Arrays.fill(bytes, (byte) 0);
        assertEquals(0x1234, state.word(X_FRACTION));
        assertArrayEquals(new byte[64], zero.copyBytes());
    }

    @Test
    void sourceSpecificTailAliasesStayInTheSamePhysicalSixtyFourBytes() {
        var pool = new CdObjectPool();
        var state = pool.state(32).moveWord(MOSQUI_ANCHOR_X, 0x270)
                .moveLong(MOSQUI_X_DELTA, -0x8000).moveLong(MOSQUI_ANIMATION_SCRIPT, 0x20B6D8);
        byte[] bytes = state.copyBytes();
        assertEquals(2, Byte.toUnsignedInt(bytes[0x2A]));
        assertEquals(0x70, Byte.toUnsignedInt(bytes[0x2B]));
        assertEquals(0xFF, Byte.toUnsignedInt(bytes[0x2C]));
        assertEquals(0x80, Byte.toUnsignedInt(bytes[0x2E]));
        assertEquals(0x20B6D8, state.longBits(MOSQUI_ANIMATION_SCRIPT));
        var reusedAsRing = state.moveWord(RING_ANCHOR_X, 0xF0).moveByte(RING_MEMBER, 2);
        assertEquals(0x002000F0, reusedAsRing.longBits(MOSQUI_ANIMATION_SCRIPT), "Real overlapping tail storage");
        assertEquals(2, Byte.toUnsignedInt(reusedAsRing.copyBytes()[0x34]));
        pool.write(reusedAsRing);
        pool.objNull(32);
        assertEquals(-0x8000, pool.state(32).longBits(MOSQUI_X_DELTA));
        pool.deleteObject(32);
        assertArrayEquals(new byte[64], pool.state(32).copyBytes());
    }

    @Test
    void writePreflightChecksOwnershipWithoutCommittingEitherImage() {
        var pool = new CdObjectPool(); var foreign = new CdObjectPool();
        var staged = pool.state(32).moveByte(ID, 0x18);
        pool.validateWrite(staged);
        assertEquals(0, pool.state(32).id());
        assertThrows(IllegalArgumentException.class, () -> pool.validateWrite(foreign.state(33).moveByte(ID, 0x1C)));
        assertEquals(0, pool.state(32).id()); assertEquals(0, pool.state(33).id());
        pool.write(staged); assertEquals(0x18, pool.state(32).id());
    }

    @Test
    void captureIsDeepImmutableAndForeignPoolWritesOrRestoreRejectUnchanged() {
        var pool = new CdObjectPool();
        pool.write(pool.state(127).moveByte(ID, 0x3D).moveLong(X_POSITION, 0x0270ABCD));
        var saved = pool.capture();
        assertThrows(UnsupportedOperationException.class, () -> saved.slots().clear());
        pool.clearDynamicSlots();
        assertEquals(0x3D, saved.slots().get(95).id());
        pool.restore(saved);
        assertEquals(0x0270ABCD, pool.state(127).longBits(X_POSITION));
        var foreign = new CdObjectPool();
        var before = pool.capture();
        assertThrows(IllegalArgumentException.class, () -> pool.restore(foreign.capture()));
        assertThrows(IllegalArgumentException.class, () -> pool.write(foreign.state(32)));
        assertEquals(before.slots(), pool.capture().slots());
    }
}
