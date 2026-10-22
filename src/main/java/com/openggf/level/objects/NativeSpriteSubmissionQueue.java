package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One simulated object frame's authenticated DisplaySprite calls. Values retain
 * RAM slot addresses, never Java owners or draw-time copies of their attributes.
 * The completed frame remains available for paused/repeated presentation; the
 * next object pass replaces it. Rendering itself does not advance admission.
 */
final class NativeSpriteSubmissionQueue {
    static final int BUCKET_COUNT = 8;
    // DisplaySprite (_incObj/sub DisplaySprite.asm:6-19) compares #$7E.
    // A $80-byte bucket holds its count word plus 63 word-sized SST addresses.
    static final int CAPACITY_PER_BUCKET = (0x80 - Short.BYTES) / Short.BYTES;
    private final int[] slots = new int[BUCKET_COUNT * CAPACITY_PER_BUCKET];
    private final byte[] buckets = new byte[slots.length];
    private final int[] counts = new int[BUCKET_COUNT];
    private final Runnable invalidate;
    private int size;

    record State(List<NativeSpriteSubmissionAccess.Admission> admissions) {
        State { admissions = List.copyOf(admissions); }
    }

    NativeSpriteSubmissionQueue(Runnable invalidate) {
        this.invalidate = Objects.requireNonNull(invalidate);
    }

    boolean submit(int slot, int priorityBucket) {
        return submit(slot, priorityBucket, false);
    }

    boolean submitSpilling(int slot, int priorityBucket) {
        return submit(slot, priorityBucket, true);
    }

    private boolean submit(int slot, int priorityBucket, boolean spill) {
        if (ObjectConstructionContext.isProbeConstruction() || slot < 0) return false;
        // DisplaySprite selects the low three priority-byte bits at call time.
        int bucket = priorityBucket & (BUCKET_COUNT - 1);
        // S3K loc_1ABDC retries successive $80-byte lists, through bucket 7.
        if (spill) {
            while (bucket < BUCKET_COUNT - 1 && counts[bucket] == CAPACITY_PER_BUCKET) bucket++;
        }
        if (counts[bucket] == CAPACITY_PER_BUCKET) return false;
        slots[size] = slot;
        buckets[size++] = (byte) bucket;
        counts[bucket]++;
        invalidate.run();
        return true;
    }

    void beginFrame() {
        size = 0;
        Arrays.fill(counts, 0);
        invalidate.run();
    }

    int size() { return size; }
    int slotAt(int index) { return slots[index]; }
    int bucketAt(int index) { return buckets[index]; }

    List<NativeSpriteSubmissionAccess.Admission> admissions() {
        List<NativeSpriteSubmissionAccess.Admission> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new NativeSpriteSubmissionAccess.Admission(slots[i], buckets[i]));
        }
        return List.copyOf(result);
    }

    State capture() { return new State(admissions()); }

    void restore(State state) {
        Objects.requireNonNull(state);
        int[] restoredCounts = new int[BUCKET_COUNT];
        for (var entry : state.admissions()) {
            if (entry.slot() < 0 || entry.bucket() < 0 || entry.bucket() >= BUCKET_COUNT
                    || ++restoredCounts[entry.bucket()] > CAPACITY_PER_BUCKET) {
                throw new IllegalArgumentException("Invalid native sprite admission snapshot");
            }
        }
        size = 0;
        for (var entry : state.admissions()) {
            slots[size] = entry.slot();
            buckets[size++] = (byte) entry.bucket();
        }
        System.arraycopy(restoredCounts, 0, counts, 0, BUCKET_COUNT);
        invalidate.run();
    }

    RewindSnapshottable<State> rewindAdapter() {
        return new RewindSnapshottable<>() {
            @Override public String key() { return NativeSpriteSubmissionAccess.REWIND_KEY; }
            @Override public State capture() { return NativeSpriteSubmissionQueue.this.capture(); }
            @Override public void restore(State state) { NativeSpriteSubmissionQueue.this.restore(state); }
            @Override public void resetForMissingSnapshot() { beginFrame(); }
        };
    }
}
