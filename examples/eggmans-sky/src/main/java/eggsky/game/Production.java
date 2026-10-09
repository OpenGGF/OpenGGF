package eggsky.game;

/** Capacity-aware, atomic production. Preview and execution use the same calculation. */
public final class Production {
    private Production() { }

    public static int maximum(Player p, int output, int outCount, int[] inputs, int[] counts) {
        int limit = Integer.MAX_VALUE;
        for (int i = 0; i < inputs.length; i++) {
            limit = Math.min(limit, p.available(inputs[i]) / counts[i]);
        }
        // Removing ingredients may free slots. Capacity is not monotonic at those boundaries,
        // so inspect candidates downward rather than binary-searching initial free space.
        for (int n = limit; n > 0; n--) {
            if (preview(p, output, outCount, inputs, counts, n) != null) {
                return n;
            }
        }
        return 0;
    }

    private static Inventory preview(Player p, int output, int outCount, int[] inputs, int[] counts, int n) {
        if (n <= 0) {
            return null;
        }
        Inventory next = new Inventory(p.catalog(), p.cargo.slots());
        next.decode(p.cargo.encode());
        for (int i = 0; i < inputs.length; i++) {
            if (p.available(inputs[i]) < (long) counts[i] * n) {
                return null;
            }
            next.remove(inputs[i], counts[i] * n);
        }
        return next.add(output, outCount * n) == 0 ? next : null;
    }

    public static boolean make(Player p, int output, int outCount, int[] inputs, int[] counts, int n) {
        Inventory next = preview(p, output, outCount, inputs, counts, n);
        if (next == null) {
            return false;
        }
        p.cargo.decode(next.encode());
        return true;
    }
}
