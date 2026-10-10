package starpost.core;

/**
 * The monitor slots Sonic carries (design doc §6.2): 12, then 24, then 36. The first twelve
 * are the hotbar; one of them is selected. Stacks hold up to 999.
 */
public final class Inventory {
    public static final int HOTBAR = 12;
    public static final int MAX_STACK = 999;

    private String[] ids = new String[HOTBAR];
    private int[] counts = new int[HOTBAR];
    private int selected;

    public int size() {
        return ids.length;
    }

    public void resize(int slots) {
        if (slots <= ids.length) {
            return;
        }
        ids = java.util.Arrays.copyOf(ids, slots);
        counts = java.util.Arrays.copyOf(counts, slots);
    }

    public String id(int slot) {
        return ids[slot];
    }

    public int count(int slot) {
        return ids[slot] == null ? 0 : counts[slot];
    }

    public int selected() {
        return selected;
    }

    public void select(int slot) {
        selected = Math.floorMod(slot, HOTBAR);
    }

    public String selectedId() {
        return ids[selected];
    }

    /** How many of an item are carried in all slots. */
    public int total(String id) {
        int n = 0;
        for (int i = 0; i < ids.length; i++) {
            if (id.equals(ids[i])) {
                n += counts[i];
            }
        }
        return n;
    }

    /**
     * Adds items, filling existing stacks first, then empty slots. Returns how many did not
     * fit (0 when all were taken).
     */
    public int add(Item item, int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (item.stackable()) {
            for (int i = 0; i < ids.length && amount > 0; i++) {
                if (item.id().equals(ids[i]) && counts[i] < MAX_STACK) {
                    int take = Math.min(amount, MAX_STACK - counts[i]);
                    counts[i] += take;
                    amount -= take;
                }
            }
        }
        for (int i = 0; i < ids.length && amount > 0; i++) {
            if (ids[i] == null) {
                int take = item.stackable() ? Math.min(amount, MAX_STACK) : 1;
                ids[i] = item.id();
                counts[i] = take;
                amount -= take;
            }
        }
        return amount;
    }

    /** Whether all of {@code amount} would fit. */
    public boolean fits(Item item, int amount) {
        int room = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == null) {
                room += item.stackable() ? MAX_STACK : 1;
            } else if (item.stackable() && item.id().equals(ids[i])) {
                room += MAX_STACK - counts[i];
            }
            if (room >= amount) {
                return true;
            }
        }
        return false;
    }

    /** Removes up to {@code amount} of an item; returns how many were removed. */
    public int remove(String id, int amount) {
        int removed = 0;
        for (int i = ids.length - 1; i >= 0 && removed < amount; i--) {
            if (id.equals(ids[i])) {
                int take = Math.min(amount - removed, counts[i]);
                counts[i] -= take;
                removed += take;
                if (counts[i] == 0) {
                    ids[i] = null;
                }
            }
        }
        return removed;
    }

    /** Takes one from a slot (planting, eating). */
    public void useOne(int slot) {
        if (ids[slot] != null && --counts[slot] <= 0) {
            ids[slot] = null;
            counts[slot] = 0;
        }
    }

    public void clear(int slot) {
        ids[slot] = null;
        counts[slot] = 0;
    }

    public void set(int slot, String id, int count) {
        ids[slot] = count > 0 ? id : null;
        counts[slot] = count > 0 ? count : 0;
    }

    public void swap(int a, int b) {
        String id = ids[a];
        int count = counts[a];
        ids[a] = ids[b];
        counts[a] = counts[b];
        ids[b] = id;
        counts[b] = count;
    }
}
