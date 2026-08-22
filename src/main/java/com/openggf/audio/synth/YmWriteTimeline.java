package com.openggf.audio.synth;

import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.YmServiceTimingProfile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fixed-capacity queue of source-ordered YM writes keyed by master cycle.
 * Publication may allocate while validating a journal; draining never does.
 */
public final class YmWriteTimeline {
    public static final long MASTER_CYCLES_PER_INTERNAL_SAMPLE = 1008L;

    private static final Comparator<Entry> DRAIN_ORDER = (left, right) -> {
        int dueOrder = Long.compare(
                left.dueMasterCycle(), right.dueMasterCycle());
        return dueOrder != 0 ? dueOrder : Long.compare(
                left.sourceOrdinal(), right.sourceOrdinal());
    };

    private final Entry[] pending;
    private int size;
    private long nextOrdinal;

    public YmWriteTimeline(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException(
                    "timeline capacity cannot be negative");
        }
        pending = new Entry[capacity];
    }

    /**
     * One immutable write publication.
     *
     * @param segment audited source-timing segment, or {@code null} only for
     *                an unprofiled write retained as an ordered fence
     */
    public record Entry(
            long dueMasterCycle,
            long sourceOrdinal,
            int port,
            int register,
            int value,
            long driverGeneration,
            long serviceOrdinal,
            SmpsSourceDescriptor sourceDescriptor,
            YmServiceTimingProfile.SegmentKind segment) {
        public Entry {
            if (dueMasterCycle < 0) {
                throw new IllegalArgumentException(
                        "due master cycle cannot be negative");
            }
            if (sourceOrdinal < 0) {
                throw new IllegalArgumentException(
                        "source ordinal cannot be negative");
            }
            if (port < 0 || port > 1) {
                throw new IllegalArgumentException("YM port must be 0 or 1");
            }
            if (register < 0 || register > 0x1FF) {
                throw new IllegalArgumentException(
                        "YM register must fit the address space");
            }
            if (value < 0 || value > 0xFF) {
                throw new IllegalArgumentException(
                        "YM value must fit one byte");
            }
            if (driverGeneration < 0) {
                throw new IllegalArgumentException(
                        "driver generation cannot be negative");
            }
            if (serviceOrdinal < 0) {
                throw new IllegalArgumentException(
                        "service ordinal cannot be negative");
            }
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
        }
    }

    /**
     * Immutable queue state. {@code nextOrdinal} is the first unused source
     * identity, so every pending entry must have a smaller ordinal.
     */
    public record Snapshot(
            int capacity,
            long nextOrdinal,
            List<Entry> pending) {
        public Snapshot {
            if (capacity < 0) {
                throw new IllegalArgumentException(
                        "timeline capacity cannot be negative");
            }
            if (nextOrdinal < 0) {
                throw new IllegalArgumentException(
                        "next ordinal cannot be negative");
            }
            pending = List.copyOf(Objects.requireNonNull(
                    pending, "pending"));
            if (pending.size() > capacity) {
                throw new IllegalArgumentException(
                        "pending writes exceed timeline capacity");
            }
        }
    }

    /**
     * Atomically validates and publishes a complete write journal. Its unique
     * source ordinals must exactly fill the contiguous range beginning at the
     * current {@link Snapshot#nextOrdinal()} watermark; list order is ignored.
     */
    public void commit(List<Entry> journal) {
        Objects.requireNonNull(journal, "journal");
        int committedSize = Math.addExact(size, journal.size());
        if (committedSize > pending.length) {
            throw new IllegalStateException("YM write timeline capacity exceeded");
        }

        Entry[] incoming = journal.toArray(Entry[]::new);
        long committedNextOrdinal = Math.addExact(
                nextOrdinal, (long) incoming.length);
        for (int i = 0; i < incoming.length; i++) {
            Entry entry = Objects.requireNonNull(incoming[i], "journal entry");
            long ordinalAfterEntry = Math.addExact(
                    entry.sourceOrdinal(), 1);
            if (entry.sourceOrdinal() < nextOrdinal
                    || ordinalAfterEntry > committedNextOrdinal) {
                throw new IllegalArgumentException(
                        "YM source ordinals must be the contiguous range ["
                                + nextOrdinal + ", "
                                + committedNextOrdinal + ")");
            }
            requireUniqueJournalOrdinal(
                    entry.sourceOrdinal(), incoming, i);
        }

        Arrays.sort(incoming, DRAIN_ORDER);
        Entry[] merged = new Entry[committedSize];
        int oldIndex = 0;
        int incomingIndex = 0;
        int mergedIndex = 0;
        while (oldIndex < size && incomingIndex < incoming.length) {
            Entry oldEntry = pending[oldIndex];
            Entry incomingEntry = incoming[incomingIndex];
            if (DRAIN_ORDER.compare(oldEntry, incomingEntry) <= 0) {
                merged[mergedIndex++] = oldEntry;
                oldIndex++;
            } else {
                merged[mergedIndex++] = incomingEntry;
                incomingIndex++;
            }
        }
        while (oldIndex < size) {
            merged[mergedIndex++] = pending[oldIndex++];
        }
        while (incomingIndex < incoming.length) {
            merged[mergedIndex++] = incoming[incomingIndex++];
        }

        System.arraycopy(merged, 0, pending, 0, committedSize);
        size = committedSize;
        nextOrdinal = committedNextOrdinal;
    }

    private static void requireUniqueJournalOrdinal(
            long ordinal, Entry[] incoming, int incomingLimit) {
        for (int i = 0; i < incomingLimit; i++) {
            if (incoming[i].sourceOrdinal() == ordinal) {
                throw new IllegalArgumentException(
                        "duplicate YM source ordinal " + ordinal);
            }
        }
    }

    /** Drains all entries visible at the already-rendered master-cycle frontier. */
    public void drainDue(
            long renderedMasterCycle, Consumer<Entry> mutation) {
        if (renderedMasterCycle < 0) {
            throw new IllegalArgumentException(
                    "rendered master cycle cannot be negative");
        }
        Objects.requireNonNull(mutation, "mutation");
        while (size > 0
                && pending[0].dueMasterCycle() <= renderedMasterCycle) {
            Entry entry = pending[0];
            int retained = size - 1;
            if (retained > 0) {
                System.arraycopy(pending, 1, pending, 0, retained);
            }
            pending[retained] = null;
            size = retained;
            mutation.accept(entry);
        }
    }

    public void discardBeforeGeneration(long generation) {
        if (generation < 0) {
            throw new IllegalArgumentException(
                    "driver generation cannot be negative");
        }
        int retained = 0;
        for (int i = 0; i < size; i++) {
            Entry entry = pending[i];
            if (entry.driverGeneration() >= generation) {
                pending[retained++] = entry;
            }
        }
        Arrays.fill(pending, retained, size, null);
        size = retained;
    }

    public Snapshot captureSnapshot() {
        Entry[] copy = Arrays.copyOf(pending, size);
        return new Snapshot(pending.length, nextOrdinal, List.of(copy));
    }

    public void restoreSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.capacity() != pending.length) {
            throw new IllegalArgumentException(
                    "snapshot capacity does not match timeline capacity");
        }

        Entry[] restored = snapshot.pending().toArray(Entry[]::new);
        long highestNextOrdinal = 0;
        for (int i = 0; i < restored.length; i++) {
            Entry entry = Objects.requireNonNull(
                    restored[i], "snapshot pending entry");
            highestNextOrdinal = Math.max(highestNextOrdinal,
                    Math.addExact(entry.sourceOrdinal(), 1));
            for (int prior = 0; prior < i; prior++) {
                if (restored[prior].sourceOrdinal()
                        == entry.sourceOrdinal()) {
                    throw new IllegalArgumentException(
                            "duplicate YM source ordinal "
                                    + entry.sourceOrdinal());
                }
            }
        }
        if (snapshot.nextOrdinal() < highestNextOrdinal) {
            throw new IllegalArgumentException(
                    "snapshot next ordinal precedes a pending write");
        }
        Arrays.sort(restored, DRAIN_ORDER);

        Arrays.fill(pending, null);
        System.arraycopy(restored, 0, pending, 0, restored.length);
        size = restored.length;
        nextOrdinal = snapshot.nextOrdinal();
    }
}
