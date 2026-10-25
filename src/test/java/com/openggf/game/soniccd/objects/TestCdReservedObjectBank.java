package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Storage contracts for the exact ClearObjects cut, not a level-boot or native execution test.
 * Tests authored before production; J 5d2bca4027ee1b0e52cf1a1336660a89b9a03b32,
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. This local candidate has not been executed.
 */
class TestCdReservedObjectBank {
    @Test
    void clearCutHasExactlyThirtyOneOrderedPhysicalSlotsAndAllSixtyFourBytesAreZero() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        var cut = bank.capture();
        assertEquals(1, CdReservedObjectBank.FIRST_WHOLE_INDEX);
        assertEquals(32, CdReservedObjectBank.END_WHOLE_INDEX);
        assertEquals(31, CdReservedObjectBank.RESERVED_COUNT);
        assertEquals(64, CdReservedObjectBank.BYTE_SIZE);
        assertEquals(31, cut.slots().size());
        for (int whole = 1; whole <= 31; whole++) {
            var image = bank.state(whole);
            assertEquals(new CdReservedObjectBank.Identity(whole), image.identity());
            assertEquals(whole, image.identity().wholeIndex());
            assertEquals(0xFFD000 + whole * 0x40, image.identity().nativeAddress24());
            assertArrayEquals(new byte[64], image.copyBytes(), "whole " + whole);
            assertEquals(image, cut.slots().get(whole - 1));
        }
        assertEquals(0xFFD040, cut.slots().getFirst().identity().nativeAddress24());
        assertEquals(0xFFD7C0, cut.slots().getLast().identity().nativeAddress24());
    }

    @Test
    void primaryAndDynamicIdentitiesCannotEnterReservedStorageOrExpandTheDynamicDomain() {
        var bank = patternedBank();
        var before = bank.capture();
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 32, 127, 128, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> bank.state(invalid));
            assertThrows(IndexOutOfBoundsException.class, () -> new CdReservedObjectBank.Identity(invalid));
        }
        for (int whole = 1; whole <= 31; whole++) {
            int index = whole;
            assertThrows(IndexOutOfBoundsException.class, () -> new CdObjectState.Identity(index));
        }
        assertEquals(32, new CdObjectState.Identity(32).wholeIndex());
        assertEquals(127, new CdObjectState.Identity(127).wholeIndex());
        assertThrows(IndexOutOfBoundsException.class, () -> new CdObjectState.Identity(0));
        assertThrows(IndexOutOfBoundsException.class, () -> new CdObjectState.Identity(128));
        assertEquals(before.slots(), bank.capture().slots());
    }

    @Test
    void everySlotRetainsDistinctFullImagesAndOneWritePreservesAllOtherSlots() {
        var bank = patternedBank();
        var before = bank.capture();
        var replacement = bank.state(6).moveByte(ID, 3).moveWord(TILE, 0xBEEF);
        bank.write(replacement);
        for (int whole = 1; whole <= 31; whole++) {
            if (whole == 6) {
                byte[] expected = pattern(6);
                expected[0] = 3;
                expected[2] = (byte) 0xBE;
                expected[3] = (byte) 0xEF;
                assertArrayEquals(expected, bank.state(whole).copyBytes());
            } else {
                assertArrayEquals(pattern(whole), bank.state(whole).copyBytes());
            }
            assertArrayEquals(pattern(whole), before.slots().get(whole - 1).copyBytes());
        }
    }

    @Test
    void byteWordAndLongMovesCoverEveryLegalOffsetAndPreserveEveryUnwrittenByte() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        byte[] original = pattern(0x41);
        var image = bank.state(1).withBytes(original);
        for (int at = 0; at < 64; at++) {
            byte[] expected = original.clone();
            expected[at] = (byte) 0xFE;
            var moved = image.moveByteAt(at, 0x1FE);
            assertArrayEquals(expected, moved.copyBytes(), "byte at " + at);
            assertEquals(0xFE, moved.byteAt(at));
        }
        for (int at = 0; at <= 62; at++) {
            byte[] expected = original.clone();
            expected[at] = (byte) 0xAB;
            expected[at + 1] = (byte) 0xCD;
            var moved = image.moveWordAt(at, 0x1234ABCD);
            assertArrayEquals(expected, moved.copyBytes(), "word at " + at);
            assertEquals(0xABCD, moved.wordAt(at));
        }
        for (int at = 0; at <= 60; at++) {
            byte[] expected = original.clone();
            expected[at] = (byte) 0x89;
            expected[at + 1] = (byte) 0xAB;
            expected[at + 2] = (byte) 0xCD;
            expected[at + 3] = (byte) 0xEF;
            var moved = image.moveLongAt(at, 0x89ABCDEF);
            assertArrayEquals(expected, moved.copyBytes(), "long at " + at);
            assertEquals(0x89ABCDEF, moved.longBitsAt(at));
        }
        assertArrayEquals(original, image.copyBytes());
        assertArrayEquals(new byte[64], bank.state(1).copyBytes(), "Detached moves never publish");
    }

    @Test
    void everyTypedDescriptorUsesTheSameBoundedBytesAsRawStorage() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        var image = bank.state(31).withBytes(pattern(31));
        for (var field : CdObjectState.ByteField.values()) {
            var changed = image.moveByte(field, -128);
            assertEquals(image.moveByteAt(field.byteOffset(), -128), changed);
            assertEquals(0x80, changed.unsignedByte(field));
            assertEquals(-128, changed.signedByte(field));
        }
        for (var field : CdObjectState.WordField.values()) {
            var changed = image.moveWord(field, -0x1234);
            assertEquals(image.moveWordAt(field.byteOffset(), -0x1234), changed);
            assertEquals(0xEDCC, changed.word(field));
            assertEquals(-0x1234, changed.signedWord(field));
        }
        for (var field : CdObjectState.LongField.values()) {
            var changed = image.moveLong(field, 0xFEDCBA98);
            assertEquals(image.moveLongAt(field.byteOffset(), 0xFEDCBA98), changed);
            assertEquals(0xFEDCBA98, changed.longBits(field));
        }
        assertArrayEquals(pattern(31), image.copyBytes());
        assertArrayEquals(new byte[64], bank.state(31).copyBytes());
    }

    @Test
    void literalAliasesAndIntegerWritesKeepFractionsAndUnrelatedTailBytes() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        var initial = bank.state(1).withBytes(pattern(1));
        var changed = initial.moveLong(X_POSITION, 0xFFFF1234).moveWord(X, 0x10001)
                .moveLong(Y_POSITION, 0x4321ABCD).moveWord(Y, -2)
                .moveByte(ROUTINE, 0x14).moveByte(SOLID_TYPE, 0xA3)
                .moveByte(SUBTYPE, 0x62).moveByte(LAYER, 0xD5);
        byte[] expected = pattern(1);
        expected[8] = 0; expected[9] = 1; expected[10] = 0x12; expected[11] = 0x34;
        expected[12] = (byte) 0xFF; expected[13] = (byte) 0xFE;
        expected[14] = (byte) 0xAB; expected[15] = (byte) 0xCD;
        expected[0x24] = 0x14; expected[0x25] = (byte) 0xA3;
        expected[0x28] = 0x62; expected[0x29] = (byte) 0xD5;
        assertArrayEquals(expected, changed.copyBytes());
        assertEquals(0x00011234, changed.longBits(X_POSITION));
        assertEquals(0xFFFEABCD, changed.longBits(Y_POSITION));
        assertEquals(0x1234, changed.word(X_FRACTION));
        assertEquals(0xABCD, changed.word(Y_FRACTION));
        assertEquals(0x12, changed.unsignedByte(SCREEN_Y));
        assertEquals(0xA3, changed.unsignedByte(ROUTINE_2));
        assertEquals(0xD5, changed.unsignedByte(SUBTYPE2));
        assertArrayEquals(pattern(1), initial.copyBytes());
    }

    @Test
    void rawPlayerTwoAliasesAndOverlappingTailStoresHaveNoIndependentShadow() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        var image = bank.state(1).withBytes(pattern(1));
        var changed = image.moveWordAt(0x14, 0xFEDC).moveByteAt(0x2A, 0x81)
                .moveLongAt(0x3C, 0x89ABCDEF).moveWord(EXPLOSION_POINTS_FRAME, 0x1357)
                .moveByte(STAND_LINK_3D, 0x42);
        byte[] expected = pattern(1);
        expected[0x14] = (byte) 0xFE; expected[0x15] = (byte) 0xDC;
        expected[0x2A] = (byte) 0x81;
        expected[0x3C] = (byte) 0x89; expected[0x3D] = 0x42;
        expected[0x3E] = 0x13; expected[0x3F] = 0x57;
        assertArrayEquals(expected, changed.copyBytes());
        assertEquals(0xFEDC, changed.wordAt(0x14));
        assertEquals(0x81, changed.byteAt(0x2A));
        assertEquals(0x89421357, changed.longBitsAt(0x3C));
        bank.write(changed);
        assertEquals(changed, bank.state(1));
        assertArrayEquals(new byte[64], bank.state(2).copyBytes());
    }

    @Test
    void rawWidthsRejectNegativeOverflowAndCrossSlotOffsetsBeforeAnyWrite() {
        var bank = patternedBank();
        var before = bank.capture();
        var image = bank.state(31);
        for (int at : new int[]{Integer.MIN_VALUE, -1, 64, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> image.byteAt(at));
            assertThrows(IndexOutOfBoundsException.class, () -> bank.write(image.moveByteAt(at, 1)));
        }
        for (int at : new int[]{Integer.MIN_VALUE, -1, 63, 64, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> image.wordAt(at));
            assertThrows(IndexOutOfBoundsException.class, () -> bank.write(image.moveWordAt(at, 1)));
        }
        for (int at : new int[]{Integer.MIN_VALUE, -1, 61, 63, 64, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> image.longBitsAt(at));
            assertThrows(IndexOutOfBoundsException.class, () -> bank.write(image.moveLongAt(at, 1)));
        }
        assertEquals(0xFF, image.moveByteAt(63, -1).byteAt(63));
        assertEquals(0xFFFF, image.moveWordAt(62, -1).wordAt(62));
        assertEquals(-1, image.moveLongAt(60, -1).longBitsAt(60));
        assertEquals(before.slots(), bank.capture().slots());
    }

    @Test
    void callerBuffersAndCopiesCannotMutateImageLiveStorageOrEarlierCaptures() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        byte[] supplied = pattern(8);
        var cold = bank.capture();
        var image = bank.state(8).withBytes(supplied);
        Arrays.fill(supplied, (byte) 0);
        assertArrayEquals(pattern(8), image.copyBytes());
        assertArrayEquals(new byte[64], bank.state(8).copyBytes());
        bank.write(image);
        var populated = bank.capture();
        byte[] copy = image.copyBytes();
        Arrays.fill(copy, (byte) 0xFF);
        byte[] capturedCopy = populated.slots().get(7).copyBytes();
        Arrays.fill(capturedCopy, (byte) 0xAA);
        assertArrayEquals(pattern(8), bank.state(8).copyBytes());
        assertArrayEquals(pattern(8), populated.slots().get(7).copyBytes());
        assertArrayEquals(new byte[64], cold.slots().get(7).copyBytes());
        assertThrows(UnsupportedOperationException.class, () -> populated.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> populated.slots().set(7, bank.state(9)));
        bank.restore(cold);
        assertArrayEquals(new byte[64], bank.state(8).copyBytes());
        assertArrayEquals(pattern(8), populated.slots().get(7).copyBytes());
    }

    @Test
    void imageEqualityIsSameOwnerSameIdentityAndAllSixtyFourValuesNotObjectReference() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        var foreign = CdReservedObjectBank.clearedAfterLevelStart();
        var image = bank.state(1).withBytes(pattern(1));
        var equal = image.withBytes(pattern(1));
        assertNotSame(image, equal);
        assertEquals(image, equal);
        assertEquals(image.hashCode(), equal.hashCode());
        var otherIdentity = bank.state(2).withBytes(pattern(1));
        var otherOwner = foreign.state(1).withBytes(pattern(1));
        assertEquals(image.identity(), otherOwner.identity());
        assertArrayEquals(image.copyBytes(), otherOwner.copyBytes());
        assertNotEquals(image, otherIdentity);
        assertNotEquals(image, otherOwner);
        assertNotEquals(image, image.moveByteAt(63, image.byteAt(63) ^ 1));
        assertEquals(3, new HashSet<>(List.of(image, equal, otherIdentity, otherOwner)).size());
    }

    @Test
    void foreignImagesAndSnapshotsRejectWithoutChangingAnyLocalSlot() {
        var bank = patternedBank();
        var foreign = patternedBank();
        var before = bank.capture();
        for (int whole : new int[]{1, 6, 8, 15, 31}) {
            assertThrows(IllegalArgumentException.class, () -> bank.write(foreign.state(whole)));
            assertEquals(before.slots(), bank.capture().slots());
        }
        assertThrows(IllegalArgumentException.class, () -> bank.validateRestore(foreign.capture()));
        assertThrows(IllegalArgumentException.class, () -> bank.restore(foreign.capture()));
        assertThrows(IllegalArgumentException.class,
                () -> bank.compareAndSet(foreign.capture(), List.of(bank.state(1).moveByte(ID, 9))));
        assertEquals(before.slots(), bank.capture().slots());
    }

    @Test
    void validateRestoreIsPureAndRestorationAcceptsAnEarlierSameOwnerFullCut() {
        var bank = patternedBank();
        var saved = bank.capture();
        bank.write(bank.state(1).moveWord(Y_VELOCITY, 0x8000));
        bank.write(bank.state(31).moveByte(ID, 0x19));
        var changed = bank.capture();
        bank.validateRestore(saved);
        assertEquals(changed.slots(), bank.capture().slots());
        bank.restore(saved);
        assertEquals(saved.slots(), bank.capture().slots());
        for (int whole = 1; whole <= 31; whole++) {
            assertArrayEquals(pattern(whole), bank.state(whole).copyBytes());
        }
    }

    @Test
    void compareAndSetCommitsUnorderedUniqueImagesOnlyAfterMatchingTheFullCut() {
        var bank = patternedBank();
        var expected = bank.capture();
        var first = bank.state(1).moveWord(Y_VELOCITY, 0xFE80);
        var shield = bank.state(6).moveByte(ID, 3);
        var last = bank.state(31).moveByte(ANIMATION, 0x0A);
        var supplied = new ArrayList<>(List.of(last, first, shield));
        assertTrue(bank.compareAndSet(expected, supplied));
        supplied.clear();
        for (int whole = 1; whole <= 31; whole++) {
            var wanted = whole == 1 ? first : whole == 6 ? shield : whole == 31 ? last
                    : expected.slots().get(whole - 1);
            assertEquals(wanted, bank.state(whole));
        }
        assertArrayEquals(pattern(1), expected.slots().getFirst().copyBytes());
        assertArrayEquals(pattern(31), expected.slots().getLast().copyBytes());
    }

    @Test
    void staleFullBankCutRejectsEvenWhenTheSelectedImageIsUnchanged() {
        var bank = patternedBank();
        var expected = bank.capture();
        var selected = bank.state(1).moveWord(Y_VELOCITY, 0x8000);
        bank.write(bank.state(31).moveByteAt(63, 0xD7));
        var before = bank.capture();
        assertEquals(expected.slots().getFirst(), bank.state(1));
        assertFalse(bank.compareAndSet(expected, List.of(selected)));
        assertFalse(bank.compareAndSet(expected, List.of()));
        assertEquals(before.slots(), bank.capture().slots());
    }

    @Test
    void valueCompareAndSetAcceptsEquivalentImagesAndRestoredValuesWithoutRevisionAuthority() {
        var bank = patternedBank();
        var expected = bank.capture();
        for (int whole = 1; whole <= 31; whole++) {
            var old = bank.state(whole);
            var equal = old.withBytes(old.copyBytes());
            assertNotSame(old, equal);
            bank.write(equal);
        }
        assertTrue(bank.compareAndSet(expected, List.of()));
        bank.write(bank.state(31).moveByte(ID, 0));
        bank.restore(expected);
        var changed = bank.state(1).moveWordAt(0x14, 0xFEDC);
        assertTrue(bank.compareAndSet(expected, List.of(changed)));
        assertEquals(changed, bank.state(1));
    }

    @Test
    void duplicateForeignAndNullLateImagesPreflightBeforeAnyCompareAndSetWrite() {
        var bank = patternedBank();
        var expected = bank.capture();
        var first = bank.state(1).moveByte(ID, 0x0D);
        var last = bank.state(31).moveByte(ID, 0x19);
        var foreign = CdReservedObjectBank.clearedAfterLevelStart();
        assertThrows(IllegalArgumentException.class,
                () -> bank.compareAndSet(expected, List.of(first, last, last)));
        assertThrows(IllegalArgumentException.class,
                () -> bank.compareAndSet(expected, List.of(first, last, last.moveByte(ID, 0x1C))));
        assertThrows(IllegalArgumentException.class,
                () -> bank.compareAndSet(expected, List.of(first, foreign.state(31))));
        assertThrows(NullPointerException.class,
                () -> bank.compareAndSet(expected, Arrays.asList(first, null)));
        assertEquals(expected.slots(), bank.capture().slots());
    }

    @Test
    void invalidLateWholeIndexAndWidthNeverPublishAnEarlierStagedImage() {
        var bank = patternedBank();
        var expected = bank.capture();
        var first = bank.state(1).moveByte(ID, 0x0D);
        assertThrows(IndexOutOfBoundsException.class,
                () -> bank.compareAndSet(expected, List.of(first, bank.state(32))));
        assertThrows(IndexOutOfBoundsException.class,
                () -> bank.compareAndSet(expected, List.of(first, bank.state(31).moveWordAt(63, 1))));
        assertEquals(expected.slots(), bank.capture().slots());
    }

    @Test
    void invalidSuppliedImagesStillRejectWhenTheExpectedFullCutIsStale() {
        var bank = patternedBank();
        var stale = bank.capture();
        bank.write(bank.state(31).moveByte(ID, 0x19));
        var before = bank.capture();
        var first = bank.state(1).moveByte(ID, 0x0D);
        var foreign = CdReservedObjectBank.clearedAfterLevelStart();
        assertThrows(IllegalArgumentException.class,
                () -> bank.compareAndSet(stale, List.of(first, foreign.state(31))));
        assertThrows(IllegalArgumentException.class,
                () -> bank.compareAndSet(stale, List.of(first, first)));
        assertEquals(before.slots(), bank.capture().slots());
    }

    @Test
    void malformedSnapshotsPreflightCountOrderAndLastOwnerBeforeAnyRestoreOrCasWrite()
            throws ReflectiveOperationException {
        var bank = patternedBank();
        var saved = bank.capture();
        var foreign = patternedBank();
        var tooShort = new ArrayList<>(saved.slots());
        tooShort.removeLast();
        var tooLong = new ArrayList<>(saved.slots());
        tooLong.add(saved.slots().getLast());
        var wrongOrder = new ArrayList<>(saved.slots());
        Collections.swap(wrongOrder, 29, 30);
        var duplicateLast = new ArrayList<>(saved.slots());
        duplicateLast.set(30, saved.slots().get(29));
        var foreignLast = new ArrayList<>(saved.slots());
        foreignLast.set(30, foreign.state(31));
        bank.write(bank.state(1).moveByte(ID, 0x0D));
        bank.write(bank.state(31).moveByte(ID, 0x19));
        var before = bank.capture();
        for (var invalid : List.of(tooShort, tooLong, wrongOrder, duplicateLast, foreignLast)) {
            // Controlled corruption of the private snapshot constructor, not a production seed API.
            var malformed = malformedSnapshot(saved, invalid);
            assertThrows(IllegalArgumentException.class, () -> bank.validateRestore(malformed));
            assertThrows(IllegalArgumentException.class, () -> bank.restore(malformed));
            assertThrows(IllegalArgumentException.class,
                    () -> bank.compareAndSet(malformed, List.of(bank.state(6).moveByte(ID, 3))));
            assertEquals(before.slots(), bank.capture().slots(), "Preflight before the first slot write");
        }
    }

    @Test
    void malformedSnapshotConstructorCopiesItsInputList() throws ReflectiveOperationException {
        var bank = patternedBank();
        var original = bank.capture();
        var supplied = new ArrayList<>(original.slots());
        var copy = malformedSnapshot(original, supplied);
        supplied.clear();
        bank.validateRestore(copy);
        assertEquals(original.slots(), copy.slots());
        assertThrows(UnsupportedOperationException.class, () -> copy.slots().clear());
        assertEquals(original.slots(), bank.capture().slots());
    }

    @Test
    void nullDescriptorsBuffersAndBankArgumentsRejectWithoutChangingStorage() {
        var bank = patternedBank();
        var before = bank.capture();
        var image = bank.state(1);
        assertThrows(NullPointerException.class, () -> image.withBytes(null));
        for (int length : new int[]{0, 63, 65, 128}) {
            assertThrows(IllegalArgumentException.class, () -> image.withBytes(new byte[length]));
        }
        assertThrows(NullPointerException.class, () -> image.unsignedByte(null));
        assertThrows(NullPointerException.class, () -> image.signedByte(null));
        assertThrows(NullPointerException.class, () -> image.word(null));
        assertThrows(NullPointerException.class, () -> image.signedWord(null));
        assertThrows(NullPointerException.class, () -> image.longBits(null));
        assertThrows(NullPointerException.class, () -> bank.write(image.moveByte(null, 1)));
        assertThrows(NullPointerException.class, () -> bank.write(image.moveWord(null, 1)));
        assertThrows(NullPointerException.class, () -> bank.write(image.moveLong(null, 1)));
        assertThrows(NullPointerException.class, () -> bank.write(null));
        assertThrows(NullPointerException.class, () -> bank.validateRestore(null));
        assertThrows(NullPointerException.class, () -> bank.restore(null));
        assertThrows(NullPointerException.class, () -> bank.compareAndSet(null, List.of()));
        assertThrows(NullPointerException.class, () -> bank.compareAndSet(before, null));
        assertEquals(before.slots(), bank.capture().slots());
    }

    @Test
    void nonzeroFullBankRestoreAndForwardReplayRetainUnwrittenBytesAcrossAllThirtyOneSlots() {
        var bank = patternedBank();
        var seed = bank.capture();
        replayStorageWrites(bank);
        var first = bank.capture();
        bank.restore(seed);
        assertEquals(seed.slots(), bank.capture().slots());
        replayStorageWrites(bank);
        assertEquals(first.slots(), bank.capture().slots());
        for (int whole = 1; whole <= 31; whole++) {
            byte[] expected = pattern(whole);
            if (whole == 1) {
                expected[0x12] = (byte) 0xFE; expected[0x13] = (byte) 0x80;
                expected[8] = 0x12; expected[9] = 0x34;
            } else if (whole == 6) {
                expected[0] = 3;
            } else if (whole >= 8 && whole <= 15) {
                expected[0x1C] = (byte) (whole - 7);
            } else if (whole == 31) {
                expected[0x0A] = 1; expected[0x0B] = 0x52;
            }
            assertArrayEquals(expected, bank.state(whole).copyBytes(), "replayed whole " + whole);
            assertArrayEquals(pattern(whole), seed.slots().get(whole - 1).copyBytes());
        }
        assertEquals((Byte.toUnsignedInt(pattern(1)[10]) << 8) | Byte.toUnsignedInt(pattern(1)[11]),
                bank.state(1).word(X_FRACTION));
    }

    private static CdReservedObjectBank patternedBank() {
        var bank = CdReservedObjectBank.clearedAfterLevelStart();
        for (int whole = 1; whole <= 31; whole++) {
            bank.write(bank.state(whole).withBytes(pattern(whole)));
        }
        return bank;
    }

    private static byte[] pattern(int salt) {
        byte[] bytes = new byte[64];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (salt * 13 + i * 37);
        return bytes;
    }

    private static void replayStorageWrites(CdReservedObjectBank bank) {
        var cut = bank.capture();
        var images = new ArrayList<CdReservedObjectBank.Image>();
        images.add(bank.state(1).moveWord(Y_VELOCITY, 0xFE80).moveWord(X, 0x1234));
        images.add(bank.state(6).moveByte(ID, 3));
        for (int whole = 8; whole <= 15; whole++) {
            images.add(bank.state(whole).moveByte(ANIMATION, whole - 7));
        }
        images.add(bank.state(31).moveWordAt(0x0A, 0x0152));
        assertTrue(bank.compareAndSet(cut, images));
    }

    private static CdReservedObjectBank.Snapshot malformedSnapshot(CdReservedObjectBank.Snapshot source,
                                                                   List<CdReservedObjectBank.Image> images)
            throws ReflectiveOperationException {
        var owner = CdReservedObjectBank.Snapshot.class.getDeclaredField("owner");
        owner.setAccessible(true);
        var constructor = CdReservedObjectBank.Snapshot.class.getDeclaredConstructor(Object.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(owner.get(source), images);
    }
}
