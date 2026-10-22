package com.openggf.level.objects;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.level.objects.NativeSpriteSubmissionAccess.Admission;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-free checks of S1's DisplaySprite admission contract, independent of rendering.
 *
 * <p>At pinned s1disasm 28872a46, DisplaySprite
 * (_incObj/sub DisplaySprite.asm:6-19) reserves a count word and appends word-sized
 * SST addresses to each $80-byte bucket. Its #$7E comparison admits exactly
 * ($80 - 2) / 2 = 63 calls, including duplicate addresses. BuildSprites
 * (_inc/BuildSprites.asm:29-38,118-125) later walks each bucket in admission order
 * and resolves the current slot contents; admission itself does not resolve an
 * object or save any of its attributes. The diagnostic view below preserves
 * global call chronology; the rendering consumer owns the bucket traversal.
 */
class TestNativeSpriteSubmissionQueue {
    @Test
    void drawSpriteSpillsToFirstAvailableLaterBucketAndKeepsItsActualAdmissionBucket() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        for (int call = 0; call < 63; call++) {
            assertTrue(queue.submit(32, 3));
            assertTrue(queue.submit(40, 4));
        }
        assertTrue(queue.submitSpilling(70, 3));
        assertTrue(queue.submitSpilling(40, 3));
        assertTrue(queue.submit(32, 5));
        assertEquals(List.of(70, 40, 32), slotsInBucket(queue, 5));
        assertFalse(queue.submit(70, 3), "S1/S2 DisplaySprite does not borrow S3K spill semantics");
        var state = queue.capture();
        queue.beginFrame();
        queue.restore(state);
        assertEquals(List.of(new Admission(70, 5), new Admission(40, 5), new Admission(32, 5)),
                queue.admissions().subList(126, 129));
    }

    @Test
    void drawSpriteNeverWrapsFullBucketSevenToEarlierFreeBuckets() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        for (int bucket = 6; bucket <= 7; bucket++) {
            for (int call = 0; call < 63; call++) assertTrue(queue.submit(40, bucket));
        }
        assertFalse(queue.submitSpilling(32, 6));
        assertFalse(queue.submitSpilling(32, 7));
        assertTrue(queue.submitSpilling(32, 5), "Earlier empty lists are usable only when selected");
        assertEquals(127, queue.size());
    }

    @Test
    void eachNativeBucketHoldsExactly63WordAddressAdmissions() {
        AtomicInteger invalidations = new AtomicInteger();
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(invalidations::incrementAndGet);

        for (int slot = 0; slot < 63; slot++) {
            assertTrue(queue.submit(slot, 4), "Native admission " + (slot + 1) + " must fit");
        }

        assertFalse(queue.submit(127, 4), "The 64th pointer would overwrite the next $80-byte bucket");
        assertEquals(IntStream.range(0, 63).mapToObj(slot -> new Admission(slot, 4)).toList(),
                queue.admissions());
        assertEquals(63, invalidations.get(), "Rejected calls must not invalidate presentation");
    }

    @Test
    void repeatedAndNonmonotonicSlotsKeepEveryCallInFifoOrder() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        List<Admission> expected = List.of(
                new Admission(90, 3), new Admission(32, 3), new Admission(90, 3),
                new Admission(0, 3), new Admission(127, 3), new Admission(16, 3));

        for (Admission admission : expected) {
            assertTrue(queue.submit(admission.slot(), admission.bucket()));
        }

        assertEquals(expected, queue.admissions(),
                "DisplaySprite appends calls, without sorting SST addresses or deduplicating them");
    }

    @Test
    void eachCallCapturesItsOwnBucketEvenWhenItReferencesTheSameSlot() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        assertTrue(queue.submit(40, 7));
        assertTrue(queue.submit(32, 2));
        assertTrue(queue.submit(40, 2));
        assertTrue(queue.submit(16, 7));
        assertTrue(queue.submit(40, 7));

        assertEquals(List.of(new Admission(40, 7), new Admission(32, 2),
                new Admission(40, 2), new Admission(16, 7), new Admission(40, 7)), queue.admissions());
        assertEquals(List.of(40, 16, 40), slotsInBucket(queue, 7),
                "Grouping for BuildSprites must preserve FIFO within each bucket");
        assertEquals(List.of(32, 40), slotsInBucket(queue, 2));
    }

    @Test
    void bucketSelectionUsesTheLowThreePriorityByteBits() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        assertTrue(queue.submit(32, 8));
        assertTrue(queue.submit(33, 0xFF));
        assertTrue(queue.submit(34, 0xFE));

        assertEquals(List.of(new Admission(32, 0), new Admission(33, 7), new Admission(34, 6)),
                queue.admissions(), "DisplaySprite's shift and $380 mask select priority & 7");
    }

    @Test
    void allEightBucketsIndependentlyAdmit63CallsIncludingDuplicates() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        List<Admission> expected = new ArrayList<>();
        for (int bucket = 0; bucket < 8; bucket++) {
            for (int call = 0; call < 63; call++) {
                assertTrue(queue.submit(40, bucket), "Bucket " + bucket + " call " + (call + 1));
                expected.add(new Admission(40, bucket));
            }
            assertFalse(queue.submit(41, bucket), "Only the filled bucket must reject its 64th call");
        }

        assertEquals(504, queue.admissions().size(), "Eight native buckets each hold 63 address words");
        assertEquals(expected, queue.admissions());
    }

    @Test
    void rejectionPreservesTheAcceptedLedgerAndDoesNotInvalidate() {
        AtomicInteger invalidations = new AtomicInteger();
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(invalidations::incrementAndGet);
        for (int call = 0; call < 63; call++) assertTrue(queue.submit(32, 1));
        assertTrue(queue.submit(64, 6));
        List<Admission> before = queue.admissions();
        int beforeInvalidations = invalidations.get();

        assertFalse(queue.submit(127, 9), "Priority 9 aliases the already full bucket 1");

        assertEquals(before, queue.admissions());
        assertEquals(beforeInvalidations, invalidations.get());
        assertTrue(queue.submit(65, 6), "A rejected call must not consume another bucket's capacity");
        assertEquals(List.of(64, 65), slotsInBucket(queue, 6));
    }

    @Test
    void beginningAFrameClearsEveryBucketAndRestoresCapacity() {
        AtomicInteger invalidations = new AtomicInteger();
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(invalidations::incrementAndGet);
        for (int call = 0; call < 63; call++) assertTrue(queue.submit(32, 0));
        assertTrue(queue.submit(127, 7));
        int beforeClear = invalidations.get();

        queue.beginFrame();

        assertEquals(List.of(), queue.admissions());
        assertTrue(invalidations.get() > beforeClear, "Discarding the prior frame changes presentation");
        for (int call = 0; call < 63; call++) assertTrue(queue.submit(64, 0));
        assertFalse(queue.submit(65, 0));
        assertTrue(queue.submit(127, 7), "Other buckets must also start empty");
    }

    @Test
    void admissionViewsAreImmutableAndDetachedFromFutureFrames() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        assertTrue(queue.submit(32, 0));
        List<Admission> view = queue.admissions();

        assertThrows(UnsupportedOperationException.class, () -> view.add(new Admission(33, 0)));
        assertThrows(UnsupportedOperationException.class, () -> view.set(0, new Admission(34, 0)));
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertTrue(queue.submit(40, 1));
        queue.beginFrame();

        assertEquals(List.of(new Admission(32, 0)), view,
                "A saved read view must not change when later calls or frames change the queue");
        assertEquals(List.of(), queue.admissions());
    }

    @Test
    void snapshotDefensivelyCopiesItsInputAndExposesAnImmutableList() {
        List<Admission> input = new ArrayList<>(List.of(new Admission(127, 7), new Admission(16, 0)));
        NativeSpriteSubmissionQueue.State state = new NativeSpriteSubmissionQueue.State(input);

        input.set(0, new Admission(32, 2));
        input.clear();

        assertEquals(List.of(new Admission(127, 7), new Admission(16, 0)), state.admissions());
        assertThrows(UnsupportedOperationException.class, () -> state.admissions().clear());
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        queue.restore(state);
        assertEquals(List.of(new Admission(127, 7), new Admission(16, 0)), queue.admissions());
    }

    @Test
    void captureAndRestoreRetainRawSlotsDuplicatesAndCallTimeBuckets() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        List<Admission> expected = List.of(new Admission(127, 7), new Admission(16, 0),
                new Admission(127, 2), new Admission(127, 7));
        for (Admission admission : expected) assertTrue(queue.submit(admission.slot(), admission.bucket()));
        NativeSpriteSubmissionQueue.State captured = queue.capture();

        queue.beginFrame();
        assertTrue(queue.submit(40, 4));
        assertEquals(expected, captured.admissions());
        assertThrows(UnsupportedOperationException.class, () -> captured.admissions().clear());
        queue.restore(captured);

        assertEquals(expected, queue.admissions(),
                "The queue needs no live Java objects to retain or restore native SST references");
        assertTrue(queue.submit(90, 7));
        assertEquals(expected, captured.admissions(), "Restoring must not make the snapshot mutable");
        assertEquals(List.of(127, 127, 90), slotsInBucket(queue, 7));
    }

    @Test
    void restoringAFullBucketRestoresItsAdmissionLimitAndReplacesFutureCalls() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        for (int call = 0; call < 63; call++) assertTrue(queue.submit(32, 1));
        NativeSpriteSubmissionQueue.State captured = queue.capture();
        queue.beginFrame();
        assertTrue(queue.submit(127, 6));

        queue.restore(captured);

        assertFalse(queue.submit(40, 1), "Restored addresses still occupy all 63 native entries");
        assertEquals(List.of(), slotsInBucket(queue, 6), "Future-frame calls must be discarded");
        for (int call = 0; call < 63; call++) assertTrue(queue.submit(64, 6));
        assertFalse(queue.submit(65, 6));
    }

    @Test
    void rewindAdapterRestoresAdmissionsAndResetsWhenItsSnapshotIsMissing() {
        NativeSpriteSubmissionQueue queue = new NativeSpriteSubmissionQueue(() -> { });
        RewindRegistry registry = new RewindRegistry();
        var withoutQueue = registry.capture();
        var adapter = queue.rewindAdapter();
        assertEquals("object-native-sprite-submissions", adapter.key());
        registry.register(adapter);
        assertTrue(queue.submit(127, 7));
        assertTrue(queue.submit(16, 0));
        var withQueue = registry.capture();
        queue.beginFrame();
        assertTrue(queue.submit(40, 4));

        registry.restore(withQueue);
        assertEquals(List.of(new Admission(127, 7), new Admission(16, 0)), queue.admissions());

        registry.restore(withoutQueue);
        assertEquals(List.of(), queue.admissions(), "A missing older queue snapshot must not retain future calls");
        for (int call = 0; call < 63; call++) assertTrue(queue.submit(32, 7));
        assertFalse(queue.submit(33, 7), "Missing-snapshot reset must also reset per-bucket admission counts");
    }

    private static List<Integer> slotsInBucket(NativeSpriteSubmissionQueue queue, int bucket) {
        return queue.admissions().stream().filter(admission -> admission.bucket() == bucket)
                .map(Admission::slot).toList();
    }
}
