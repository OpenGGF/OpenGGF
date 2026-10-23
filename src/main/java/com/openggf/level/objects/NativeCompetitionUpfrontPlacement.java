package com.openggf.level.objects;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Explicit one-shot native placement policy, separate from camera streaming.
 * S3K loc_1B720 advances Object_load_routine to its terminal RTS before the
 * first free-slot probe (sonic3k.asm:37499-37525). No later camera movement
 * or newly empty slot restarts this pass.
 */
final class NativeCompetitionUpfrontPlacement {
    private final int maxXExclusive;
    private List<ObjectSpawn> source = List.of();
    private boolean initialized;
    private int nextRecord;

    NativeCompetitionUpfrontPlacement(int maxXExclusive) {
        if (maxXExclusive <= 0 || maxXExclusive > 0xFFFF) {
            throw new IllegalArgumentException("Upfront cutoff must be an unsigned nonzero word");
        }
        this.maxXExclusive = maxXExclusive;
    }

    void reset(List<ObjectSpawn> source) {
        this.source = List.copyOf(source);
        initialized = false;
        nextRecord = 0;
    }

    void initialize(IntSupplier firstSlot, BiPredicate<ObjectSpawn, Integer> load, IntUnaryOperator nextSlot) {
        if (initialized) return;
        initialized = true;
        // AllocateObject precedes even the first X comparison. sub_1BA0C ends
        // with CreateNewSprite4, probing forward AFTER each write, including
        // the final record (sonic3k.asm:37829-37847,37934-37944).
        int slot = firstSlot.getAsInt();
        while (slot >= 0 && nextRecord < source.size()) {
            ObjectSpawn spawn = source.get(nextRecord);
            if ((spawn.x() & 0xFFFF) >= maxXExclusive || !load.test(spawn, slot)) return;
            nextRecord++;
            slot = nextSlot.applyAsInt(slot);
        }
    }

    boolean isOutsideUnloadWindow(int referenceX) {
        // loc_1B74C fixes Camera_X_pos_coarse_back at zero. The standard
        // Sprite_OnScreen_Test remains active and uses an unsigned >$280 test;
        // competition-specific persistence belongs to each object's routine
        // (sonic3k.asm:37516-37526,37302-37318).
        return (referenceX & 0xFF80) > 0x280;
    }

    Object capture() { return new State(initialized, nextRecord); }

    void restore(Object snapshot) {
        if (!(snapshot instanceof State state) || state.nextRecord < 0 || state.nextRecord > source.size()) {
            throw new IllegalArgumentException("Invalid native upfront placement snapshot");
        }
        initialized = state.initialized;
        nextRecord = state.nextRecord;
    }

    private record State(boolean initialized, int nextRecord) { }
}
