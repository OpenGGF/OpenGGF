package com.openggf.game.soniccd.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Only the 96 dynamic slots (whole-pool 32..127). No reserved slots/player or dispatch owner.
 * FindObjSlot/FindNextObjSlot: Level/Object Spawner.asm:329-373;
 * DeleteObject: Object Functions.asm:228-237; ObjNull: Palmtree Panic/Object Index (Act 1 Present).asm:77-79.
 */
public final class CdObjectPool {
    public static final int FIRST_WHOLE_INDEX = 32;
    public static final int END_WHOLE_INDEX = 128;
    public static final int DYNAMIC_COUNT = 96;

    public static final class Snapshot {
        private final Object owner;
        private final List<CdObjectState> slots;
        private Snapshot(Object owner, List<CdObjectState> slots) {
            this.owner = owner;
            this.slots = List.copyOf(slots);
        }
        public List<CdObjectState> slots() { return slots; }
    }

    private final Object owner = new Object();
    private final CdObjectState[] slots = new CdObjectState[DYNAMIC_COUNT];

    /** Explicit fresh zeroed dynamic RAM; finding a free slot never clears it again. */
    public CdObjectPool() { clearDynamicSlots(); }

    public CdObjectState state(int wholeIndex) { return slots[dynamicIndex(wholeIndex)]; }

    /** Commit an immutable image from this same pool, retaining physical identity. */
    public void write(CdObjectState state) {
        validateWrite(state);
        slots[dynamicIndex(state.identity().wholeIndex())] = state;
    }

    /** Pure same-owner preflight used before committing a staged multi-slot response. */
    void validateWrite(CdObjectState state) {
        Objects.requireNonNull(state);
        if (!state.belongsTo(owner)) { throw new IllegalArgumentException("Foreign CD dynamic slot owner"); }
    }

    /** FindObjSlot tests ID only, in physical order, with no writes/reservation/implicit clear. */
    public OptionalInt findObjSlot() { return findFrom(FIRST_WHOLE_INDEX); }

    /**
     * Argument is a WHOLE-pool index, 31..127. The last reserved slot (31) may
     * start a search at the first dynamic slot. Earlier callers could observe
     * reserved slots in native FindNextObjSlot, so this dynamic-only owner rejects them.
     * Searches strictly later slots without wrapping or clearing any slot.
     */
    public OptionalInt findNextObjSlotAfterWholeIndex(int currentWholeIndex) {
        Objects.checkIndex(currentWholeIndex, END_WHOLE_INDEX);
        if (currentWholeIndex < FIRST_WHOLE_INDEX - 1) {
            throw new IllegalArgumentException("CD allocation search would include unowned reserved slots");
        }
        return findFrom(currentWholeIndex + 1);
    }

    public void deleteObject(int wholeIndex) {
        slots[dynamicIndex(wholeIndex)] = CdObjectState.cleared(owner, wholeIndex);
    }

    public void objNull(int wholeIndex) { write(state(wholeIndex).moveByte(CdObjectState.ByteField.ID, 0)); }

    /** Explicit dynamic-RAM clear only. It does not reset the placement cursors or saved bank. */
    public void clearDynamicSlots() {
        for (int whole = FIRST_WHOLE_INDEX; whole < END_WHOLE_INDEX; whole++) { deleteObject(whole); }
    }

    public Snapshot capture() {
        var copy = new ArrayList<CdObjectState>(DYNAMIC_COUNT);
        for (CdObjectState slot : slots) { copy.add(slot); }
        return new Snapshot(owner, copy);
    }

    /** Composite owners can preflight all partitions before any restore mutates state. */
    public void validateRestore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.owner != owner) { throw new IllegalArgumentException("Foreign CD dynamic pool capture"); }
    }

    public void restore(Snapshot snapshot) {
        validateRestore(snapshot);
        for (int i = 0; i < DYNAMIC_COUNT; i++) { slots[i] = snapshot.slots.get(i); }
    }

    private OptionalInt findFrom(int firstWholeIndex) {
        for (int whole = firstWholeIndex; whole < END_WHOLE_INDEX; whole++) {
            if (state(whole).id() == 0) { return OptionalInt.of(whole); }
        }
        return OptionalInt.empty();
    }

    private static int dynamicIndex(int wholeIndex) {
        return Objects.checkIndex(wholeIndex - FIRST_WHOLE_INDEX, DYNAMIC_COUNT);
    }
}
