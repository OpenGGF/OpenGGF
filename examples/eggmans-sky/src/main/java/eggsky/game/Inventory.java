package eggsky.game;

/**
 * The Egg Mobile's cargo: a fixed number of slots, each holding one item type up to its stack
 * size. Adding fills existing stacks first, then empty slots; what does not fit is returned.
 */
public final class Inventory {
    private final Catalog catalog;
    private int[] item;
    private int[] count;

    public Inventory(Catalog catalog, int slots) {
        this.catalog = catalog;
        this.item = new int[slots];
        this.count = new int[slots];
    }

    public int slots() {
        return item.length;
    }

    /** Grows (never shrinks) to {@code slots}. */
    public void resize(int slots) {
        if (slots <= item.length) {
            return;
        }
        item = java.util.Arrays.copyOf(item, slots);
        count = java.util.Arrays.copyOf(count, slots);
    }

    public int itemAt(int slot) {
        return item[slot];
    }

    public int countAt(int slot) {
        return count[slot];
    }

    public void set(int slot, int id, int n) {
        item[slot] = n <= 0 ? 0 : id;
        count[slot] = Math.max(0, n);
    }

    private int stack(int id) {
        Catalog.Item it = catalog.item(id);
        return it == null ? 1 : it.stack();
    }

    /** Adds up to {@code n}; returns how many did not fit. */
    public int add(int id, int n) {
        if (id <= 0 || n <= 0) {
            return n;
        }
        int max = stack(id);
        for (int i = 0; i < item.length && n > 0; i++) {
            if (item[i] == id && count[i] < max) {
                int take = Math.min(n, max - count[i]);
                count[i] += take;
                n -= take;
            }
        }
        for (int i = 0; i < item.length && n > 0; i++) {
            if (item[i] == 0) {
                int take = Math.min(n, max);
                item[i] = id;
                count[i] = take;
                n -= take;
            }
        }
        return n;
    }

    /** How many of {@code id} would fit. */
    public int room(int id) {
        int max = stack(id);
        int room = 0;
        for (int i = 0; i < item.length; i++) {
            if (item[i] == id) {
                room += max - count[i];
            } else if (item[i] == 0) {
                room += max;
            }
        }
        return room;
    }

    public int count(int id) {
        int n = 0;
        for (int i = 0; i < item.length; i++) {
            if (item[i] == id) {
                n += count[i];
            }
        }
        return n;
    }

    public boolean has(int id, int n) {
        return count(id) >= n;
    }

    /** Removes up to {@code n}, from the smallest stacks first; returns how many were removed. */
    public int remove(int id, int n) {
        int removed = 0;
        while (n > 0) {
            int best = -1;
            for (int i = 0; i < item.length; i++) {
                if (item[i] == id && (best < 0 || count[i] < count[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            int take = Math.min(n, count[best]);
            count[best] -= take;
            if (count[best] == 0) {
                item[best] = 0;
            }
            n -= take;
            removed += take;
        }
        return removed;
    }

    public int usedSlots() {
        int n = 0;
        for (int i : item) {
            if (i != 0) {
                n++;
            }
        }
        return n;
    }

    /** "id:count,id:count,..." per slot (empty slots as 0:0). */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < item.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item[i]).append(':').append(count[i]);
        }
        return sb.toString();
    }

    public void decode(String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        String[] parts = s.split(",");
        resize(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String[] kv = parts[i].split(":");
            if (kv.length == 2) {
                try {
                    int id = Integer.parseInt(kv[0]);
                    int n = Integer.parseInt(kv[1]);
                    set(i, catalog.item(id) == null ? 0 : id, n);
                } catch (NumberFormatException ignored) {
                    set(i, 0, 0);
                }
            }
        }
    }

    /** Sorts slots by item id, merging partial stacks. */
    public void sort() {
        int[] totals = new int[Catalog.MAX_ITEM];
        for (int i = 0; i < item.length; i++) {
            if (item[i] > 0 && item[i] < totals.length) {
                totals[item[i]] += count[i];
            }
            item[i] = 0;
            count[i] = 0;
        }
        for (int id = 1; id < totals.length; id++) {
            if (totals[id] > 0) {
                add(id, totals[id]);
            }
        }
    }
}
