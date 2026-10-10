package starpost.barn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.CropDef;
import starpost.core.Farm;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.core.PlaceableDef;
import starpost.core.Plot;
import starpost.core.SaveSection;
import starpost.fishing.FishDef;
import starpost.fishing.FishTable;

/**
 * The farm's animals and buildings (the {@code barn} save section): the Cucky Coop and the Pocky
 * Pen (each built, then made big once the Capsule's Hatchery or Robomart grants {@code big_coop}),
 * their feed hoppers, the animals, what they have given, the truffles a Picky has dug up, and the
 * Flicky Roosts' morning harvest.
 *
 * <p>Overnight, in this order: the roosts pick every ripe crop within reach of their row into
 * their baskets; each animal that was fed yesterday may give (if it is grown and its goods are due,
 * with a chance that rises with affection); affection drifts (down without a pet, sharply down
 * without food, a little up in a big building); then each animal eats for the new day by grazing
 * (dry weather outside winter, four plots of open grass each) or from its hopper.
 */
public final class Barn implements SaveSection {
    public static final String PREFIX = "barn";
    public static final int MAX_FEED = 240;
    public static final int MAX_GOODS = 99;
    public static final int MAX_TRUFFLES = 20;
    public static final int GRASS_PER_ANIMAL = 4;
    public static final int POND_CAPACITY = 2;
    /** A sunflower's seeds in the coop's hopper are three days of feed (the sunflower's own rule). */
    public static final int SUNFLOWER_FEED = 3;
    public static final String BIG = "big_coop";

    /** One crop a roost's Flickies picked this morning (shown flying in, never saved). */
    public record Harvest(int roostRow, int roostColumn, int row, int column, String item) {
    }

    private final FishTable fish = new FishTable();
    /** 0 not built, 1 built, 2 big. */
    public int coop;
    public int pen;
    public int coopFeed;
    public int penFeed;
    public final List<Animal> animals = new ArrayList<>();
    public final Map<String, Integer> coopGoods = new LinkedHashMap<>();
    public final Map<String, Integer> penGoods = new LinkedHashMap<>();
    /** Truffles waiting in the grass, as "row.column". */
    public final Set<String> truffles = new LinkedHashSet<>();
    public final List<Harvest> harvested = new ArrayList<>();
    private int nextId = 1;

    // ------------------------------------------------------------------ buildings

    public int level(int home) {
        return switch (home) {
            case Animals.COOP -> coop;
            case Animals.PEN -> pen;
            default -> 1;
        };
    }

    public int capacity(int home) {
        return switch (home) {
            case Animals.COOP -> coop * 4;
            case Animals.PEN -> pen * 4;
            default -> POND_CAPACITY;
        };
    }

    public int count(int home) {
        int n = 0;
        for (Animal a : animals) {
            if (Animals.home(a.kind) == home) {
                n++;
            }
        }
        return n;
    }

    public Map<String, Integer> goods(int home) {
        return home == Animals.PEN ? penGoods : coopGoods;
    }

    public int feed(int home) {
        return home == Animals.PEN ? penFeed : coopFeed;
    }

    private void setFeed(int home, int value) {
        if (home == Animals.PEN) {
            penFeed = value;
        } else {
            coopFeed = value;
        }
    }

    // ------------------------------------------------------------------ the farmer's actions

    /** Buys an animal: null when it joined, otherwise why not. */
    public String buy(Game game, String kind) {
        int home = Animals.home(kind);
        if (level(home) == 0) {
            return home == Animals.COOP ? "BUILD THE CUCKY COOP FIRST" : "BUILD THE POCKY PEN FIRST";
        }
        if (count(home) >= capacity(home)) {
            return "NO ROOM: " + count(home) + "/" + capacity(home);
        }
        int price = Animals.price(kind);
        if (game.rings < price) {
            return "NOT ENOUGH RINGS";
        }
        game.rings -= price;
        Animal animal = new Animal(nextId++, kind, freeName(kind));
        animal.affection = 100;
        animals.add(animal);
        return null;
    }

    private String freeName(String kind) {
        for (String name : Animals.names(kind)) {
            boolean taken = false;
            for (Animal a : animals) {
                taken |= a.name.equals(name);
            }
            if (!taken) {
                return name;
            }
        }
        return Animals.name(kind) + " " + nextId;
    }

    /** Puts fibre (and, in the coop, sunflowers) from the monitors into a hopper. Returns the feed added. */
    public int fill(Game game, int home) {
        int added = 0;
        int room = MAX_FEED - feed(home);
        int fibre = Math.min(room, game.inventory.total("fibre"));
        if (fibre > 0) {
            game.inventory.remove("fibre", fibre);
            added += fibre;
            room -= fibre;
        }
        if (home == Animals.COOP && room >= SUNFLOWER_FEED) {
            int flowers = Math.min(room / SUNFLOWER_FEED, game.inventory.total("sunflower"));
            if (flowers > 0) {
                game.inventory.remove("sunflower", flowers);
                added += flowers * SUNFLOWER_FEED;
            }
        }
        setFeed(home, feed(home) + added);
        return added;
    }

    /** Takes what a building's animals gave into the monitors (as much as fits). Returns how many. */
    public int collect(Game game, int home) {
        int taken = 0;
        Map<String, Integer> goods = goods(home);
        for (Map.Entry<String, Integer> e : new ArrayList<>(goods.entrySet())) {
            Item item = game.item(e.getKey());
            int left = game.inventory.add(item, e.getValue());
            taken += e.getValue() - left;
            if (left > 0) {
                goods.put(e.getKey(), left);
            } else {
                goods.remove(e.getKey());
            }
        }
        if (taken > 0) {
            game.xp(starpost.core.Skills.FARMING, taken * 2);
        }
        return taken;
    }

    /** A pet, once a day: affection now (twice as much for a Cuddler). False when already petted today. */
    public boolean pet(Game game, Animal animal) {
        if (animal.petted) {
            return false;
        }
        animal.petted = true;
        animal.affection = Math.min(Animal.MAX_AFFECTION, animal.affection + Animals.petGain(game));
        return true;
    }

    /** Rocky hands over his catch. Returns the item id, or null when he has none (or no room). */
    public String takeCatch(Game game, Animal rocky) {
        if (rocky.holding == null || !game.catalog.hasItem(rocky.holding)) {
            return null;
        }
        String id = rocky.holding;
        if (game.inventory.add(game.item(id), 1) > 0) {
            return null;
        }
        rocky.holding = null;
        return id;
    }

    /** Picks up a truffle from the grass. */
    public boolean pickTruffle(Game game, int row, int column) {
        if (!truffles.contains(row + "." + column) || !game.inventory.fits(game.item("hill_truffle"), 1)) {
            return false;
        }
        truffles.remove(row + "." + column);
        game.inventory.add(game.item("hill_truffle"), 1);
        game.xp(starpost.core.Skills.FARMING, 6);
        return true;
    }

    // ------------------------------------------------------------------ overnight

    @Override
    public void nextDay(Game game) {
        harvested.clear();
        harvestRoosts(game);
        int season = game.calendar.season();
        int weather = game.weather;
        int grass = grassPlots(game);
        int grazers = 0;
        for (Animal a : animals) {
            int home = Animals.home(a.kind);
            a.age = Math.min(9999, a.age + 1);
            a.since = Math.min(99, a.since + 1);
            boolean fedYesterday = a.fed;
            if (fedYesterday && a.age >= Animals.adult(a.kind)) {
                give(game, a, season, weather);
            }
            int change = a.petted ? 0 : -4;
            if (!fedYesterday) {
                change -= 20;
            }
            if (level(home) == 2) {
                change += 3;
            }
            a.affection = Math.max(0, Math.min(Animal.MAX_AFFECTION, a.affection + change));
            a.petted = false;
            if (home == Animals.POND) {
                a.fed = true;
            } else if (season != Calendar.WINTER && Animals.outside(a.kind, weather)
                    && grazers < grass / GRASS_PER_ANIMAL) {
                grazers++;
                a.fed = true;
            } else if (feed(home) > 0) {
                setFeed(home, feed(home) - 1);
                a.fed = true;
            } else {
                a.fed = false;
            }
        }
    }

    private void give(Game game, Animal a, int season, int weather) {
        if (a.since < Animals.every(a.kind, game) || !Animals.givesToday(a.kind, season, weather)
                || game.rng.nextInt(100) >= Animals.chance(a.affection)) {
            return;
        }
        a.since = 0;
        int count = a.affection >= 800 && game.rng.nextInt(100) < 25 ? 2 : 1;
        switch (a.kind) {
            case "rocky" -> {
                if (a.holding == null) {
                    a.holding = fish.chooseFish(new FishTable.Waters(FishDef.POND, season, 9 * 60, weather, false, 50,
                            game.flags, Set.of()), game.rng);
                }
            }
            case "picky" -> {
                for (int i = 0; i < count; i++) {
                    digTruffle(game);
                }
            }
            default -> goods(Animals.home(a.kind)).merge(Animals.product(a.kind), count,
                    (x, y) -> Math.min(MAX_GOODS, x + y));
        }
    }

    /** A truffle turns up in a random patch of open grass (none when the grass is all dug or planted). */
    private void digTruffle(Game game) {
        if (truffles.size() >= MAX_TRUFFLES) {
            return;
        }
        List<String> spots = new ArrayList<>();
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < game.farm.open(); c++) {
                if (grass(game.farm.plot(r, c)) && !truffles.contains(r + "." + c)) {
                    spots.add(r + "." + c);
                }
            }
        }
        if (!spots.isEmpty()) {
            truffles.add(spots.get(game.rng.nextInt(spots.size())));
        }
    }

    private static boolean grass(Plot p) {
        return p != null && p.cover == Plot.GRASS && !p.tilled && p.crop == null && p.object == null;
    }

    /** Open grass plots on the farm: what grazing animals eat. */
    public static int grassPlots(Game game) {
        int n = 0;
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < game.farm.open(); c++) {
                if (grass(game.farm.plot(r, c))) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Each Flicky Roost picks the ripe crops within its reach on its row into its basket. */
    void harvestRoosts(Game game) {
        Catalog catalog = game.catalog;
        Farm farm = game.farm;
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.COLUMNS; c++) {
                Plot plot = farm.raw(r, c);
                PlaceableDef def = plot.object == null ? null : catalog.placeable(plot.object);
                if (def == null || def.role() != PlaceableDef.Role.ROOST) {
                    continue;
                }
                Inventory basket = farm.chest(r, c, def.slots());
                for (int cc = c - def.reach(); cc <= c + def.reach(); cc++) {
                    Plot crop = farm.plot(r, cc);
                    if (crop == null || !farm.ripe(catalog, crop)) {
                        continue;
                    }
                    CropDef crops = catalog.crop(crop.crop);
                    Item item = catalog.item(crops.produce());
                    if (!basket.fits(item, crops.yield())) {
                        continue;
                    }
                    farm.harvest(catalog, crop);
                    basket.add(item, crops.yield());
                    harvested.add(new Harvest(r, c, r, cc, item.id()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public void save(Map<String, String> out) {
        out.put("buildings", coop + "," + pen);
        out.put("feed", coopFeed + "," + penFeed);
        out.put("next", Integer.toString(nextId));
        for (Animal a : animals) {
            out.put("animal." + a.id, a.kind + "," + a.name + "," + a.affection + "," + (a.fed ? 1 : 0) + ","
                    + (a.petted ? 1 : 0) + "," + a.age + "," + a.since + "," + (a.holding == null ? "" : a.holding));
        }
        for (Map.Entry<String, Integer> e : coopGoods.entrySet()) {
            out.put("goods.coop." + e.getKey(), Integer.toString(e.getValue()));
        }
        for (Map.Entry<String, Integer> e : penGoods.entrySet()) {
            out.put("goods.pen." + e.getKey(), Integer.toString(e.getValue()));
        }
        for (String spot : truffles) {
            out.put("truffle." + spot, "1");
        }
    }

    /**
     * Restores the section. Malformed numbers reject the save (the codec then refuses it); values
     * out of range are clamped; unknown animals, goods and spots are dropped; animals beyond a
     * building's room are turned away.
     */
    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        animals.clear();
        coopGoods.clear();
        penGoods.clear();
        truffles.clear();
        harvested.clear();
        int[] buildings = ints(in.getOrDefault("buildings", "0,0"), 2);
        coop = clamp(buildings[0], 0, 2);
        pen = clamp(buildings[1], 0, 2);
        int[] feed = ints(in.getOrDefault("feed", "0,0"), 2);
        coopFeed = clamp(feed[0], 0, MAX_FEED);
        penFeed = clamp(feed[1], 0, MAX_FEED);
        nextId = clamp(Integer.parseInt(in.getOrDefault("next", "1").trim()), 1, 1_000_000);
        for (Map.Entry<String, String> e : in.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("animal.")) {
                loadAnimal(Integer.parseInt(key.substring(7)), e.getValue().split(",", -1), catalog);
            } else if (key.startsWith("goods.coop.") || key.startsWith("goods.pen.")) {
                boolean penKey = key.startsWith("goods.pen.");
                String id = key.substring(penKey ? 10 : 11);
                int count = Integer.parseInt(e.getValue().trim());
                if (catalog.hasItem(id) && catalog.item(id).kind() == Kind.ANIMAL_GOOD && count > 0) {
                    (penKey ? penGoods : coopGoods).put(id, Math.min(MAX_GOODS, count));
                }
            } else if (key.startsWith("truffle.")) {
                String[] rc = key.substring(8).split("\\.");
                int r = Integer.parseInt(rc[0]), c = Integer.parseInt(rc[1]);
                if (r >= 0 && r < Farm.ROWS && c >= 0 && c < Farm.COLUMNS && truffles.size() < MAX_TRUFFLES) {
                    truffles.add(r + "." + c);
                }
            }
        }
    }

    private void loadAnimal(int id, String[] p, Catalog catalog) {
        String kind = p[0];
        int home = Animals.known(kind) ? Animals.home(kind) : -1;
        if (home < 0 || count(home) >= capacity(home) || id <= 0) {
            return;
        }
        Animal a = new Animal(id, kind, clean(p[1], kind));
        a.affection = clamp(Integer.parseInt(p[2].trim()), 0, Animal.MAX_AFFECTION);
        a.fed = p[3].equals("1");
        a.petted = p[4].equals("1");
        a.age = clamp(Integer.parseInt(p[5].trim()), 0, 9999);
        a.since = clamp(Integer.parseInt(p[6].trim()), 0, 99);
        String holding = p.length > 7 ? p[7] : "";
        a.holding = kind.equals("rocky") && catalog.hasItem(holding) && catalog.item(holding).kind() == Kind.FISH
                ? holding : null;
        animals.add(a);
        nextId = Math.max(nextId, id + 1);
    }

    private static String clean(String name, String kind) {
        StringBuilder out = new StringBuilder();
        for (char ch : name.toUpperCase().toCharArray()) {
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == ' ') {
                out.append(ch);
            }
        }
        String s = out.toString().trim();
        return s.isEmpty() ? Animals.name(kind) : s.substring(0, Math.min(10, s.length()));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int[] ints(String text, int n) {
        String[] parts = text.split(",");
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }
}
