package starpost.fishing;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import starpost.core.Catalog;
import starpost.core.SaveSection;

/**
 * What the farmer has landed (the {@code fishing} save section): a count per fish, which makes
 * the collection and keeps the once-only legends from biting twice. Only known fish are kept on
 * load, with counts clamped, so a damaged save cannot invent catches.
 */
public final class FishingSection implements SaveSection {
    public static final String PREFIX = "fishing";
    public static final int MAX_COUNT = 99999;

    private final FishTable table = new FishTable();
    private final Map<String, Integer> landed = new LinkedHashMap<>();

    public FishTable table() {
        return table;
    }

    /** Records a landed catch (junk included). */
    public void record(String id) {
        if (table.get(id) != null || FishTable.JUNK.equals(id)) {
            landed.merge(id, 1, (a, b) -> Math.min(MAX_COUNT, a + b));
        }
    }

    public int landed(String id) {
        return landed.getOrDefault(id, 0);
    }

    /** How many different fish and badniks have been landed (junk does not count). */
    public int species() {
        int n = 0;
        for (String id : landed.keySet()) {
            if (table.get(id) != null) {
                n++;
            }
        }
        return n;
    }

    /** The once-only catches already landed. */
    public Set<String> landedOnce() {
        Set<String> out = new LinkedHashSet<>();
        for (String id : landed.keySet()) {
            FishDef def = table.get(id);
            if (def != null && def.once()) {
                out.add(id);
            }
        }
        return out;
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public void save(Map<String, String> out) {
        for (Map.Entry<String, Integer> e : landed.entrySet()) {
            out.put("landed." + e.getKey(), Integer.toString(e.getValue()));
        }
    }

    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        landed.clear();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (!e.getKey().startsWith("landed.")) {
                continue;
            }
            String id = e.getKey().substring("landed.".length());
            int count;
            try {
                count = Integer.parseInt(e.getValue().trim());
            } catch (NumberFormatException bad) {
                continue;
            }
            if ((table.get(id) != null || FishTable.JUNK.equals(id)) && count > 0) {
                FishDef def = table.get(id);
                landed.put(id, def != null && def.once() ? 1 : Math.min(MAX_COUNT, count));
            }
        }
    }
}
