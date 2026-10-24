package com.openggf.graphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Actual-size private object art reservations, sharing the governed presentation range with trails.
 */
final class ObjectVisualPatternBanks {
    record Bank(int base, int size) {}

    private final IdentityHashMap<Object, Bank> owners = new IdentityHashMap<>();

    Bank acquire(Object owner, int count, Collection<Integer> trailBases, int trailSize) {
        Bank existing = owners.get(owner);
        if (existing != null) {
            if (existing.size() != Math.max(1, count)) {
                throw new IllegalArgumentException(
                        "Object art owner changed its immutable pattern count");
            }
            return existing;
        }
        int size = Math.max(1, count);
        List<Bank> occupied = new ArrayList<>(owners.values());
        for (int base : trailBases) {
            occupied.add(new Bank(base, trailSize));
        }
        occupied.sort(Comparator.comparingInt(Bank::base));
        int base = PatternAtlasRange.PRESENTATION_TRAILS.base();
        for (Bank bank : occupied) {
            if ((long) base + size <= bank.base()) {
                break;
            }
            base = Math.max(base, bank.base() + bank.size());
        }
        if ((long) base + size > PatternAtlasRange.PRESENTATION_TRAILS.endExclusive()) {
            throw new IllegalStateException(
                    "Directed object art exceeds the shared presentation pattern budget; reuse cast"
                        + " art or reduce distinct simultaneous appearances");
        }
        Bank bank = new Bank(base, size);
        owners.put(owner, bank);
        return bank;
    }

    boolean overlaps(int base, int size) {
        for (Bank bank : owners.values()) {
            if (base < bank.base() + bank.size() && bank.base() < base + size) {
                return true;
            }
        }
        return false;
    }

    boolean valid(Object owner, Bank bank) {
        return owners.get(owner) == bank;
    }

    void release(Object owner, Bank bank) {
        if (valid(owner, bank)) {
            owners.remove(owner);
        }
    }

    void clear() {
        owners.clear();
    }
}
