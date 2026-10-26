package com.openggf.game.sonic2.competition.specialstage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Native 128-SST object RAM; slot addresses are {@code $B000 + slot * $40}. */
public final class CompetitiveHalfpipeObjectRam<T> {
    public static final int SLOT_COUNT = 128;
    public static final int FIRST_DYNAMIC_SLOT = 8;
    public static final int LAST_DYNAMIC_SLOT = 126;
    public static final int GUARD_SLOT = 127;

    /** Null state is an explicitly empty physical slot, not an omitted entry. */
    public record Slot<S>(int index, S state) {
        public Slot { Objects.checkIndex(index, SLOT_COUNT); }
    }

    /** The codec owns deep immutability; this container retains only its encoded states. */
    public record State<S>(List<Slot<S>> slots) {
        public State {
            slots = List.copyOf(slots);
            if (slots.size() != SLOT_COUNT) throw new IllegalArgumentException("Native object RAM needs 128 physical slots");
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (slots.get(i).index() != i) throw new IllegalArgumentException("Native object RAM slots must retain physical order");
            }
        }
    }

    private final Object[] nodes = new Object[SLOT_COUNT];
    private boolean runningPass;

    @SuppressWarnings("unchecked")
    public T at(int slot) { return (T) nodes[Objects.checkIndex(slot, SLOT_COUNT)]; }

    public void fixed(int slot, T node) {
        if (slot < 0 || slot >= FIRST_DYNAMIC_SLOT) throw new IllegalArgumentException("Fixed object slots are 0..7");
        replace(slot, node);
    }

    /** Explicit native writes may address every physical slot, including the allocation guard. */
    public void replace(int slot, T node) {
        Objects.checkIndex(slot, SLOT_COUNT);
        nodes[slot] = Objects.requireNonNull(node, "node");
    }

    public void clear(int slot) { nodes[Objects.checkIndex(slot, SLOT_COUNT)] = null; }
    public void clear() { Arrays.fill(nodes, null); }

    /** SSAllocateObject (s2.asm:9450-9460): B200, DBF $76, hence slots 8..126. */
    public int allocate(T node) { return claim(findFree(), node); }

    /** Returns an available native slot without claiming or changing it. */
    public int findFree() { return findFreeAtOrAfter(FIRST_DYNAMIC_SLOT); }

    /** The same current-inclusive scan as AfterCurrent, without an allocation side effect. */
    public int findFreeAtOrAfter(int current) {
        Objects.checkIndex(current, SLOT_COUNT);
        for (int slot = current; slot <= LAST_DYNAMIC_SLOT; slot++) {
            if (nodes[slot] == null) return slot;
        }
        return -1;
    }

    /**
     * SSAllocateObjectAfterCurrent (s2.asm:9465-9491) tests a0 itself first.
     * A caller in a fixed slot may therefore reuse that fixed slot; CFC0 is never tested.
     */
    public int allocateAfter(int current, T node) {
        Objects.checkIndex(current, SLOT_COUNT);
        return claim(findFreeAtOrAfter(current), node);
    }

    private int claim(int slot, T node) {
        Objects.requireNonNull(node, "node");
        if (slot >= 0) nodes[slot] = node;
        return slot;
    }

    /** RunObjects observes live ascending slots; new later occupants execute on this same pass. */
    public void runPass(BiConsumer<Integer, T> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        if (runningPass) throw new IllegalStateException("Native object pass cannot reenter itself");
        runningPass = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                T node = at(slot);
                if (node != null) visitor.accept(slot, node);
            }
        } finally {
            runningPass = false;
        }
    }

    /** Encodes each occupied node; a non-null immutable payload is required for an occupied slot. */
    public <S> State<S> capture(Function<T, S> encoder) {
        Objects.requireNonNull(encoder, "encoder");
        List<Slot<S>> slots = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            T node = at(slot);
            S state = node == null ? null : Objects.requireNonNull(encoder.apply(node), "encoded node");
            slots.add(new Slot<>(slot, state));
        }
        return new State<>(slots);
    }

    /** Recreates all occupants at their stable slots, publishing only after every decode succeeds. */
    public <S> void restore(State<S> state, Function<S, T> decoder) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(decoder, "decoder");
        Object[] restored = new Object[SLOT_COUNT];
        for (Slot<S> slot : state.slots()) {
            if (slot.state() != null) restored[slot.index()] = Objects.requireNonNull(decoder.apply(slot.state()), "decoded node");
        }
        System.arraycopy(restored, 0, nodes, 0, SLOT_COUNT);
    }
}
