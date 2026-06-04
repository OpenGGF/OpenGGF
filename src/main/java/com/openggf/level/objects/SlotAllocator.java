package com.openggf.level.objects;

import java.util.BitSet;

/**
 * The single authority for dynamic SST slot occupancy + allocation for one game's
 * {@link ObjectSlotLayout}. Mirrors ROM FindFreeObj / FindNextFreeObj (linear
 * first-empty scan) and DeleteObject/Delete_Current_Sprite (identity cleared ⇒
 * slot recyclable). Every allocation path — windowing load, child spawn, dynamic
 * object, boss sub-part — MUST go through this; there is no second allocator.
 */
public final class SlotAllocator {
    private final ObjectSlotLayout layout;
    private final SlotEmptyPredicate emptyPredicate;
    private final BitSet used;
    private int peak;

    public SlotAllocator(ObjectSlotLayout layout, SlotEmptyPredicate emptyPredicate) {
        this.layout = layout;
        this.emptyPredicate = emptyPredicate;
        this.used = new BitSet(layout.dynamicSlotCount());
    }

    public SlotEmptyPredicate emptyPredicate() { return emptyPredicate; }
    public int peakSlotCount() { return peak; }

    /** ROM FindFreeObj: first empty slot from the start of the dynamic range. -1 if pool full. */
    public int allocate() {
        return allocateFrom(0);
    }

    /** ROM FindNextFreeObj: first empty slot strictly after {@code parentSlot}. -1 if none. */
    public int allocateAfter(int parentSlot) {
        int startExec = Math.max(0, layout.toExecIndex(parentSlot) + 1);
        return allocateFrom(startExec);
    }

    private int allocateFrom(int startExec) {
        int bit = used.nextClearBit(startExec);
        if (bit >= layout.dynamicSlotCount()) {
            return -1;
        }
        used.set(bit);
        peak = Math.max(peak, used.cardinality());
        return layout.toSlotIndex(bit);
    }

    /** Re-reserve a specific dynamic slot (rewind restore of subsystem-owned occupants). */
    public boolean reserve(int slotIndex) {
        if (!layout.isDynamicSlot(slotIndex)) {
            return false;
        }
        int exec = layout.toExecIndex(slotIndex);
        if (used.get(exec)) {
            return false;
        }
        used.set(exec);
        peak = Math.max(peak, used.cardinality());
        return true;
    }

    public void release(int slotIndex) {
        if (layout.isDynamicSlot(slotIndex)) {
            used.clear(layout.toExecIndex(slotIndex));
        }
    }

    public boolean isEmpty(int slotIndex) {
        return !layout.isDynamicSlot(slotIndex) || !used.get(layout.toExecIndex(slotIndex));
    }

    public void clear() {
        used.clear();
        peak = 0;
    }

    // --- Occupancy introspection + rewind/restore (parity with the prior raw `usedSlots` usage) ---

    /** Number of occupied dynamic slots (was {@code usedSlots.cardinality()}). */
    public int activeCount() { return used.cardinality(); }

    /** Snapshot occupancy for rewind (was {@code usedSlots.toLongArray()}). */
    public long[] toLongArray() { return used.toLongArray(); }

    /** Restore occupancy from a rewind snapshot (replaces the BitSet contents wholesale). */
    public void restoreFromLongArray(long[] bits) {
        used.clear();
        used.or(java.util.BitSet.valueOf(bits));
        peak = Math.max(peak, used.cardinality());
    }

    /**
     * Force-mark a dynamic slot occupied regardless of current state — for rewind
     * restore and pre-assigned/reserved slots where {@link #reserve(int)}'s
     * "fail if already taken" semantics are not wanted. No-op for non-dynamic slots.
     */
    public void reserveOrMarkUsed(int slotIndex) {
        if (layout.isDynamicSlot(slotIndex)) {
            used.set(layout.toExecIndex(slotIndex));
            peak = Math.max(peak, used.cardinality());
        }
    }
}
