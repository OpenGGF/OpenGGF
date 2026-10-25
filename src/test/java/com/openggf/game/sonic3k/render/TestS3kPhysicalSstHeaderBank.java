package com.openggf.game.sonic3k.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent raw-data oracles for the native044 SST layout, explicit clears,
 * partial writes and then-current physical queue visits (Cold R79).
 * No actor, allocator, render producer, accepted-parent or world adapter is
 * represented. Source-only candidate: no compiler or execution in this phase.
 */
class TestS3kPhysicalSstHeaderBank {
    private static final int A = 0xB000;
    private static final int B = 0xB04A;
    private static final int LAST = 0xCF82;
    private static final S3kPhysicalDisplayQueue.CameraWords CAMERA =
            new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF);

    @Test
    void initiallyUnknownBytesNeverBecomeImplicitClearedFields() {
        var bank = new S3kPhysicalSstHeaderBank();
        var snapshot = bank.capture();
        assertEquals(0x1FCC, snapshot.image().length);
        assertEquals(0x1FCC, snapshot.knownBytes().length);
        assertEquals(0x1FCC, snapshot.provenance().length);
        boolean[] unknown = snapshot.knownBytes();
        var unknownOrigins = snapshot.provenance();
        for (int i = 0; i < 0x1FCC; i++) {
            assertFalse(unknown[i]);
            assertNull(unknownOrigins[i]);
        }
        assertThrows(IllegalStateException.class, () -> bank.read(A));
        assertThrows(IllegalStateException.class, () -> bank.code(A));
        assertThrows(IllegalStateException.class, () -> bank.priority(A));
        assertThrows(IllegalStateException.class, () -> bank.artTile(A));
        assertThrows(IllegalStateException.class, () -> bank.mappings(A));
        assertThrows(IllegalStateException.class, () -> bank.mappingFrame(A));
        assertThrows(IllegalStateException.class, () -> bank.xFraction(A));
        assertThrows(IllegalStateException.class, () -> bank.yFraction(A));

        bank.writeLong(A, 0, 0, "044 code-only write");
        bank.writeByte(A, 4, 0, "044 flags-only write");
        assertEquals(0, bank.code(A));
        assertEquals(0, bank.readByte(A, 4));
        assertFalse(bank.isKnown(A, 6));
        assertThrows(IllegalStateException.class, () -> bank.read(A));
        assertThrows(IllegalStateException.class, () -> bank.mappings(A));
    }

    @Test
    void everyMissingProjectionByteFailsBeforeAnyQueueOrFlagMutation() {
        // Independent byte addresses consumed by the existing projection contract.
        int[] required = {4, 6, 7, 0x10, 0x11, 0x14, 0x15};
        int[] literalBytes = {0x84, 0, 8, 0, 10, 0, 20};
        for (int missing : required) {
            var bank = new S3kPhysicalSstHeaderBank();
            for (int i = 0; i < required.length; i++) {
                if (required[i] != missing) {
                    bank.writeByte(A, required[i], literalBytes[i], "declared partial header write");
                }
            }
            var queue = new S3kPhysicalDisplayQueue();
            queue.submit(A, 0);
            queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
            var before = bank.capture();
            byte[] queueBefore = queue.capture().image();
            assertThrows(IllegalStateException.class, () -> queue.publishNext(bank, CAMERA),
                    "missing byte " + missing);
            sameBank(before, bank.capture());
            assertArrayEquals(queueBefore, queue.capture().image());
            assertThrows(IllegalStateException.class, () -> bank.writeRenderFlags(A, 0x80));
            sameBank(before, bank.capture());
        }
    }

    @Test
    void fullyKnownProjectionDoesNotPretendOtherConsumedFieldsAreKnown() {
        var bank = new S3kPhysicalSstHeaderBank();
        header(bank, A, 0x85, 0xFFFF, 0x8123, 4, 12);
        assertEquals(new S3kPhysicalDisplayQueue.Header(0x85, 0xFFFF, 0x8123, 4, 12),
                bank.read(A));
        assertFalse(bank.isKnown(A, 0));
        assertThrows(IllegalStateException.class, () -> bank.code(A));
        assertThrows(IllegalStateException.class, () -> bank.mappings(A));
        assertThrows(IllegalStateException.class, () -> bank.xFraction(A));
        assertThrows(IllegalStateException.class, () -> bank.origin(A, 0));
        bank.writeRenderFlags(A, 5);
        assertEquals(5, bank.readByte(A, 4));
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.RENDER_PUBLICATION,
                bank.origin(A, 4).kind());
        assertThrows(IllegalStateException.class, () -> bank.priority(A));
    }

    @Test
    void typedPartialWritesUseBigEndianWidthsAndPreserveEveryUntouchedByte() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.writeLong(A, 0, 0xFEDCBA98L, "native code long");
        bank.writeWord(A, 8, 0x0380, "native priority word");
        bank.writeWord(A, 0xA, 0x8123, "native art word");
        bank.writeLong(A, 0xC, 0x00184F34, "native mappings long");
        bank.writeWord(A, 0x12, 0xABCD, "native x fraction");
        bank.writeWord(A, 0x16, 0xFEDC, "native y fraction");
        bank.writeByte(A, 0x22, 0xFE, "native mapping frame byte");
        assertEquals(0xFEDCBA98L, bank.code(A));
        assertEquals(0x0380, bank.priority(A));
        assertEquals(0x8123, bank.artTile(A));
        assertEquals(0x00184F34, bank.mappings(A));
        assertEquals(0xABCD, bank.xFraction(A));
        assertEquals(0xFEDC, bank.yFraction(A));
        assertEquals(0xFE, bank.mappingFrame(A));
        assertArrayEquals(new byte[]{(byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98},
                java.util.Arrays.copyOfRange(bank.capture().image(), 0, 4));
        assertEquals(0x18, bank.readByte(A, 0xD));
        assertFalse(bank.isKnown(A, 4));
        assertFalse(bank.isKnown(A, 0x10));
        assertFalse(bank.isKnown(A, 0x23));

        bank.writeByte(A, 1, 0x11, "native one-byte overwrite");
        assertEquals(0xFE11BA98L, bank.code(A));
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.PARTIAL_BYTE, bank.origin(A, 1).kind());
        assertEquals("native one-byte overwrite", bank.origin(A, 1).nativeWriter());
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.PARTIAL_LONG, bank.origin(A, 0).kind());
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.PARTIAL_LONG, bank.origin(A, 2).kind());
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.PARTIAL_WORD, bank.origin(A, 8).kind());
    }

    @Test
    void knownLeadingBytesDoNotSupplyUnknownLongOrFractionTails() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.writeWord(A, 0, 0, "native first code word only");
        bank.writeWord(A, 0xC, 0x1234, "native first mappings word only");
        bank.writeByte(A, 0x16, 0, "native first y-fraction byte only");
        assertThrows(IllegalStateException.class, () -> bank.code(A));
        assertThrows(IllegalStateException.class, () -> bank.mappings(A));
        assertThrows(IllegalStateException.class, () -> bank.yFraction(A));
        assertFalse(bank.isKnown(A, 2));
        assertFalse(bank.isKnown(A, 0xE));
        assertFalse(bank.isKnown(A, 0x17));
        bank.writeWord(A, 2, 0x5678, "native remaining code word");
        assertEquals(0x00005678, bank.code(A));
        assertThrows(IllegalStateException.class, () -> bank.mappings(A));
    }

    @Test
    void explicitSpriteClearTouchesExactlySeventyFourBytesAndHasDistinctProvenance() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.writeByte(B, 0, 0xAB, "unrelated slot byte");
        var beforeB = bank.capture();
        bank.clearSprite(A);
        var after = bank.capture();
        for (int i = 0; i < 0x4A; i++) {
            assertEquals(0, bank.readByte(A, i));
            assertTrue(bank.isKnown(A, i));
            assertEquals(S3kPhysicalSstHeaderBank.WriteKind.SPRITE_CLEAR,
                    bank.origin(A, i).kind());
        }
        assertEquals(0xAB, bank.readByte(B, 0));
        assertEquals(beforeB.provenance()[0x4A], after.provenance()[0x4A]);
        assertFalse(bank.isKnown(B, 1));
        assertThrows(IllegalStateException.class, () -> bank.code(B));
        assertEquals(0, bank.code(A));
        assertEquals(0, bank.mappings(A));
        assertEquals(0, bank.mappingFrame(A));
    }

    @Test
    void explicitLevelClearCoversAll110SlotsWithoutResettingTheSeparateQueue() {
        var bank = new S3kPhysicalSstHeaderBank();
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(LAST, 0x380);
        byte[] pending = queue.capture().image();
        bank.writeByte(LAST, 0x49, 0xFF, "last actual byte");
        bank.clearLevelObjectRam();
        var cleared = bank.capture();
        byte[] bytes = cleared.image();
        boolean[] known = cleared.knownBytes();
        var origins = cleared.provenance();
        for (int i = 0; i < 0x1FCC; i++) {
            assertEquals(0, bytes[i]);
            assertTrue(known[i]);
            assertEquals(S3kPhysicalSstHeaderBank.WriteKind.LEVEL_CLEAR, origins[i].kind());
        }
        assertArrayEquals(pending, queue.capture().image());
        assertEquals(0, bank.readByte(LAST, 0x49));
    }

    @Test
    void physicalEndpointsEvenLongsAndPerSlotRangesAreExplicit() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.writeLong(B, 0, 0x11223344, "even but not four-aligned native long");
        bank.writeLong(LAST, 0x46, 0xA1B2C3D4L, "last four-byte native write");
        assertEquals(0x11223344, bank.readLong(B, 0));
        assertEquals(0xA1B2C3D4L, bank.readLong(LAST, 0x46));
        assertEquals(0xD4, bank.readByte(LAST, 0x49));
        assertEquals(0xC3D4, bank.readWord(LAST, 0x48));
        var beforeInvalid = bank.capture();
        for (int address : new int[]{0xAFFF, 0xB001, 0xB002, 0xCFCC, 0xFFFFB000, -1}) {
            assertThrows(IllegalArgumentException.class, () -> bank.clearSprite(address));
            assertThrows(IllegalArgumentException.class, () -> bank.readByte(address, 0));
        }
        for (int offset : new int[]{-1, 0x4A, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bank.writeByte(A, offset, 1, "invalid"));
        }
        for (int offset : new int[]{1, 0x49, 0x4A}) {
            assertThrows(IllegalArgumentException.class, () -> bank.readWord(A, offset));
            assertThrows(IllegalArgumentException.class, () -> bank.writeWord(A, offset, 1, "invalid"));
        }
        for (int offset : new int[]{1, 0x47, 0x48}) {
            assertThrows(IllegalArgumentException.class, () -> bank.readLong(A, offset));
            assertThrows(IllegalArgumentException.class, () -> bank.writeLong(A, offset, 1, "invalid"));
        }
        assertThrows(IllegalArgumentException.class, () -> bank.writeByte(A, 0, 256, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> bank.writeWord(A, 0, -1, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> bank.writeLong(A, 0, 0x100000000L, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> bank.writeByte(A, 0, 1, " "));
        assertThrows(NullPointerException.class, () -> bank.writeByte(A, 0, 1, null));
        assertFalse(bank.isKnown(A, 0));
        sameBank(beforeInvalid, bank.capture());
    }

    @Test
    void actualClearThenQueuedVisitPublishesIntoCodeZeroFreeResidue() {
        var bank = new S3kPhysicalSstHeaderBank();
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        bank.clearSprite(A); // The actual full74-byte clear, not absence/registration inference.
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertTrue(queue.publishNext(bank, CAMERA).published());
        assertEquals(0x80, bank.readByte(A, 4));
        assertEquals(0, bank.code(A));
        assertEquals(0, bank.mappings(A));
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.RENDER_PUBLICATION, bank.origin(A, 4).kind());
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.SPRITE_CLEAR, bank.origin(A, 0).kind());
        queue.finishVisit();
        assertNull(queue.publishNext(bank, CAMERA));

        // Code-only reuse writes no other bytes; the bank owns no occupancy/free callback.
        bank.writeLong(A, 0, 0x000915F6, "native partial code replacement");
        assertEquals(0x000915F6, bank.code(A));
        assertEquals(0x80, bank.readByte(A, 4));
        assertEquals(0, bank.readByte(A, 7));
        assertEquals(S3kPhysicalSstHeaderBank.WriteKind.RENDER_PUBLICATION, bank.origin(A, 4).kind());
    }

    @Test
    void duplicateVisitsRereadPhysicalReuseAndClearRatherThanAnEnqueueOwner() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.clearSprite(A);
        bank.clearSprite(B);
        header(bank, A, 4, 10, 10, 8, 0);
        header(bank, B, 1, 0xFFFF, 0xFFFF, 255, 255); // screen-coordinate header
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        queue.submit(B, 0);
        queue.submit(A, 0);
        queue.submit(A, 0);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertTrue(queue.publishNext(bank, CAMERA).published());
        queue.finishVisit();
        assertTrue(queue.publishNext(bank, CAMERA).published());
        assertEquals(0x81, bank.readByte(B, 4));
        queue.finishVisit();

        bank.writeLong(A, 0, 0x000917C0, "native code reuse");
        header(bank, A, 0x87, 400, 0, 4, 4);
        var reused = queue.publishNext(bank, CAMERA);
        assertEquals(A, reused.addressWord());
        assertEquals(0x87, reused.flagsBefore());
        assertFalse(reused.published());
        assertEquals(7, bank.readByte(A, 4));
        assertEquals(0x000917C0, bank.code(A));
        assertEquals(0x81, bank.readByte(B, 4));
        queue.finishVisit();

        bank.clearSprite(A);
        assertTrue(queue.publishNext(bank, CAMERA).published());
        assertEquals(0x80, bank.readByte(A, 4));
        assertEquals(0, bank.code(A));
        queue.finishVisit();
        assertNull(queue.publishNext(bank, CAMERA));
    }

    @Test
    void snapshotsDefendBytesKnownnessAndProvenanceAndRestoreUnknownHolesExactly() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.writeByte(A, 0x49, 0xFE, "native tail byte");
        bank.clearSprite(B);
        var saved = bank.capture();
        saved.image()[0x49] = 1;
        saved.knownBytes()[0] = true;
        saved.provenance()[0x49] = null;
        bank.clearLevelObjectRam();
        bank.writeByte(B, 4, 0x80, "later known residue");
        for (int replay = 0; replay < 2; replay++) {
            bank.restore(saved);
            sameBank(saved, bank.capture());
            assertFalse(bank.isKnown(A, 0));
            assertThrows(IllegalStateException.class, () -> bank.code(A));
            assertEquals(0xFE, bank.readByte(A, 0x49));
            assertEquals("native tail byte", bank.origin(A, 0x49).nativeWriter());
            bank.writeByte(A, 0x49, 3, "later mutation");
        }
        assertEquals((byte) 0xFE, saved.image()[0x49]);
        assertFalse(saved.knownBytes()[0]);
    }

    @Test
    void coordinatedBankAndQueueSnapshotsReplayExactLocalStateTwice() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.clearSprite(A);
        bank.clearSprite(B);
        header(bank, A, 4, 10, 10, 8, 0);
        header(bank, B, 1, 0, 0, 0, 0);
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        queue.submit(B, 0);
        queue.submit(A, 0);
        var savedBank = bank.capture();
        var savedQueue = queue.capture();
        List<S3kPhysicalDisplayQueue.Visit> first = localForward(bank, queue);
        assertEquals(List.of(0x84, 0x81, 0x80),
                first.stream().map(S3kPhysicalDisplayQueue.Visit::flagsAfter).toList());
        var finalBank = bank.capture();
        byte[] finalQueue = queue.capture().image();
        for (int replay = 0; replay < 2; replay++) {
            bank.restore(savedBank);
            queue.restore(savedQueue);
            assertEquals(first, localForward(bank, queue));
            sameBank(finalBank, bank.capture());
            assertArrayEquals(finalQueue, queue.capture().image());
        }
        assertFalse(bank.isKnown(LAST, 0));
    }

    @Test
    void coordinatedOpenVisitRestoreRetainsPublishedFlagsWithoutRepeatingPublication() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.clearSprite(A);
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        queue.publishNext(bank, CAMERA);
        var savedBank = bank.capture();
        var savedQueue = queue.capture();
        for (int replay = 0; replay < 2; replay++) {
            bank.writeLong(A, 0, 0x12345678, "discarded later code");
            bank.restore(savedBank);
            queue.restore(savedQueue);
            assertEquals(0, bank.code(A));
            assertEquals(0x80, bank.readByte(A, 4));
            assertThrows(IllegalStateException.class, () -> queue.publishNext(bank, CAMERA));
            sameBank(savedBank, bank.capture());
            queue.finishVisit();
            assertNull(queue.publishNext(bank, CAMERA));
        }
    }

    private static List<S3kPhysicalDisplayQueue.Visit> localForward(
            S3kPhysicalSstHeaderBank bank, S3kPhysicalDisplayQueue queue) {
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        List<S3kPhysicalDisplayQueue.Visit> visits = new ArrayList<>();
        visits.add(queue.publishNext(bank, CAMERA));
        queue.finishVisit();
        visits.add(queue.publishNext(bank, CAMERA));
        queue.finishVisit();
        bank.clearSprite(A);
        visits.add(queue.publishNext(bank, CAMERA));
        queue.finishVisit();
        assertNull(queue.publishNext(bank, CAMERA));
        return visits;
    }

    @Test
    void snapshotValuesDistinguishUnknownBytesAndOriginsAndWorkAsHashKeys() {
        var bank = new S3kPhysicalSstHeaderBank();
        var independent = new S3kPhysicalSstHeaderBank();
        var unknown = bank.capture();
        assertNotSame(unknown, independent.capture());
        assertEquals(unknown, independent.capture());
        assertEquals(unknown.hashCode(), independent.capture().hashCode());
        var values = new java.util.HashSet<S3kPhysicalSstHeaderBank.Snapshot>();
        values.add(unknown);

        bank.writeByte(A, 4, 0, "declared flags writer");
        var writtenZero = bank.capture();
        assertArrayEquals(unknown.image(), writtenZero.image());
        assertNotEquals(unknown, writtenZero, "known zero is not an unknown zero-shaped backing byte");
        independent.writeByte(A, 4, 0, new String("declared flags writer"));
        var sameValue = independent.capture();
        assertNotSame(writtenZero, sameValue);
        assertEquals(writtenZero, sameValue);
        assertEquals(sameValue, writtenZero);
        assertEquals(writtenZero.hashCode(), sameValue.hashCode());
        values.add(writtenZero);
        assertTrue(values.contains(sameValue));

        independent.writeByte(A, 4, 0, "different declared writer");
        var differentOrigin = independent.capture();
        assertArrayEquals(writtenZero.image(), differentOrigin.image());
        assertArrayEquals(writtenZero.knownBytes(), differentOrigin.knownBytes());
        assertNotEquals(writtenZero, differentOrigin, "same bytes/knownness do not erase provenance");
        bank.writeByte(A, 4, 0x80, "declared flags writer");
        var differentByte = bank.capture();
        assertArrayEquals(writtenZero.knownBytes(), differentByte.knownBytes());
        assertArrayEquals(writtenZero.provenance(), differentByte.provenance());
        assertNotEquals(writtenZero, differentByte, "the actual backing bytes also belong to the value");
        assertNotEquals(writtenZero, null);
        assertNotEquals(writtenZero, new Object());

        writtenZero.image()[4] = (byte) 0xFF;
        writtenZero.knownBytes()[4] = false;
        writtenZero.provenance()[4] = null;
        for (int cycle = 0; cycle < 2; cycle++) {
            bank.restore(writtenZero);
            assertEquals(sameValue, bank.capture());
            assertTrue(values.contains(bank.capture()), "restored equal values have compatible hash codes");
            bank.clearSprite(A);
        }
        assertEquals(2, values.size(), "escaped arrays and later bank writes cannot change saved hash keys");
        assertTrue(values.contains(writtenZero));
        assertTrue(values.contains(unknown));
    }

    private static void header(S3kPhysicalSstHeaderBank bank, int address,
                               int flags, int x, int y, int width, int height) {
        bank.writeByte(address, 4, flags, "declared native flags write");
        bank.writeByte(address, 6, height, "declared native height write");
        bank.writeByte(address, 7, width, "declared native width write");
        bank.writeWord(address, 0x10, x, "declared native x word");
        bank.writeWord(address, 0x14, y, "declared native y word");
    }

    private static void sameBank(S3kPhysicalSstHeaderBank.Snapshot expected,
                                 S3kPhysicalSstHeaderBank.Snapshot actual) {
        assertArrayEquals(expected.image(), actual.image());
        assertArrayEquals(expected.knownBytes(), actual.knownBytes());
        assertArrayEquals(expected.provenance(), actual.provenance());
    }
}
