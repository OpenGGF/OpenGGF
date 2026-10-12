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
        if (slots < 0 || slots > Player.maxSlots(catalog)) throw new IllegalArgumentException("Cargo capacity");
        this.item = new int[slots];
        this.count = new int[slots];
    }

    public int slots() {
        return item.length;
    }

    /** Grows (never shrinks) to {@code slots}. */
    public void resize(int slots) {
        if (slots < 0 || slots > Player.maxSlots(catalog)) throw new IllegalArgumentException("Cargo capacity");
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

    /** Checks slot and stack bounds before any allocation or mutation. */
    public static boolean validEncoding(Catalog catalog, String text, int capacity) {
        if (text == null) return false;
        if (text.isEmpty()) return true;
        int slots = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ',' && ++slots > capacity) return false;
        if (slots > capacity) return false;
        try {
            for (String part : text.split(",", -1)) {
                String[] pair = part.split(":", -1);
                if (pair.length != 2) return false;
                int id = Integer.parseInt(pair[0]);
                int n = Integer.parseInt(pair[1]);
                Catalog.Item definition = catalog.item(id);
                if (id == 0 ? n != 0 : definition == null || n <= 0 || n > definition.stack()) return false;
            }
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    public boolean decode(String text) {
        if (!validEncoding(catalog, text, Player.maxSlots(catalog))) return false;
        String[] parts = text.isEmpty() ? new String[0] : text.split(",");
        resize(parts.length);
        java.util.Arrays.fill(item, 0);
        java.util.Arrays.fill(count, 0);
        for (int i = 0; i < parts.length; i++) {
            String[] pair = parts[i].split(":");
            set(i, Integer.parseInt(pair[0]), Integer.parseInt(pair[1]));
        }
        return true;
    }

    void replaceWith(Inventory candidate) {
        item = candidate.item;
        count = candidate.count;
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
