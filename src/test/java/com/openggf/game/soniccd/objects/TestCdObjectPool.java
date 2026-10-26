package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    // Pure derived call-cut images, not a capture certificate or a live pool write.
    @Test
    void overlayPreservesAllNinetySixPhysicalSlotsAndTheBaseWhileReplacingFirstAndLastImages() {
        var pool = new CdObjectPool();
        pool.write(pool.state(32).moveByte(ID, 0x1E).moveLong(X_POSITION, 0x05B01234));
        pool.write(pool.state(127).moveByte(ID, 0x12).moveLong(Y_POSITION, 0x020CABCD));
        var base = pool.capture();
        var first = base.slots().getFirst().moveByte(ANIMATION, 1);
        var last = base.slots().getLast().moveByte(ANIMATION, 1);
        var images = new ArrayList<>(List.of(last, first));
        var derived = CdObjectPool.overlay(base, images);
        images.clear();
        assertNotSame(base, derived); assertEquals(96, derived.slots().size());
        for (int i = 0; i < 96; i++) {
            var expected = i == 0 ? first : i == 95 ? last : base.slots().get(i);
            assertSame(expected, derived.slots().get(i));
            assertEquals(i + 32, derived.slots().get(i).identity().wholeIndex());
            assertEquals(i, derived.slots().get(i).identity().dynamicIndex());
        }
        assertEquals(0, base.slots().getFirst().unsignedByte(ANIMATION));
        assertEquals(0, base.slots().getLast().unsignedByte(ANIMATION));
        assertEquals(base.slots(), pool.capture().slots());
        assertThrows(UnsupportedOperationException.class, () -> derived.slots().clear());
        pool.validateRestore(derived); // SAME real owner; derived does not imply live provenance/CAS.
        pool.validateWrite(first); pool.validateWrite(last);
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPool().validateRestore(derived));
        assertEquals(base.slots(), pool.capture().slots(), "All preflights remain pure");
    }

    @Test
    void overlayCanReplaceEverySlotAndChainedViewsKeepTheirOriginalPoolOwner() {
        var pool = new CdObjectPool(); var base = pool.capture();
        var images = new ArrayList<CdObjectState>();
        for (int i = 95; i >= 0; i--) images.add(base.slots().get(i).moveByte(ANIMATION, 1));
        var derived = CdObjectPool.overlay(base, images);
        var next = CdObjectPool.overlay(derived, List.of(derived.slots().getLast().addByte(ROUTINE, 2)));
        for (int i = 0; i < 96; i++) {
            assertEquals(1, next.slots().get(i).unsignedByte(ANIMATION));
            assertEquals(i == 95 ? 2 : 0, next.slots().get(i).routine());
            assertEquals(0, base.slots().get(i).unsignedByte(ANIMATION));
        }
        pool.validateRestore(next);
        assertEquals(base.slots(), pool.capture().slots());
        assertEquals(base.slots(), CdObjectPool.overlay(base, List.of()).slots());
    }

    @Test
    void overlayChecksEveryRealOwnerEvenForEqualBytesAtTheFirstOrLastPhysicalIdentity() {
        var pool = new CdObjectPool(); var foreign = new CdObjectPool(); var base = pool.capture();
        for (int whole : new int[]{32, 127}) {
            assertEquals(pool.state(whole).identity(), foreign.state(whole).identity());
            assertArrayEquals(pool.state(whole).copyBytes(), foreign.state(whole).copyBytes());
            assertThrows(IllegalArgumentException.class, () -> CdObjectPool.overlay(base,
                    List.of(pool.state(33).moveByte(ANIMATION, 1), foreign.state(whole))));
        }
        assertEquals(base.slots(), pool.capture().slots());
    }

    @Test
    void overlayRejectsDuplicatePhysicalIndicesAndAllNullInputsWithoutPublishingOrWriting() {
        var pool = new CdObjectPool(); var base = pool.capture(); var slot = pool.state(127);
        assertThrows(IllegalArgumentException.class, () -> CdObjectPool.overlay(base, List.of(slot, slot)));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPool.overlay(base,
                List.of(slot, slot.moveByte(ANIMATION, 1))));
        assertThrows(NullPointerException.class, () -> CdObjectPool.overlay(null, List.of()));
        assertThrows(NullPointerException.class, () -> CdObjectPool.overlay(base, null));
        assertThrows(NullPointerException.class, () -> CdObjectPool.overlay(base,
                Arrays.asList(pool.state(32), null)));
        assertEquals(base.slots(), pool.capture().slots());
    }
}
