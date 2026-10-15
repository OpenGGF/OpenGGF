package com.openggf.editor.hud;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ten brush slots per library kind, cycled with the bumpers. The highlighted slot always holds
 * the active brush: choosing or picking something already on the bar selects its slot, anything
 * else replaces the highlighted slot, so slot positions never shift under the player's thumbs.
 */
final class EditorHudHotbar {
    static final int SLOTS = 10;

    private final Map<EditorHud.LibraryKind, int[]> slots = new EnumMap<>(EditorHud.LibraryKind.class);
    private final Map<EditorHud.LibraryKind, Integer> selected = new EnumMap<>(EditorHud.LibraryKind.class);

    boolean seeded(EditorHud.LibraryKind kind) { return slots.containsKey(kind); }

    /** Fills a kind's bar, typically with the level's most used entries; unused slots are -1. */
    void seed(EditorHud.LibraryKind kind, int[] values) {
        int[] bar = new int[SLOTS];
        Arrays.fill(bar, -1);
        for (int i = 0, n = 0; i < values.length && n < SLOTS; i++) {
            if (values[i] >= 0 && indexOf(bar, values[i]) < 0) bar[n++] = values[i];
        }
        slots.put(kind, bar);
        selected.put(kind, 0);
    }

    void clear() {
        slots.clear();
        selected.clear();
    }

    int[] slots(EditorHud.LibraryKind kind) {
        int[] bar = slots.get(kind);
        if (bar == null) {
            bar = new int[SLOTS];
            Arrays.fill(bar, -1);
            slots.put(kind, bar);
        }
        return bar.clone();
    }

    int selected(EditorHud.LibraryKind kind) { return selected.getOrDefault(kind, 0); }

    int value(EditorHud.LibraryKind kind) { return slots(kind)[selected(kind)]; }

    void select(EditorHud.LibraryKind kind, int slot) {
        selected.put(kind, Math.floorMod(slot, SLOTS));
    }

    /** Moves to the next slot that holds something, in either direction. */
    void cycle(EditorHud.LibraryKind kind, int direction) {
        int[] bar = slots(kind);
        int slot = selected(kind);
        for (int step = 0; step < SLOTS; step++) {
            slot = Math.floorMod(slot + direction, SLOTS);
            if (bar[slot] >= 0) break;
        }
        select(kind, slot);
    }

    /** Selects the slot holding {@code value}, or puts it in the highlighted slot. */
    void put(EditorHud.LibraryKind kind, int value) {
        if (value < 0) return;
        int[] bar = slots.computeIfAbsent(kind, ignored -> {
            int[] empty = new int[SLOTS];
            Arrays.fill(empty, -1);
            return empty;
        });
        int existing = indexOf(bar, value);
        if (existing >= 0) {
            select(kind, existing);
            return;
        }
        bar[selected(kind)] = value;
    }

    private static int indexOf(int[] bar, int value) {
        for (int i = 0; i < bar.length; i++) if (bar[i] == value) return i;
        return -1;
    }
}
