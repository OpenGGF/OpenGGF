package com.openggf.game.sonic2.specialstage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The special stage's own object RAM, independent of the ordinary level object manager.
 *
 * <p>{@code SSAllocateObject} scans the 120 dynamic $40-byte records from $B200
 * upwards; {@code SSAllocateObjectAfterCurrent} starts at the executing record
 * and consequently finds a free record above it. Allocation order is observable
 * both during RunObjects and within a DisplaySprite priority bucket
 * ({@code SSAllocateObject}/{@code SSAllocateObjectAfterCurrent}, s2.asm:9450-9483).
 * Entries contain values and generation-checked handles only: restoring a stage
 * never retains a Java reference to an owner from the discarded timeline.</p>
 */
public final class Sonic2SpecialStageSlotPool {
    public static final int SLOT_COUNT = 128;
    public static final int FIRST_DYNAMIC_SLOT = 8;
    public static final int BASE_ADDRESS = 0xB000;
    public static final int SLOT_SIZE = 0x40;

    public enum Kind {
        SONIC, TAILS, HUD, BANNER, PLAYER_COUNTER, TOTAL_COUNTER, VS_SCORE,
        SONIC_SHADOW, TAILS_SHADOW, TAILS_TAIL,
        RINGS_TO_GO_NUMBER, RINGS_TO_GO_LETTER, BANNER_PIECE,
        MESSAGE_LETTER, MESSAGE_DIGIT, CHECKPOINT_MARKER, RAINBOW, WING, HAND,
        RING, BOMB, EMERALD, OBJECT_SHADOW, SPILLED_RING
    }

    public enum ArtRole {
        SONIC, TAILS, TAILS_TAIL, HUD, START_BANNER, MESSAGES, NUMBER,
        RING, BOMB, EMERALD, SHADOW, STARS, EXPLOSION
    }

    /** Values produced by an existing native owner, ready for SAT bucket/slot ordering. */
    public record RenderState(Entry identity, int x, int y, int mappingFrame,
                              boolean xFlip, boolean yFlip, boolean visible, ArtRole artRole) {
        public RenderState {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(artRole);
        }
    }

    public record Handle(int slot, long generation) {
        public Handle {
            checkSlot(slot);
            if (generation <= 0) {
                throw new IllegalArgumentException("Slot generation must be positive");
            }
        }

        public int address() {
            return BASE_ADDRESS + slot * SLOT_SIZE;
        }
    }

    /** Immutable identity/topology for an owner, not a draw-time ordering number. */
    public record Entry(Handle handle, Kind kind, int objectId, int priorityBucket,
                        int component, Handle parent, int routine, boolean deletionPending) {
        public Entry {
            Objects.requireNonNull(handle);
            Objects.requireNonNull(kind);
            if (objectId <= 0 || objectId > 0xFF) {
                throw new IllegalArgumentException("A live native object needs a nonzero byte ID");
            }
            checkPriority(priorityBucket);
        }

        public int slot() {
            return handle.slot();
        }

        public int address() {
            return handle.address();
        }
    }

    public record Snapshot(List<Entry> entries, long[] generations) {
        public Snapshot {
            entries = List.copyOf(entries);
            generations = generations.clone();
            if (generations.length != SLOT_COUNT) {
                throw new IllegalArgumentException("A special-stage pool has 128 generation counters");
            }
            boolean[] seen = new boolean[SLOT_COUNT];
            for (Entry entry : entries) {
                int slot = entry.slot();
                if (seen[slot] || generations[slot] != entry.handle().generation()) {
                    throw new IllegalArgumentException("Inconsistent special-stage slot snapshot");
                }
                seen[slot] = true;
            }
        }

        @Override
        public long[] generations() {
            return generations.clone();
        }
    }

    private final Entry[] slots = new Entry[SLOT_COUNT];
    private final long[] generations = new long[SLOT_COUNT];

    public Handle claimFixed(int slot, Kind kind, int objectId, int priorityBucket,
                             int component, Handle parent) {
        if (slot < 0 || slot >= FIRST_DYNAMIC_SLOT) {
            throw new IllegalArgumentException("Fixed special-stage slot must be in 0..7");
        }
        return claim(slot, kind, objectId, priorityBucket, component, parent);
    }

    public Handle allocate(Kind kind, int objectId, int priorityBucket, int component, Handle parent) {
        return allocateFrom(FIRST_DYNAMIC_SLOT, kind, objectId, priorityBucket, component, parent);
    }

    public Handle allocateAfterCurrent(Handle current, Kind kind, int objectId,
                                       int priorityBucket, int component, Handle parent) {
        if (!isLive(current)) {
            return null;
        }
        return allocateFrom(Math.max(FIRST_DYNAMIC_SLOT, current.slot() + 1),
                kind, objectId, priorityBucket, component, parent);
    }

    private Handle allocateFrom(int firstSlot, Kind kind, int objectId,
                                int priorityBucket, int component, Handle parent) {
        for (int slot = firstSlot; slot < SLOT_COUNT; slot++) {
            if (slots[slot] == null) {
                return claim(slot, kind, objectId, priorityBucket, component, parent);
            }
        }
        return null;
    }

    private Handle claim(int slot, Kind kind, int objectId, int priorityBucket,
                         int component, Handle parent) {
        if (slots[slot] != null) {
            return null;
        }
        long generation = Math.incrementExact(generations[slot]);
        Handle handle = new Handle(slot, generation);
        Entry entry = new Entry(handle, kind, objectId, priorityBucket, component, parent, 0, false);
        generations[slot] = generation;
        slots[slot] = entry;
        return handle;
    }

    public boolean release(Handle handle) {
        if (!isLive(handle)) {
            return false;
        }
        slots[handle.slot()] = null;
        return true;
    }

    public boolean isLive(Handle handle) {
        return handle != null && slots[handle.slot()] != null
                && slots[handle.slot()].handle().equals(handle);
    }

    public Entry entry(Handle handle) {
        return isLive(handle) ? slots[handle.slot()] : null;
    }

    public Entry entryAt(int slot) {
        checkSlot(slot);
        return slots[slot];
    }

    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : slots) {
            if (entry != null) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    public int size() {
        int count = 0;
        for (Entry entry : slots) {
            if (entry != null) {
                count++;
            }
        }
        return count;
    }

    public boolean hasFreeDynamicSlot() {
        for (int slot = FIRST_DYNAMIC_SLOT; slot < SLOT_COUNT; slot++) {
            if (slots[slot] == null) {
                return true;
            }
        }
        return false;
    }

    public void setPriority(Handle handle, int priorityBucket) {
        checkPriority(priorityBucket);
        Entry entry = entry(handle);
        if (entry != null) {
            slots[handle.slot()] = new Entry(handle, entry.kind(), entry.objectId(), priorityBucket,
                    entry.component(), entry.parent(), entry.routine(), entry.deletionPending());
        }
    }

    public void setRoutine(Handle handle, int routine) {
        Entry entry = entry(handle);
        if (entry != null) {
            slots[handle.slot()] = new Entry(handle, entry.kind(), entry.objectId(), entry.priorityBucket(),
                    entry.component(), entry.parent(), routine, entry.deletionPending());
        }
    }

    /** Models the child's objoff_2A deletion flag; it still occupies RAM until its own turn. */
    public void markForDeletion(Handle handle) {
        Entry entry = entry(handle);
        if (entry != null) {
            slots[handle.slot()] = new Entry(handle, entry.kind(), entry.objectId(), entry.priorityBucket(),
                    entry.component(), entry.parent(), entry.routine(), true);
        }
    }

    public boolean isPendingDeletion(Handle handle) {
        Entry entry = entry(handle);
        return entry != null && entry.deletionPending();
    }

    public Snapshot snapshot() {
        return new Snapshot(entries(), generations);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        Arrays.fill(slots, null);
        System.arraycopy(snapshot.generations(), 0, generations, 0, SLOT_COUNT);
        for (Entry entry : snapshot.entries()) {
            slots[entry.slot()] = entry;
        }
    }

    public void clear() {
        // Retain counters so references held outside this pool cannot revive after a reset.
        Arrays.fill(slots, null);
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Special-stage slot must be in 0..127");
        }
    }

    private static void checkPriority(int priorityBucket) {
        if (priorityBucket < 0 || priorityBucket > 7) {
            throw new IllegalArgumentException("DisplaySprite priority bucket must be in 0..7");
        }
    }
}
