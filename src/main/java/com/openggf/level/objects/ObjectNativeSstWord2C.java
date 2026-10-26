package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/** One proven addressable WORD, not a model of otherwise unrepresented object RAM. */
final class ObjectNativeSstWord2C {
    record Cell(int slot, int value) { }
    record State(List<Cell> cells) {
        State { cells = List.copyOf(cells); }
    }

    private final int[] words;
    private final BitSet known = new BitSet();
    private final IntFunction<AbstractObjectInstance> ownerAt;
    private final ObjectCallbackRouter callbacks;
    private final BooleanSupplier reconstructing;

    ObjectNativeSstWord2C(int slots, IntFunction<AbstractObjectInstance> ownerAt,
            ObjectCallbackRouter callbacks, BooleanSupplier reconstructing) {
        words = new int[slots];
        this.ownerAt = Objects.requireNonNull(ownerAt);
        this.callbacks = Objects.requireNonNull(callbacks);
        this.reconstructing = Objects.requireNonNull(reconstructing);
    }

    private boolean constructionOnly() {
        return ObjectConstructionContext.isProbeConstruction()
                || ObjectConstructionContext.isRewindActiveRestore() || reconstructing.getAsBoolean();
    }

    private void requireGameplay(int slot) {
        checkSlot(slot);
        if (constructionOnly()) throw new IllegalStateException("SST $2C gameplay access during reconstruction/probe");
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= words.length) throw new IllegalArgumentException("Invalid physical SST: " + slot);
    }

    int read(int slot) {
        requireGameplay(slot);
        AbstractObjectInstance owner = ownerAt.apply(slot);
        if (owner != null) {
            NativeSstWord2CSource source = requireSource(owner, slot);
            remember(slot, callbacks.call(owner, source::nativeWord2C));
        }
        if (!known.get(slot)) throw new IllegalStateException("Unknown SST $2C word at slot " + slot);
        return words[slot];
    }

    void write(int slot, int value) {
        requireGameplay(slot);
        AbstractObjectInstance owner = ownerAt.apply(slot);
        int word = value & 0xFFFF;
        if (owner != null) {
            NativeSstWord2CSource source = requireSource(owner, slot);
            callbacks.run(owner, () -> source.writeNativeWord2C(word));
            if ((callbacks.call(owner, source::nativeWord2C) & 0xFFFF) != word)
                throw new IllegalStateException("SST $2C receiver did not retain the written word at slot " + slot);
        }
        remember(slot, word);
    }

    int add(int slot, int delta) {
        int word = (read(slot) + delta) & 0xFFFF;
        write(slot, word);
        return word;
    }

    /** Caller executes a real full clear and separately retires the native ID/dispatch owner. */
    void clear(int slot) {
        requireGameplay(slot);
        AbstractObjectInstance owner = ownerAt.apply(slot);
        if (owner instanceof NativeSstWord2CSource source) {
            callbacks.run(owner, () -> source.writeNativeWord2C(0));
            if ((callbacks.call(owner, source::nativeWord2C) & 0xFFFF) != 0)
                throw new IllegalStateException("SST $2C receiver did not retain full-clear zero at slot " + slot);
        }
        remember(slot, 0);
    }

    /** Sample only after successful factory construction, before detaching the old owner. */
    void preserve(int slot) {
        checkSlot(slot);
        if (constructionOnly()) return;
        AbstractObjectInstance owner = ownerAt.apply(slot);
        if (owner instanceof NativeSstWord2CSource source)
            remember(slot, callbacks.call(owner, source::nativeWord2C));
        else if (owner != null) known.clear(slot);
        // No owner is not evidence of zero; retain any known orphan word.
    }

    void invalidate(int slot) {
        requireGameplay(slot);
        known.clear(slot);
    }

    void projectPublished(ObjectInstance instance, boolean firstIdentity) {
        if (constructionOnly() || !(instance instanceof AbstractObjectInstance owner)) return;
        int slot = callbacks.call(owner, owner::getSlotIndex);
        if (slot < 0 || slot >= words.length) return;
        if (!(owner instanceof NativeSstWord2CSource source)) {
            known.clear(slot);
            return;
        }
        int word = callbacks.call(owner, source::nativeWord2C) & 0xFFFF;
        if (!firstIdentity && ownerAt.apply(slot) == owner) {
            remember(slot, word);
            return;
        }
        if (!known.get(slot) || words[slot] != word)
            throw new IllegalStateException("SST $2C birth must import known residue or execute an explicit write at slot " + slot);
    }

    private NativeSstWord2CSource requireSource(AbstractObjectInstance owner, int slot) {
        if (owner instanceof NativeSstWord2CSource source) return source;
        throw new UnsupportedOperationException("Unprojected live SST $2C receiver at slot " + slot
                + ": " + owner.getClass().getName());
    }

    private void remember(int slot, int word) {
        words[slot] = word & 0xFFFF;
        known.set(slot);
    }

    State capture() {
        // Capture synchronizes proven words; it cannot discover RAM through
        // unrelated, unprojected Java owners sharing a legacy fixed slot.
        for (int slot = known.nextSetBit(0); slot >= 0; slot = known.nextSetBit(slot + 1)) preserve(slot);
        List<Cell> cells = new ArrayList<>(known.cardinality());
        for (int slot = known.nextSetBit(0); slot >= 0; slot = known.nextSetBit(slot + 1))
            cells.add(new Cell(slot, words[slot]));
        return new State(cells);
    }

    void restore(State state) {
        Objects.requireNonNull(state);
        BitSet seen = new BitSet();
        for (Cell cell : state.cells()) {
            checkSlot(cell.slot());
            if (cell.value() < 0 || cell.value() > 0xFFFF || seen.get(cell.slot()))
                throw new IllegalArgumentException("Invalid SST $2C snapshot");
            seen.set(cell.slot());
        }
        known.clear();
        for (Cell cell : state.cells()) remember(cell.slot(), cell.value());
    }

    RewindSnapshottable<State> rewindAdapter() {
        return new RewindSnapshottable<>() {
            @Override public String key() { return NativeSstSlotAccess.WORD_2C_REWIND_KEY; }
            @Override public State capture() { return ObjectNativeSstWord2C.this.capture(); }
            @Override public void restore(State state) { ObjectNativeSstWord2C.this.restore(state); }
            @Override public void resetForMissingSnapshot() { known.clear(); }
        };
    }
}
