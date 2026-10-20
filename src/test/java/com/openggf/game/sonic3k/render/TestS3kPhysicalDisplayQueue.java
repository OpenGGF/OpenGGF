package com.openggf.game.sonic3k.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent pure-data oracles from native044 Draw_Sprite / Render_Sprites
 * (sonic3k.asm:36171..36192,36376..36430). No game, renderer, ROM loader,
 * accepted-parent, or whole-world fixture is represented by these doubles.
 * Authored in Cold R76; unexecuted at its source-review boundary.
 */
class TestS3kPhysicalDisplayQueue {
    private static final int A = 0xB000;
    private static final int B = 0xB04A;
    private static final int C = 0xB094;
    private static final S3kPhysicalDisplayQueue.CameraWords CAMERA =
            new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF);

    @Test
    void sixtyThirdEntryUsesTheLastWordThenSpillsToTheNextPriority() {
        var queue = new S3kPhysicalDisplayQueue();
        for (int i = 0; i < 62; i++) {
            assertNotNull(queue.submit(A, 0));
        }
        var last = queue.submit(B, 0);
        assertEquals(0, last.region());
        assertEquals(0x7E, last.entryOffset());
        byte[] full = queue.capture().image();
        assertEquals(0x007E, word(full, 0));
        assertEquals(B, word(full, 0x7E));

        var spill = queue.submit(C, 0);
        assertEquals(1, spill.region());
        assertEquals(2, spill.entryOffset());
        byte[] after = queue.capture().image();
        assertEquals(0x007E, word(after, 0));
        assertEquals(2, word(after, 0x80));
        assertEquals(C, word(after, 0x82));
    }

    @Test
    void spillSkipsFullRegionsAndTheLastRegionRejectsWithoutChangingBytesOrFlags() {
        var queue = new S3kPhysicalDisplayQueue();
        for (int region = 0; region < 7; region++) {
            for (int i = 0; i < 63; i++) {
                assertNotNull(queue.submit(A, region * 0x80));
            }
        }
        var spill = queue.submit(B, 0);
        assertEquals(7, spill.region());
        assertEquals(2, spill.entryOffset());
        for (int i = 1; i < 63; i++) {
            assertNotNull(queue.submit(C, 0x380));
        }
        var memory = new Memory();
        memory.put(B, 0, header(0x81, 0, 0, 8, 0));
        byte[] before = queue.capture().image();
        assertNull(queue.submit(B, 0x380));
        assertNull(queue.submit(B, 0));
        assertArrayEquals(before, queue.capture().image());
        assertEquals(0x81, memory.flags(B));
        assertTrue(memory.writes.isEmpty());
        assertTrue(memory.reads.isEmpty());
    }

    @Test
    void visitsUseRegionOrderAndSubmissionFifoIncludingDuplicatePhysicalWords() {
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0x100);
        queue.submit(B, 0);
        queue.submit(C, 0x100);
        queue.submit(A, 0x80);
        queue.submit(A, 0);
        var memory = inside(A, B, C);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        List<Integer> addresses = new ArrayList<>();
        List<Integer> regions = new ArrayList<>();
        S3kPhysicalDisplayQueue.Visit visit;
        while ((visit = queue.publishNext(memory, CAMERA)) != null) {
            addresses.add(visit.addressWord());
            regions.add(visit.region());
            queue.finishVisit();
        }
        assertEquals(List.of(B, A, A, A, C), addresses);
        assertEquals(List.of(0, 0, 1, 2, 2), regions);
        byte[] after = queue.capture().image();
        assertEquals(0, word(after, 0));
        assertEquals(B, word(after, 2));
        assertEquals(A, word(after, 4));
        assertEquals(A, word(after, 0x82));
        assertEquals(A, word(after, 0x102));
        assertEquals(C, word(after, 0x104));
    }

    @Test
    void noSubmissionOrNoVisitPreservesPriorFlagsAndBeginningAPassDoesNotClearCounts() {
        var queue = new S3kPhysicalDisplayQueue();
        var memory = new Memory();
        memory.put(A, 0x915F6, header(0x85, 0x400, 0x400, 4, 4));
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertNull(queue.publishNext(memory, CAMERA));
        assertEquals(0x85, memory.flags(A));
        assertTrue(memory.reads.isEmpty());

        queue.submit(A, 0);
        byte[] submitted = queue.capture().image();
        assertEquals(0x85, memory.flags(A));
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertArrayEquals(submitted, queue.capture().image());
        assertEquals(0x85, memory.flags(A));
        assertTrue(memory.writes.isEmpty());
    }

    @Test
    void normalGeometryUsesSignedWordXAndUnsignedWrappedYWithIndependentNativeDimensions() {
        record Case(int x, int y, int width, int height, int cx, int cy, int wrap, boolean visible) { }
        List<Case> cases = List.of(
                new Case(-8, 0, 8, 0, 0, 0, 0x7FF, true),
                new Case(-9, 0, 8, 0, 0, 0, 0x7FF, false),
                new Case(327, 0, 8, 0, 0, 0, 0x7FF, true),
                new Case(328, 0, 8, 0, 0, 0, 0x7FF, false),
                new Case(0x7FFF, 0, 8, 0, 0x8007, 0, 0x7FF, true),
                new Case(0x7FF8, 0, 8, 0, 0, 0, 0x7FF, false),
                new Case(-255, 0, 255, 0, 0, 0, 0x7FF, true),
                new Case(0, 223, 8, 0, 0, 0, 0x7FF, true),
                new Case(0, 224, 8, 0, 0, 0, 0x7FF, false),
                new Case(0, -1, 8, 0, 0, 0, 0x7FF, false),
                new Case(0, 0x80C, 8, 0, 0, 0, 0x7FF, true),
                new Case(0, 0x80C, 8, 0, 0, 0, 0xFFF, false),
                new Case(0, -12, 4, 12, 0, 0, 0x7FF, true),
                new Case(0, -13, 4, 12, 0, 0, 0x7FF, false),
                new Case(0, 235, 4, 12, 0, 0, 0x7FF, true),
                new Case(0, 236, 4, 12, 0, 0, 0x7FF, false),
                new Case(0, -255, 4, 255, 0, 0, 0x7FF, true),
                new Case(0, 478, 4, 255, 0, 0, 0x7FF, true),
                new Case(0, 479, 4, 255, 0, 0, 0x7FF, false),
                new Case(100, 200, 4, 0, 100, 200, 0x7FF, true));
        for (Case c : cases) {
            var queue = new S3kPhysicalDisplayQueue();
            var memory = new Memory();
            memory.put(A, 1, header(0xA7, c.x(), c.y(), c.width(), c.height()));
            queue.submit(A, 0);
            queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
            var visit = queue.publishNext(memory,
                    new S3kPhysicalDisplayQueue.CameraWords(c.cx(), c.cy(), c.wrap()));
            assertEquals(c.visible(), visit.published(), c.toString());
            assertEquals(c.visible() ? 0xA7 : 0x27, memory.flags(A), c.toString());
            assertEquals(c.visible()
                            ? List.of(new FlagWrite(A, 0x27), new FlagWrite(A, 0xA7))
                            : List.of(new FlagWrite(A, 0x27)),
                    memory.writes, c.toString());
            queue.finishVisit();
        }
    }

    @Test
    void screenCoordinatesSkipWorldGeometryAndRetainAllOtherFlagBits() {
        var queue = new S3kPhysicalDisplayQueue();
        var memory = new Memory();
        memory.put(A, 0, header(0xBB, 0x8000, 0xFFFF, 255, 255)); // bit2 clear, bit6 clear
        queue.submit(A, 0);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var visit = queue.publishNext(memory, new S3kPhysicalDisplayQueue.CameraWords(123, 456, 0xFFF));
        assertTrue(visit.published());
        assertEquals(0xBB, memory.flags(A));
        assertEquals(List.of(new FlagWrite(A, 0x3B), new FlagWrite(A, 0xBB)), memory.writes);
        queue.finishVisit();
    }

    @Test
    void publicationIsWrittenBeforeTheSeparateZeroPieceOrExhaustedSatTailAndBeforeCountDecrement() {
        // These downstream values are data only; the publication core cannot consult them.
        record PieceTail(int pieceCount, int satBudget) { }
        for (PieceTail tail : List.of(new PieceTail(0, 79), new PieceTail(1, -1))) {
            var queue = new S3kPhysicalDisplayQueue();
            var memory = inside(A);
            queue.submit(A, 0);
            queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
            var visit = queue.publishNext(memory, CAMERA);
            assertTrue(visit.published());
            assertEquals(0x84, memory.flags(A), "already published before piece/SAT work: " + tail);
            assertEquals(2, word(queue.capture().image(), 0), "NextObj has not yet decremented");
            assertThrows(IllegalStateException.class, () -> queue.publishNext(memory, CAMERA));
            // Model the native tail's no-piece/full-SAT branch only AFTER observing publication.
            assertTrue(tail.pieceCount() == 0 || tail.satBudget() < 0);
            queue.finishVisit();
            assertEquals(0, word(queue.capture().image(), 0));
        }
    }

    @Test
    void duplicateVisitsResolveThenCurrentReusedSlotWithoutRetainingAnEnqueueOwner() {
        var queue = new S3kPhysicalDisplayQueue();
        var memory = inside(A);
        Slot old = memory.slots.get(A);
        queue.submit(A, 0);
        queue.submit(A, 0);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertTrue(queue.publishNext(memory, CAMERA).published());
        queue.finishVisit();
        memory.put(A, 0x917C0, header(0x87, 400, 0, 4, 4));
        var second = queue.publishNext(memory, CAMERA);
        assertEquals(0x87, second.flagsBefore());
        assertFalse(second.published());
        assertEquals(7, memory.flags(A));
        assertEquals(0x84, old.header.renderFlags(), "reused slot no longer writes the detached data record");
        assertEquals(List.of(A, A), memory.reads);
        assertEquals(0x917C0, memory.slots.get(A).code);
        queue.finishVisit();
    }

    @Test
    void explicitlyClearedCodeZeroSlotIsVisitedAndPublishesIntoItsCurrentResidue() {
        var queue = new S3kPhysicalDisplayQueue();
        var memory = inside(A);
        queue.submit(A, 0);
        // Literal result of native74-byte clear, not missing-owner normalization.
        memory.put(A, 0, header(0, 0, 0, 0, 0));
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var visit = queue.publishNext(memory, CAMERA);
        assertTrue(visit.published());
        assertEquals(0x80, memory.flags(A));
        assertEquals(0, memory.slots.get(A).code, "publication does not allocate/change code");
        assertEquals(List.of(new FlagWrite(A, 0), new FlagWrite(A, 0x80)), memory.writes);
        // R75 read canonical ROM0/frame0 offset0/count0. Mapping decode is deliberately outside this core.
        queue.finishVisit();
    }

    @Test
    void callerSuppliedPartialAttributeResidueKeepsPriorFlagsUntilTheActualVisit() {
        var queue = new S3kPhysicalDisplayQueue();
        var memory = new Memory();
        memory.put(A, 0x85AD2, header(0x85, 0, 0, 32, 32));
        queue.submit(A, 0);
        // A pure-data projection of the fields SetUp_ObjAttributes replaces; not a real owner adapter.
        memory.put(A, 0x915F6, header(0x85, 328, 0, 4, 4));
        assertEquals(0x85, memory.flags(A));
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var visit = queue.publishNext(memory, CAMERA);
        assertEquals(0x85, visit.flagsBefore());
        assertFalse(visit.published());
        assertEquals(5, memory.flags(A));
        queue.finishVisit();
    }

    @Test
    void unresolvedOrUnsupportedProjectionDoesNotInventAHeaderOrConsumeTheEntry() {
        var queue = new S3kPhysicalDisplayQueue();
        var memory = new Memory();
        queue.submit(A, 0);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var before = queue.capture();
        assertThrows(IllegalStateException.class, () -> queue.publishNext(memory, CAMERA));
        assertArrayEquals(before.image(), queue.capture().image());
        assertTrue(memory.writes.isEmpty());
        memory.put(A, 1, header(0xC4, 0, 0, 8, 8));
        assertThrows(UnsupportedOperationException.class, () -> queue.publishNext(memory, CAMERA));
        assertArrayEquals(before.image(), queue.capture().image());
        assertEquals(0xC4, memory.flags(A));
        assertTrue(memory.writes.isEmpty());
        assertThrows(IllegalStateException.class, queue::finishVisit);

        var competition = new S3kPhysicalDisplayQueue();
        competition.submit(A, 0);
        byte[] queued = competition.capture().image();
        assertThrows(UnsupportedOperationException.class,
                () -> competition.beginPass(S3kPhysicalDisplayQueue.Consumer.COMPETITION));
        assertArrayEquals(queued, competition.capture().image());
    }

    @Test
    void internalCaptureIsImmutableAndReplaysTheExactPendingCursorAndStaleBytesTwice() {
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        queue.submit(B, 0);
        var memory = inside(A, B, C);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertEquals(A, queue.publishNext(memory, CAMERA).addressWord());
        queue.finishVisit();
        var pending = queue.capture();
        byte[] escaped = pending.image();
        escaped[1] = 0;
        escaped[4] = 0;
        assertEquals(2, word(pending.image(), 0));
        assertEquals(B, word(pending.image(), 4));

        byte[] expected = new byte[1024]; // counts drained, all untouched/stale words remain native bytes
        expected[2] = (byte) 0xB0;
        expected[3] = 0;
        expected[4] = (byte) 0xB0;
        expected[5] = 0x4A;
        expected[0x102] = (byte) 0xB0;
        expected[0x103] = (byte) 0x94;
        for (int cycle = 0; cycle < 2; cycle++) {
            queue.restore(pending);
            assertEquals(B, queue.publishNext(memory, CAMERA).addressWord());
            queue.finishVisit();
            assertNull(queue.publishNext(memory, CAMERA));
            queue.submit(C, 0x100);
            queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
            assertEquals(C, queue.publishNext(memory, CAMERA).addressWord());
            queue.finishVisit();
            assertNull(queue.publishNext(memory, CAMERA));
            assertArrayEquals(expected, queue.capture().image());
        }
        assertEquals(2, word(pending.image(), 0), "later writes cannot mutate the saved input image");
        assertEquals(0, word(pending.image(), 0x100));
        // This captures QUEUE state only. No caller header/world snapshot closure is asserted.
    }

    @Test
    void captureOfPublishedButUnfinishedEntryCannotRepeatItsFlagWritesAfterRestore() {
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        queue.submit(B, 0);
        var memory = inside(A, B);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        queue.publishNext(memory, CAMERA);
        var inFlight = queue.capture();
        queue.finishVisit();
        queue.restore(inFlight);
        int writes = memory.writes.size();
        assertThrows(IllegalStateException.class, () -> queue.publishNext(memory, CAMERA));
        assertEquals(writes, memory.writes.size());
        queue.finishVisit();
        assertEquals(B, queue.publishNext(memory, CAMERA).addressWord());
        queue.finishVisit();
        assertNull(queue.publishNext(memory, CAMERA));
    }

    @Test
    void callerDomainRejectsInvalidPriorityOrPhysicalWordWithoutClampingOrErasingAQueue() {
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(0xCF82, 0x380); // last of110 physical SSTs, not last dynamic allocation slot
        byte[] before = queue.capture().image();
        assertThrows(IllegalArgumentException.class, () -> queue.submit(A, 0x400));
        assertThrows(IllegalArgumentException.class, () -> queue.submit(A, 0x81));
        assertThrows(IllegalArgumentException.class, () -> queue.submit(0xB001, 0));
        assertThrows(IllegalArgumentException.class, () -> queue.submit(0xCFCC, 0));
        assertThrows(IllegalArgumentException.class, () -> queue.submit(0xFFFFB000, 0));
        assertArrayEquals(before, queue.capture().image());
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        assertThrows(IllegalStateException.class, () -> queue.submit(A, 0));
        assertArrayEquals(before, queue.capture().image());
    }

    @Test
    void snapshotValuesCompareIndependentCapturesAndRemainStableHashKeysAfterRestore() {
        var queue = new S3kPhysicalDisplayQueue();
        var independent = new S3kPhysicalDisplayQueue();
        assertEquals(queue.capture(), independent.capture());
        queue.submit(A, 0);
        independent.submit(A, 0);
        var saved = queue.capture();
        var sameValue = independent.capture();
        assertNotSame(saved, sameValue);
        assertEquals(saved, sameValue);
        assertEquals(sameValue, saved);
        assertEquals(saved.hashCode(), sameValue.hashCode());
        var values = new java.util.HashSet<S3kPhysicalDisplayQueue.Snapshot>();
        values.add(saved);
        assertTrue(values.contains(sameValue));
        independent.submit(B, 0x80);
        assertNotEquals(saved, independent.capture(), "new physical queue bytes change the value");
        assertNotEquals(saved, null);
        assertNotEquals(saved, new Object());
        saved.image()[2] = 0;
        for (int cycle = 0; cycle < 2; cycle++) {
            queue.submit(B, 0x100);
            queue.restore(saved);
            assertEquals(sameValue, queue.capture());
            assertTrue(values.contains(queue.capture()));
        }
        assertEquals(1, values.size());
        assertTrue(values.contains(saved), "defensive image copies and later queue writes cannot change a hash key");
    }

    @Test
    void identicalInputImagesDoNotHideWalkingOpenVisitRegionOrEntryCursorDifferences() {
        var queue = new S3kPhysicalDisplayQueue();
        var idle = queue.capture();
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var walking = queue.capture();
        assertArrayEquals(idle.image(), walking.image());
        assertNotEquals(idle, walking, "beginPass changes walking even with no submitted input");
        assertNull(queue.publishNext(inside(), CAMERA));
        var ended = queue.capture();
        assertArrayEquals(idle.image(), ended.image());
        assertNotEquals(idle, ended, "a completed empty pass retains its region8 cursor");

        var tail = new S3kPhysicalDisplayQueue();
        tail.submit(A, 0);
        tail.submit(A, 0);
        tail.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        tail.publishNext(inside(A), CAMERA);
        tail.finishVisit();
        var entryFour = tail.capture();

        // A completed earlier pass leaves the SAME stale words as the pending
        // second entry above; refill one word then begin at entry2, not entry4.
        var head = new S3kPhysicalDisplayQueue();
        head.submit(A, 0);
        head.submit(A, 0);
        head.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var memory = inside(A);
        for (int visit = 0; visit < 2; visit++) {
            head.publishNext(memory, CAMERA);
            head.finishVisit();
        }
        assertNull(head.publishNext(memory, CAMERA));
        head.submit(A, 0);
        head.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        var entryTwo = head.capture();
        assertArrayEquals(entryFour.image(), entryTwo.image());
        assertNotEquals(entryFour, entryTwo, "same image/region/walking/open state, different physical entry cursor");
        head.publishNext(memory, CAMERA);
        var open = head.capture();
        assertArrayEquals(entryTwo.image(), open.image());
        assertNotEquals(entryTwo, open, "publication opens a visit before decrementing the unchanged count");
        head.restore(entryTwo);
        assertEquals(entryTwo, head.capture());
        assertEquals(entryTwo.hashCode(), head.capture().hashCode());
    }

    private static S3kPhysicalDisplayQueue.Header header(int flags, int x, int y, int width, int height) {
        return new S3kPhysicalDisplayQueue.Header(flags, x, y, width, height);
    }

    private static Memory inside(int... addresses) {
        var memory = new Memory();
        for (int address : addresses) {
            memory.put(address, 1, header(4, 0, 0, 8, 0));
        }
        return memory;
    }

    private static int word(byte[] image, int offset) {
        return ((image[offset] & 255) << 8) | (image[offset + 1] & 255);
    }

    private record FlagWrite(int address, int flags) { }

    /** Raw data only; code0 remains a valid known header. Null is explicitly unresolved. */
    private static final class Slot {
        private final int code;
        private S3kPhysicalDisplayQueue.Header header;
        private Slot(int code, S3kPhysicalDisplayQueue.Header header) {
            this.code = code;
            this.header = header;
        }
    }

    private static final class Memory implements S3kPhysicalDisplayQueue.Projection {
        private final Map<Integer, Slot> slots = new HashMap<>();
        private final List<Integer> reads = new ArrayList<>();
        private final List<FlagWrite> writes = new ArrayList<>();

        private void put(int address, int code, S3kPhysicalDisplayQueue.Header header) {
            slots.put(address, new Slot(code, header));
        }

        private int flags(int address) {
            return slots.get(address).header.renderFlags();
        }

        @Override
        public S3kPhysicalDisplayQueue.Header read(int addressWord) {
            reads.add(addressWord);
            Slot slot = slots.get(addressWord);
            return slot == null ? null : slot.header;
        }

        @Override
        public void writeRenderFlags(int addressWord, int renderFlags) {
            Slot slot = slots.get(addressWord);
            var old = slot.header;
            slot.header = header(renderFlags, old.xWord(), old.yWord(), old.widthByte(), old.heightByte());
            writes.add(new FlagWrite(addressWord, renderFlags));
        }
    }
}
