package starpost.core;

import java.util.Map;
import java.util.TreeMap;

/**
 * The save file: one {@code key=value} per line, written at bedtime. Reading builds a fresh
 * {@link Game}; anything unknown or out of range is dropped rather than trusted, so an edited or
 * damaged file can never put the game in an impossible state.
 */
public final class SaveCodec {
    public static final int VERSION = 1;
    public static final String FILE = "valley.sav";

    private SaveCodec() {
    }

    public static String encode(Game g) {
        StringBuilder out = new StringBuilder();
        line(out, "version", VERSION);
        line(out, "rng", g.rng.snapshot());
        line(out, "farmer", g.farmer);
        line(out, "farmName", g.farmName);
        line(out, "calendar", g.calendar.year() + "," + g.calendar.season() + "," + g.calendar.day());
        line(out, "rings", g.rings);
        line(out, "earned", g.totalEarned);
        line(out, "momentum", g.momentum + "," + g.maxMomentum);
        line(out, "weather", (g.raining ? 1 : 0) + "," + (g.rainTomorrow ? 1 : 0));
        line(out, "water", g.waterCharges + "," + g.waterCapacity);
        line(out, "flags", String.join(",", g.flags));
        line(out, "inventory", g.inventory.size() + "," + g.inventory.selected());
        for (int i = 0; i < g.inventory.size(); i++) {
            if (g.inventory.id(i) != null) {
                line(out, "slot." + i, g.inventory.id(i) + "," + g.inventory.count(i));
            }
        }
        line(out, "farm.open", g.farm.open());
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.COLUMNS; c++) {
                Plot p = g.farm.raw(r, c);
                if (p == null) {
                    continue;
                }
                if (p.cover != Plot.GRASS || p.tilled || p.crop != null) {
                    line(out, "plot." + r + "." + c, p.cover + "," + (p.tilled ? 1 : 0) + "," + (p.watered ? 1 : 0)
                            + "," + (p.crop == null ? "" : p.crop) + "," + p.age + "," + (p.dead ? 1 : 0));
                }
            }
        }
        for (Map.Entry<String, Integer> e : g.shipping.entrySet()) {
            line(out, "ship." + e.getKey(), e.getValue());
        }
        for (SaveSection section : g.sections) {
            Map<String, String> keys = new TreeMap<>();
            section.save(keys);
            for (Map.Entry<String, String> e : keys.entrySet()) {
                line(out, "s." + section.prefix() + "." + e.getKey(), e.getValue());
            }
        }
        return out.toString();
    }

    /**
     * The saved game, or null when the text is not a save this version can read. {@code sections}
     * are installed in the game and given their keys.
     */
    public static Game decode(Catalog catalog, String text, java.util.List<SaveSection> sections) {
        Game g = decode(catalog, text);
        if (g == null) {
            return null;
        }
        Map<String, String> v = parse(text);
        for (SaveSection section : sections) {
            String prefix = "s." + section.prefix() + ".";
            Map<String, String> keys = new TreeMap<>();
            for (Map.Entry<String, String> e : v.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    keys.put(e.getKey().substring(prefix.length()), e.getValue());
                }
            }
            try {
                section.load(keys, catalog);
            } catch (RuntimeException e) {
                return null;
            }
            g.sections.add(section);
        }
        return g;
    }

    private static Map<String, String> parse(String text) {
        Map<String, String> v = new TreeMap<>();
        for (String raw : text.split("\n")) {
            int eq = raw.indexOf('=');
            if (eq > 0 && raw.length() < 4096) {
                v.put(raw.substring(0, eq).trim(), raw.substring(eq + 1).trim());
            }
        }
        return v;
    }

    /** The core game only (no sections), or null when unreadable. */
    public static Game decode(Catalog catalog, String text) {
        Map<String, String> v = parse(text);
        try {
            if (Integer.parseInt(v.getOrDefault("version", "0")) != VERSION) {
                return null;
            }
            Game g = new Game(catalog, 0);
            g.rng.restore(Long.parseLong(v.get("rng")));
            String farmer = v.getOrDefault("farmer", "sonic");
            g.farmer = farmer.equals("tails") || farmer.equals("knuckles") ? farmer : "sonic";
            g.farmName = clean(v.getOrDefault("farmName", "STARPOST"));
            int[] cal = ints(v.get("calendar"), 3);
            g.calendar.set(cal[0], cal[1], cal[2], Calendar.DAY_START);
            g.rings = Math.max(0, Integer.parseInt(v.getOrDefault("rings", "0")));
            g.totalEarned = Math.max(0, Long.parseLong(v.getOrDefault("earned", "0")));
            int[] mo = ints(v.get("momentum"), 2);
            g.maxMomentum = Math.max(Game.BASE_MOMENTUM, Math.min(400, mo[1]));
            g.momentum = Math.max(0, Math.min(g.maxMomentum, mo[0]));
            int[] weather = ints(v.getOrDefault("weather", "0,0"), 2);
            g.raining = weather[0] == 1;
            g.rainTomorrow = weather[1] == 1;
            int[] water = ints(v.getOrDefault("water", "10,10"), 2);
            g.waterCapacity = Math.max(10, Math.min(200, water[1]));
            g.waterCharges = Math.max(0, Math.min(g.waterCapacity, water[0]));
            for (String flag : v.getOrDefault("flags", "").split(",")) {
                if (!flag.isBlank() && flag.length() < 64) {
                    g.flags.add(flag);
                }
            }
            int[] inv = ints(v.get("inventory"), 2);
            g.inventory.resize(Math.max(Inventory.HOTBAR, Math.min(36, inv[0])));
            g.inventory.select(inv[1]);
            for (int i = 0; i < g.inventory.size(); i++) {
                String slot = v.get("slot." + i);
                if (slot != null) {
                    String[] parts = slot.split(",");
                    int count = Integer.parseInt(parts[1]);
                    if (catalog.hasItem(parts[0]) && count > 0) {
                        g.inventory.set(i, parts[0], Math.min(Inventory.MAX_STACK, count));
                    }
                }
            }
            g.farm.open(Integer.parseInt(v.getOrDefault("farm.open", Integer.toString(Farm.START_COLUMNS))));
            for (int r = 0; r < Farm.ROWS; r++) {
                for (int c = 0; c < Farm.COLUMNS; c++) {
                    String plot = v.get("plot." + r + "." + c);
                    Plot p = g.farm.raw(r, c);
                    if (p == null) {
                        continue;
                    }
                    p.cover = Plot.GRASS;
                    if (plot == null) {
                        continue;
                    }
                    String[] parts = plot.split(",", -1);
                    p.cover = Math.max(0, Math.min(Plot.STUMP, Integer.parseInt(parts[0])));
                    p.tilled = parts[1].equals("1");
                    p.watered = parts[2].equals("1");
                    p.crop = !parts[3].isEmpty() && catalog.crop(parts[3]) != null ? parts[3] : null;
                    p.age = p.crop == null ? 0 : Math.max(0, Math.min(99, Integer.parseInt(parts[4])));
                    p.dead = p.crop != null && parts[5].equals("1");
                }
            }
            for (Map.Entry<String, String> e : v.entrySet()) {
                if (e.getKey().startsWith("ship.")) {
                    String id = e.getKey().substring(5);
                    int count = Integer.parseInt(e.getValue());
                    if (catalog.hasItem(id) && count > 0) {
                        g.ship(id, Math.min(99999, count));
                    }
                }
            }
            return g;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void line(StringBuilder out, String key, Object value) {
        out.append(key).append('=').append(value).append('\n');
    }

    private static int[] ints(String text, int n) {
        String[] parts = text.split(",");
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    private static String clean(String name) {
        StringBuilder out = new StringBuilder();
        for (char ch : name.toUpperCase().toCharArray()) {
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == ' ') {
                out.append(ch);
            }
        }
        String s = out.toString().trim();
        return s.isEmpty() ? "STARPOST" : s.substring(0, Math.min(12, s.length()));
    }
}
