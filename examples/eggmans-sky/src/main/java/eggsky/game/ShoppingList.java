package eggsky.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A single pinned recipe or next technology tier, including missing intermediate steps. */
public final class ShoppingList {
    public record Need(int item, int owned, int required, int depth) { }
    private ShoppingList() { }

    public static String title(Player p) {
        if (p.pinned < 0) {
            Catalog.Tech t = p.catalog().tech(-p.pinned - 1);
            return t.name() + (p.level(t.id()) >= t.max() ? " COMPLETE" : " UPGRADE");
        }
        return p.pinned == 0 ? "" : p.catalog().name(p.pinned);
    }

    public static List<Need> needs(Player p) {
        Map<Integer, Integer> required = new LinkedHashMap<>();
        Map<Integer, Integer> depths = new LinkedHashMap<>();
        if (p.pinned < 0) {
            Catalog.Tech t = p.catalog().tech(-p.pinned - 1);
            if (p.level(t.id()) < t.max()) {
                add(p, t.material(), t.count() * (p.level(t.id()) + 1), 0, required, depths);
            }
        } else {
            p.catalog().recipes().stream().filter(r -> r.output() == p.pinned).findFirst().ifPresent(r -> {
                for (int i = 0; i < r.inputs().length; i++) {
                    add(p, r.inputs()[i], r.counts()[i], 0, required, depths);
                }
            });
        }
        List<Need> result = new ArrayList<>();
        required.forEach((id, n) -> result.add(new Need(id, p.cargo.count(id), n, depths.get(id))));
        return result;
    }

    private static void add(Player p, int id, int n, int depth, Map<Integer, Integer> totals,
            Map<Integer, Integer> depths) {
        int before = totals.getOrDefault(id, 0);
        totals.put(id, before + n);
        depths.merge(id, depth, Math::min);
        int missing = Math.max(0, before + n - p.cargo.count(id)) - Math.max(0, before - p.cargo.count(id));
        if (missing == 0 || depth >= 4) {
            return;
        }
        Catalog.Recipe recipe = p.catalog().recipes().stream().filter(r -> r.output() == id).findFirst().orElse(null);
        if (recipe != null) {
            int batches = (missing + recipe.outCount() - 1) / recipe.outCount();
            for (int i = 0; i < recipe.inputs().length; i++) {
                add(p, recipe.inputs()[i], recipe.counts()[i] * batches, depth + 1, totals, depths);
            }
        } else {
            // The first catalogue conversion is the basic route (e.g. copper to chromatic
            // metal). Stop at raw materials rather than following optional recycling recipes.
            if (id == Catalog.PURE_FERRITE || id == Catalog.MAGNETISED_FERRITE
                    || id == Catalog.CONDENSED_CARBON || id == Catalog.DIHYDROGEN_JELLY
                    || id == Catalog.CHROMATIC_METAL) {
                Catalog.Refine r = p.catalog().refines().stream().filter(v -> v.output() == id).findFirst().orElse(null);
                if (r != null) {
                    add(p, r.input(), (missing + r.outCount() - 1) / r.outCount() * r.inCount(),
                            depth + 1, totals, depths);
                }
            }
        }
    }

    public static boolean wanted(Player p, int item) {
        return p.pinned != 0 && needs(p).stream().anyMatch(n -> n.item() == item && n.owned() < n.required());
    }
}
