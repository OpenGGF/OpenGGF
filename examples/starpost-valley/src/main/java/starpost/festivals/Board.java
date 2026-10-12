package starpost.festivals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import starpost.core.Calendar;
import starpost.core.CropDef;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.core.Skills;
import starpost.people.Line;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;

/**
 * The Signpost Board's requests (design doc §6.8), as rules. Each morning old notes come down
 * and a villager pins a new one; Mondays bring a bigger weekly job and, once Robomart is open,
 * Robotnik's special order. Up to {@value #MAX_ACTIVE} can be taken on at once. A delivery is
 * finished by talking to the villager with the goods in hand; popping badniks finishes itself.
 *
 * <p>Requests only ask for what can be had now: crops growing this season, the season's forage,
 * building materials, and minerals or fish when those systems are installed. Engine-free; choices
 * use a hash of the day, never the game's random numbers.
 */
public final class Board {
    public static final int MAX_POSTED = 4;
    public static final int MAX_ACTIVE = 3;
    /** A daily note runs today and tomorrow; a weekly one and Robotnik's order run seven days. */
    public static final int DAILY_DAYS = 2;
    public static final int WEEKLY_DAYS = 7;
    /** Robomart opens on Spring 5 (day number 4); Robotnik's orders start the Monday after. */
    public static final int ROBOMART_OPENS = 4;
    /** Robotnik's special order: what he pays per item, as a multiple of its price, mostly in rings. */
    public static final int SPECIAL_MULTIPLIER = 4;
    public static final int SPECIAL_COLA = 12;
    /** The catch: Dandel sees the Egg Truck at the gate. */
    public static final int SPECIAL_DANDEL_PENALTY = 150;

    final List<Request> posted = new ArrayList<>();
    final List<Request> active = new ArrayList<>();
    int nextId = 1;
    int completed;
    int specialOrders;

    public List<Request> posted() {
        return Collections.unmodifiableList(posted);
    }

    public List<Request> active() {
        return Collections.unmodifiableList(active);
    }

    public int completed() {
        return completed;
    }

    /** How many of Robotnik's special orders the farmer has filled. */
    public int specialOrders() {
        return specialOrders;
    }

    // ------------------------------------------------------------------ the morning

    /**
     * The morning's board: notes past their day come down (taken-on ones too, with a notice),
     * and new ones go up. Returns notices for the screen.
     */
    public List<String> morning(Game game, People people) {
        List<String> notices = new ArrayList<>();
        int today = game.calendar.dayNumber();
        posted.removeIf(r -> r.due < today);
        for (Request r : new ArrayList<>(active)) {
            if (r.due < today) {
                active.remove(r);
                notices.add("REQUEST RAN OUT: " + describe(r, game).toUpperCase());
            }
        }
        if (people == null) {
            return notices;
        }
        int before = posted.size();
        if (posted.size() < MAX_POSTED) {
            Request daily = generate(game, people, today, false, 1);
            if (daily != null) {
                posted.add(daily);
            }
        }
        if (game.calendar.weekday() == 0) {
            if (posted.size() < MAX_POSTED) {
                Request weekly = generate(game, people, today, true, 2);
                if (weekly != null) {
                    posted.add(weekly);
                }
            }
            if (today >= ROBOMART_OPENS && !hasSpecial()) {
                Request special = special(game, people, today);
                if (special != null) {
                    posted.add(special);
                }
            }
        }
        if (posted.size() > before) {
            notices.add(posted.size() - before == 1 ? "A NEW REQUEST ON THE SIGNPOST BOARD"
                    : "NEW REQUESTS ON THE SIGNPOST BOARD");
        }
        return notices;
    }

    private boolean hasSpecial() {
        for (Request r : posted) {
            if (r.type == Request.SPECIAL) {
                return true;
            }
        }
        for (Request r : active) {
            if (r.type == Request.SPECIAL) {
                return true;
            }
        }
        return false;
    }

    /** Villagers who post requests: present, not pets, not the totem, and not Robotnik (he has his orders). */
    static List<VillagerDef> posters(Game game, People people) {
        List<VillagerDef> out = new ArrayList<>();
        for (VillagerDef v : people.cast.all()) {
            if (people.present(v, game) && !v.isPet() && !v.body().equals("totem") && !v.id.equals("robotnik")) {
                out.add(v);
            }
        }
        return out;
    }

    /** A daily or weekly request from someone, or null when nobody can post. */
    Request generate(Game game, People people, int today, boolean weekly, int salt) {
        List<VillagerDef> posters = posters(game, people);
        if (posters.isEmpty()) {
            return null;
        }
        VillagerDef v = posters.get(mix(today, salt) % posters.size());
        for (Request r : posted) {
            if (r.villager.equals(v.id)) {
                v = posters.get((mix(today, salt) + 1) % posters.size());   // someone else, if they can
                break;
            }
        }
        int days = weekly ? WEEKLY_DAYS : DAILY_DAYS;
        boolean canPop = game.section(Skills.class) != null;
        if (canPop && mix(today, salt + 10) % 4 == 0) {
            int count = weekly ? 8 + mix(today, salt + 11) % 5 : 3 + mix(today, salt + 11) % 3;
            int rings = round10(count * (weekly ? 70 : 50) + (weekly ? 150 : 50));
            return new Request(nextId++, v.id, Request.POP, null, count, rings, weekly ? 150 : 60, today,
                    today + days - 1);
        }
        String item = pick(game, v, today, salt);
        if (item == null) {
            return null;
        }
        int price = Math.max(1, game.item(item).price());
        int count = Math.max(1, Math.min(weekly ? 30 : 10, (weekly ? 360 : 120) / price));
        int rings = round10(price * count * (weekly ? 2 : 3) / (weekly ? 1 : 2) + (weekly ? 150 : 60));
        return new Request(nextId++, v.id, Request.DELIVER, item, count, rings, weekly ? 150 : 60, today,
                today + days - 1);
    }

    /** Robotnik's special order: the season's commonest crop (or forage) by the cartload, paid handsomely. */
    Request special(Game game, People people, int today) {
        VillagerDef robotnik = people.cast.get("robotnik");
        if (robotnik == null || !people.present(robotnik, game)) {
            return null;
        }
        String item = specialItem(game.calendar.season());
        if (!game.catalog.hasItem(item)) {
            return null;
        }
        int price = Math.max(1, game.item(item).price());
        int count = specialCount(game.calendar.season());
        int rings = round10(price * count * SPECIAL_MULTIPLIER);
        return new Request(nextId++, "robotnik", Request.SPECIAL, item, count, rings, 100, today,
                today + WEEKLY_DAYS - 1);
    }

    static String specialItem(int season) {
        return switch (season) {
            case Calendar.SPRING -> "ring_radish";
            case Calendar.SUMMER -> "motobug_tomato";
            case Calendar.FALL -> "egg_plant";
            default -> "snow_spud";
        };
    }

    static int specialCount(int season) {
        return season == Calendar.WINTER ? 8 : 20;
    }

    /** What the note says Robotnik wants: always a hundred times too many. */
    public static String specialHeadline(Request r, Game game) {
        return r.count * 25 + " " + plural(game.item(r.item).name()) + ". NO QUESTIONS.";
    }

    /** Something this villager would want and the farmer could get now, or null. */
    String pick(Game game, VillagerDef v, int today, int salt) {
        List<String> pool = pool(game);
        if (pool.isEmpty()) {
            return null;
        }
        List<String> favourites = new ArrayList<>();
        for (String id : pool) {
            Taste taste = v.taste(game.item(id));
            if (taste == Taste.LOVE || taste == Taste.LIKE) {
                favourites.add(id);
            }
        }
        List<String> from = !favourites.isEmpty() && mix(today, salt + 20) % 3 != 0 ? favourites : pool;
        return from.get(mix(today, salt + 21) % from.size());
    }

    /** Items that can be had this season: crops growing now, the season's forage, materials, minerals, fish. */
    static List<String> pool(Game game) {
        List<String> out = new ArrayList<>();
        int season = game.calendar.season();
        for (CropDef crop : game.catalog.crops()) {
            if (crop.grows(season) && game.catalog.seedPrice(crop.seed()) > 0) {
                out.add(crop.produce());
            }
        }
        for (String id : forage(season)) {
            if (game.catalog.hasItem(id)) {
                out.add(id);
            }
        }
        for (String id : new String[] {"palm_wood", "marble_chip", "fibre", "scrap"}) {
            if (game.catalog.hasItem(id)) {
                out.add(id);
            }
        }
        for (Item item : game.catalog.items()) {
            if ((item.kind() == Kind.MINERAL || item.kind() == Kind.FISH) && item.price() > 0) {
                out.add(item.id());
            }
        }
        return out;
    }

    /** The season's forage along the valley path (the same finds as {@code valley.Pickups}). */
    static String[] forage(int season) {
        return switch (season) {
            case Calendar.SPRING -> new String[] {"totem_leek", "hill_daffodil"};
            case Calendar.SUMMER -> new String[] {"loop_berry", "hill_daffodil"};
            case Calendar.FALL -> new String[] {"palm_coconut", "loop_berry"};
            default -> new String[] {"snow_spud", "frost_ring"};
        };
    }

    // ------------------------------------------------------------------ taking them on

    /** Takes a posted note down and on. False when it is not on the board or three are already on. */
    public boolean accept(Request r, Game game) {
        if (!posted.contains(r) || active.size() >= MAX_ACTIVE) {
            return false;
        }
        posted.remove(r);
        r.accepted = true;
        Skills skills = game.section(Skills.class);
        r.base = skills == null ? 0 : skills.xp(Skills.BOPPING);
        active.add(r);
        return true;
    }

    /** Badniks popped toward an accepted popping job. */
    public int progress(Request r, Game game) {
        if (r.type == Request.POP) {
            Skills skills = game.section(Skills.class);
            return Math.min(r.count, skills == null ? 0 : r.popped(skills.xp(Skills.BOPPING)));
        }
        return Math.min(r.count, game.inventory.total(r.item));
    }

    /** Finished popping jobs: paid and taken down. Returns notices. */
    public List<String> checkPops(Game game, People people) {
        List<String> notices = new ArrayList<>();
        for (Request r : new ArrayList<>(active)) {
            if (r.type == Request.POP && progress(r, game) >= r.count) {
                active.remove(r);
                completed++;
                game.rings += r.rings;
                if (people != null && people.cast.get(r.villager) != null) {
                    people.add(r.villager, r.friendship);
                }
                notices.add("REQUEST DONE: +" + r.rings + " RINGS");
            }
        }
        return notices;
    }

    /**
     * Talking to a villager with what they asked for: the goods change hands, rings and
     * friendship are paid, and they say thanks. Null when there is nothing to hand over.
     */
    public Line deliver(VillagerDef v, Game game, People people) {
        for (Request r : new ArrayList<>(active)) {
            if (!r.delivery() || !r.villager.equals(v.id) || !game.catalog.hasItem(r.item)
                    || game.inventory.total(r.item) < r.count) {
                continue;
            }
            game.inventory.remove(r.item, r.count);
            active.remove(r);
            completed++;
            if (r.type == Request.SPECIAL) {
                return special(r, game, people);
            }
            game.rings += r.rings;
            if (people != null) {
                people.add(v.id, r.friendship);
            }
            return new Line(thanks(r, game, v));
        }
        return null;
    }

    /** Robotnik pays in rings and the rest in Robo Cola; Dandel saw the Egg Truck at the gate. */
    private Line special(Request r, Game game, People people) {
        specialOrders++;
        game.rings += r.rings;
        if (game.catalog.hasItem("robo_cola")) {
            game.inventory.add(game.item("robo_cola"), SPECIAL_COLA);
        }
        game.flags.add("egg_supplier");
        if (people != null) {
            people.add("robotnik", r.friendship);
            if (people.cast.get("dandel") != null) {
                people.add("dandel", -SPECIAL_DANDEL_PENALTY);
            }
        }
        return new Line("EXCELLENT. THE EGG TRUCK WILL COLLECT THEM. " + r.rings
                + " RINGS, AND THE BALANCE IN ROBO COLA. DO NOT TELL THE RABBIT.");
    }

    private static String thanks(Request r, Game game, VillagerDef v) {
        String what = r.count + " " + (r.count == 1 ? game.item(r.item).name() : plural(game.item(r.item).name()));
        return switch (mix(r.id, v.id.length()) % 4) {
            case 0 -> what + "! JUST WHAT I NEEDED. THANK YOU, {FARMER}! " + r.rings + " RINGS, AS PROMISED.";
            case 1 -> "YOU BROUGHT THE " + what + "! HERE: " + r.rings + " RINGS. THE BOARD WORKS!";
            case 2 -> "OH! " + what + ", ALL HERE. YOU'RE A STAR. " + r.rings + " RINGS FOR YOU.";
            default -> "THE " + what + "! I'LL PUT THEM TO GOOD USE. " + r.rings + " RINGS, AND MY THANKS.";
        };
    }

    // ------------------------------------------------------------------ words

    /** "5 RING RADISHES FOR DANDEL" style summary (without the villager). */
    public static String describe(Request r, Game game) {
        if (r.type == Request.POP) {
            return "POP " + r.count + " BADNIKS";
        }
        if (!game.catalog.hasItem(r.item)) {
            return "?";
        }
        String name = game.item(r.item).name();
        return "BRING " + r.count + " " + (r.count == 1 ? name : plural(name));
    }

    static String plural(String name) {
        if (name.endsWith("S") || name.endsWith("FIBRE") || name.endsWith("WOOD") || name.endsWith("SCRAP")
                || name.endsWith("CORN") || name.endsWith("HOPS")) {
            return name;
        }
        if (name.endsWith("Y") && !name.endsWith("EY")) {
            return name.substring(0, name.length() - 1) + "IES";
        }
        if (name.endsWith("H") || name.endsWith("O")) {
            return name + "ES";
        }
        return name + "S";
    }

    private static int round10(int value) {
        return Math.max(10, (value + 5) / 10 * 10);
    }

    static int mix(int day, int salt) {
        int h = day * 0x9E3779B1 + salt * 0x7F4A7C15 + 0x2545F491;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h & 0x7FFFFFFF;
    }

    // ------------------------------------------------------------------ saving

    void save(Map<String, String> out) {
        out.put("board.next", Integer.toString(nextId));
        out.put("board.done", Integer.toString(completed));
        out.put("board.special", Integer.toString(specialOrders));
        List<Request> all = new ArrayList<>(posted);
        all.addAll(active);
        for (Request r : all) {
            out.put("req." + r.id, String.join(",", r.villager, Integer.toString(r.type), r.item == null ? "-" : r.item,
                    Integer.toString(r.count), Integer.toString(r.rings), Integer.toString(r.friendship),
                    Integer.toString(r.posted), Integer.toString(r.due), r.accepted ? "1" : "0",
                    Integer.toString(r.base)));
        }
    }

    /** Restores the board; malformed entries throw (the save is rejected), unknown items are dropped. */
    void load(Map<String, String> in, starpost.core.Catalog catalog) {
        posted.clear();
        active.clear();
        nextId = Math.max(1, Integer.parseInt(in.getOrDefault("board.next", "1")));
        completed = Math.max(0, Integer.parseInt(in.getOrDefault("board.done", "0")));
        specialOrders = Math.max(0, Integer.parseInt(in.getOrDefault("board.special", "0")));
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (!e.getKey().startsWith("req.")) {
                continue;
            }
            int id = Integer.parseInt(e.getKey().substring(4));
            String[] p = e.getValue().split(",");
            if (p.length != 10) {
                throw new IllegalArgumentException("Damaged request " + id);
            }
            int type = Integer.parseInt(p[1]);
            if (type < Request.DELIVER || type > Request.SPECIAL || p[0].isBlank()) {
                throw new IllegalArgumentException("Damaged request " + id);
            }
            String item = p[2].equals("-") ? null : p[2];
            int count = Integer.parseInt(p[3]), rings = Integer.parseInt(p[4]), friendship = Integer.parseInt(p[5]);
            int postedDay = Integer.parseInt(p[6]), due = Integer.parseInt(p[7]), base = Integer.parseInt(p[9]);
            boolean accepted = switch (p[8]) {
                case "1" -> true;
                case "0" -> false;
                default -> throw new IllegalArgumentException("Damaged request " + id);
            };
            if (type != Request.POP && (item == null || !catalog.hasItem(item))) {
                continue;     // an item from a system no longer installed
            }
            Request r = new Request(id, p[0], type, type == Request.POP ? null : item, Math.max(1, Math.min(999, count)),
                    Math.max(0, Math.min(1_000_000, rings)), Math.max(0, Math.min(1000, friendship)),
                    Math.max(0, postedDay), Math.max(0, due));
            r.accepted = accepted;
            r.base = Math.max(0, base);
            nextId = Math.max(nextId, id + 1);
            if (accepted && active.size() < MAX_ACTIVE) {
                active.add(r);
            } else if (!accepted && posted.size() < MAX_POSTED) {
                posted.add(r);
            }
        }
    }
}
