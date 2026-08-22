package com.openggf.audio.synth;

import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.YmServiceTimingProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestYmWriteTimeline {
    private static final SmpsSourceDescriptor SOURCE =
            new SmpsSourceDescriptor(
                    SmpsSourceDescriptor.Kind.BASE_SFX_ID,
                    0x5A, null, null, 0x1234, 16, 0x5678,
                    false, 0);

    @Test
    void drainsByDueCycleThenSourceOrdinalWithStableSameCycleOrder() {
        YmWriteTimeline timeline = new YmWriteTimeline(4);
        timeline.commit(List.of(
                entry(3_150, 2, 0x42, 1),
                entry(1_008, 0, 0x40, 1),
                entry(3_150, 1, 0x41, 1),
                entry(Long.MAX_VALUE, 3, 0x43, 1)));

        List<YmWriteTimeline.Entry> drained = new ArrayList<>();
        timeline.drainDue(1_007, drained::add);
        assertEquals(List.of(), drained);
        timeline.drainDue(1_008, drained::add);
        assertEquals(List.of(entry(1_008, 0, 0x40, 1)), drained);
        timeline.drainDue(3_150, drained::add);

        assertEquals(List.of(
                entry(1_008, 0, 0x40, 1),
                entry(3_150, 1, 0x41, 1),
                entry(3_150, 2, 0x42, 1)), drained);
        timeline.drainDue(Long.MAX_VALUE, drained::add);
        assertEquals(entry(Long.MAX_VALUE, 3, 0x43, 1), drained.getLast());
    }

    @Test
    void commitValidatesTheWholeJournalBeforeCapacityOrOrdinalFailure() {
        YmWriteTimeline exact = new YmWriteTimeline(2);
        exact.commit(List.of(
                entry(0, 0, 0x40, 1),
                entry(1_008, 1, 0x41, 1)));
        assertEquals(2, exact.captureSnapshot().pending().size());

        YmWriteTimeline tooSmall = new YmWriteTimeline(1);
        assertThrows(IllegalStateException.class, () -> tooSmall.commit(List.of(
                entry(0, 0, 0x40, 1),
                entry(1_008, 1, 0x41, 1))));
        assertEquals(List.of(), tooSmall.captureSnapshot().pending());
        assertEquals(0, tooSmall.captureSnapshot().nextOrdinal());

        YmWriteTimeline duplicate = new YmWriteTimeline(3);
        duplicate.commit(List.of(entry(0, 0, 0x40, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> duplicate.commit(List.of(
                        entry(2_016, 1, 0x41, 1),
                        entry(3_024, 0, 0x42, 1))));
        assertEquals(List.of(entry(0, 0, 0x40, 1)),
                duplicate.captureSnapshot().pending());
        assertEquals(1, duplicate.captureSnapshot().nextOrdinal());
    }

    @Test
    void ordinalAdvanceUsesCheckedLongArithmetic() {
        YmWriteTimeline timeline = new YmWriteTimeline(1);

        assertThrows(ArithmeticException.class,
                () -> timeline.commit(List.of(
                        entry(Long.MAX_VALUE, Long.MAX_VALUE, 0x40, 1))));
        assertEquals(List.of(), timeline.captureSnapshot().pending());
        assertEquals(0, timeline.captureSnapshot().nextOrdinal());
    }

    @Test
    void drainedOrdinalsCannotBeReusedAndTheExactNextOrdinalStillCommits() {
        YmWriteTimeline timeline = new YmWriteTimeline(2);
        timeline.commit(List.of(entry(0, 0, 0x40, 1)));
        timeline.drainDue(0, ignored -> { });
        YmWriteTimeline.Snapshot afterDrain = timeline.captureSnapshot();

        assertThrows(IllegalArgumentException.class,
                () -> timeline.commit(List.of(entry(1_008, 0, 0x41, 1))));
        assertEquals(afterDrain, timeline.captureSnapshot());
        assertThrows(IllegalArgumentException.class,
                () -> timeline.commit(List.of(entry(1_008, 2, 0x41, 1))));
        assertEquals(afterDrain, timeline.captureSnapshot());

        timeline.commit(List.of(entry(1_008, 1, 0x41, 1)));
        assertEquals(2, timeline.captureSnapshot().nextOrdinal());
        assertEquals(List.of(entry(1_008, 1, 0x41, 1)),
                timeline.captureSnapshot().pending());
    }

    @Test
    void discardedOrdinalsCannotBeReusedAndFailureIsAtomic() {
        YmWriteTimeline timeline = new YmWriteTimeline(3);
        timeline.commit(List.of(
                entry(0, 0, 0x40, 1),
                entry(1_008, 1, 0x41, 2)));
        timeline.discardBeforeGeneration(2);
        YmWriteTimeline.Snapshot afterDiscard = timeline.captureSnapshot();

        assertThrows(IllegalArgumentException.class,
                () -> timeline.commit(List.of(
                        entry(2_016, 2, 0x42, 2),
                        entry(3_024, 0, 0x43, 2))));
        assertEquals(afterDiscard, timeline.captureSnapshot());

        timeline.commit(List.of(entry(2_016, 2, 0x42, 2)));
        assertEquals(3, timeline.captureSnapshot().nextOrdinal());
        assertEquals(List.of(
                entry(1_008, 1, 0x41, 2),
                entry(2_016, 2, 0x42, 2)),
                timeline.captureSnapshot().pending());
    }

    @Test
    void committedEntriesAreIndependentOfTheJournalAndGenerationDiscardIsSilent() {
        YmWriteTimeline timeline = new YmWriteTimeline(3);
        ArrayList<YmWriteTimeline.Entry> journal = new ArrayList<>(List.of(
                entry(0, 0, 0x40, 1),
                entry(1_008, 1, 0x41, 2),
                entry(2_016, 2, 0x42, 3)));
        timeline.commit(journal);
        journal.clear();
        timeline.discardBeforeGeneration(3);

        List<YmWriteTimeline.Entry> drained = new ArrayList<>();
        timeline.drainDue(Long.MAX_VALUE, drained::add);

        assertEquals(List.of(entry(2_016, 2, 0x42, 3)), drained);
    }

    @Test
    void snapshotAndRestoreUseDefensivePendingCopies() {
        YmWriteTimeline timeline = new YmWriteTimeline(3);
        timeline.commit(List.of(
                entry(2_016, 0, 0x40, 1),
                entry(3_024, 1, 0x41, 1)));

        YmWriteTimeline.Snapshot captured = timeline.captureSnapshot();
        assertThrows(UnsupportedOperationException.class,
                () -> captured.pending().clear());

        ArrayList<YmWriteTimeline.Entry> callerOwned = new ArrayList<>(
                captured.pending());
        YmWriteTimeline.Snapshot supplied = new YmWriteTimeline.Snapshot(
                3, 2, callerOwned);
        callerOwned.clear();

        YmWriteTimeline restored = new YmWriteTimeline(3);
        restored.restoreSnapshot(supplied);
        assertEquals(captured, restored.captureSnapshot());
        restored.commit(List.of(entry(4_032, 2, 0x42, 1)));
        assertEquals(3, restored.captureSnapshot().nextOrdinal());

        YmWriteTimeline watermarked = new YmWriteTimeline(3);
        watermarked.restoreSnapshot(new YmWriteTimeline.Snapshot(
                3, 2, List.of(entry(2_016, 0, 0x40, 1))));
        YmWriteTimeline.Snapshot restoredWatermark =
                watermarked.captureSnapshot();
        assertThrows(IllegalArgumentException.class,
                () -> watermarked.commit(List.of(
                        entry(4_032, 1, 0x42, 1))));
        assertEquals(restoredWatermark, watermarked.captureSnapshot());

        assertThrows(IllegalArgumentException.class,
                () -> restored.restoreSnapshot(new YmWriteTimeline.Snapshot(
                        3, 1, List.of(entry(0, 1, 0x40, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> new YmWriteTimeline(2).restoreSnapshot(captured));
    }

    @Test
    void nullSegmentIsTheImmutableUnprofiledOrderedFenceMarker() {
        YmWriteTimeline.Entry fence = new YmWriteTimeline.Entry(
                1_008, 0, 1, 0x1A0, 0xFF,
                3, 7, SOURCE, null);
        YmWriteTimeline timeline = new YmWriteTimeline(1);

        assertNull(fence.segment());
        assertEquals(SOURCE, fence.sourceDescriptor());
        assertThrows(NullPointerException.class,
                () -> new YmWriteTimeline.Entry(
                        1_008, 0, 1, 0x1A0, 0xFF,
                        3, 7, null, null));

        timeline.commit(List.of(fence));
        YmWriteTimeline.Snapshot snapshot = timeline.captureSnapshot();
        assertEquals(List.of(fence), snapshot.pending());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.pending().set(0,
                        entry(2_016, 1, 0x40, 3)));
        assertEquals(List.of(fence), timeline.captureSnapshot().pending());
    }

    private static YmWriteTimeline.Entry entry(
            long dueMasterCycle, long sourceOrdinal, int register,
            long generation) {
        return new YmWriteTimeline.Entry(
                dueMasterCycle, sourceOrdinal, 0, register, 0x7F,
                generation, 4, SOURCE,
                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD);
    }
}
