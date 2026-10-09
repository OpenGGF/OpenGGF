package starpost.festivals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveSection;
import starpost.people.Line;
import starpost.people.People;
import starpost.people.Spot;
import starpost.people.VillagerDef;

/**
 * Festivals and the Signpost Board as saved state (design doc §8, §6.8 and §18): each
 * festival's result by year, the best scores, the prizes already given, trophies, this year's
 * secret friend for the Star Light Feast, and the board's requests. Engine-free.
 *
 * <p>On a festival day it gathers the valley where the festival is held (a
 * {@link People.Gathering}: villagers leave their schedules from half an hour before it opens
 * until it closes), and it finishes the board's deliveries when the farmer talks to whoever
 * asked (a {@link People.Errands}).
 */
public final class Festivals implements SaveSection, People.Gathering, People.Errands {
    public static final String PREFIX = "festivals";
    public static final int VERSION = 1;
    /** Villagers gather this long before the doors open. */
    public static final int GATHER_EARLY = 30;
    /** The Star Light Feast's secret friend is drawn by mail on Winter 18. */
    public static final int SECRET_DAY = 18;
    public static final String SECRET_FLAG = "feast_secret_";

    public final FestivalBook book = new FestivalBook();
    public final Board board = new Board();
    /** Results: "festival@year" to place (1 won; 0 joined, nothing ranked). */
    private final Map<String, Integer> places = new LinkedHashMap<>();
    /** Each festival's best score ever (rings found, seconds, points: the festival says). */
    private final Map<String, Integer> best = new LinkedHashMap<>();
    /** Each timed festival's best time in ticks (lower is better). */
    private final Map<String, Integer> times = new LinkedHashMap<>();
    /** One-time prizes already given. */
    private final Set<String> prizes = new LinkedHashSet<>();
    /** Prizes owed for want of room, collected at the Signpost Board: item id to count. */
    private final Map<String, Integer> owed = new LinkedHashMap<>();
    private String secretFriend;
    private int secretYear;
    /** The day number the board was last refreshed (idempotent mornings). */
    private int boardDay = -1;
    /**
     * The Waters lane's fishing contest for the Ice Cap Festival, when fishing is installed (not
     * saved). Without it the festival's contest is the snowboard run.
     */
    public FishingContest fishingContest;
    /** While the Ice Cap Festival's contest is out on the lake, where its points go (not saved; debug captures end it early). */
    java.util.function.IntConsumer contestAway;

    @Override
    public String prefix() {
        return PREFIX;
    }

    // ------------------------------------------------------------------ results and prizes

    public boolean joined(String festival, int year) {
        return places.containsKey(festival + "@" + year);
    }

    /** The place earned in a year (1 won), or -1 when not joined. */
    public int place(String festival, int year) {
        return places.getOrDefault(festival + "@" + year, -1);
    }

    /** Whether the farmer has ever won it. */
    public boolean everWon(String festival) {
        for (Map.Entry<String, Integer> e : places.entrySet()) {
            if (e.getKey().startsWith(festival + "@") && e.getValue() == 1) {
                return true;
            }
        }
        return false;
    }

    public int best(String festival) {
        return best.getOrDefault(festival, 0);
    }

    /** Records a festival's result; returns true when the score is a new best. */
    public boolean record(String festival, int year, int place, int score) {
        places.put(festival + "@" + year, Math.max(0, place));
        return recordBest(festival, score);
    }

    /**
     * Keeps a best score under a festival's id, or under {@code "id:event"} for a second contest
     * at the same festival (the Ice Cap Festival's fishing); returns true when it is a new best.
     */
    public boolean recordBest(String key, int score) {
        if (score > best.getOrDefault(key, 0)) {
            best.put(key, score);
            return true;
        }
        return false;
    }

    /** Whether a best-score key names a festival in the book (its id, or "id:event"). */
    private boolean knownBest(String key) {
        int colon = key.indexOf(':');
        return book.get(colon < 0 ? key : key.substring(0, colon)) != null && (colon < 0 || colon < key.length() - 1);
    }

    /** The best time in ticks, or 0 when there is none. */
    public int bestTime(String festival) {
        return times.getOrDefault(festival, 0);
    }

    /** Records a time; returns true when it is a new best. */
    public boolean recordTime(String festival, int ticks) {
        Integer old = times.get(festival);
        if (ticks > 0 && (old == null || ticks < old)) {
            times.put(festival, ticks);
            return true;
        }
        return false;
    }

    public boolean prizeTaken(String prize) {
        return prizes.contains(prize);
    }

    /** Marks a one-time prize as given; false when it already was. */
    public boolean takePrize(String prize) {
        return prizes.add(prize);
    }

    /** Puts a prize aside at the board until there is room for it. */
    void owe(String item, int count) {
        owed.merge(item, count, Integer::sum);
    }

    public Map<String, Integer> owed() {
        return java.util.Collections.unmodifiableMap(owed);
    }

    /** Hands over what is owed as far as the monitors have room; returns the names handed over. */
    public List<String> collect(Game game) {
        List<String> out = new ArrayList<>();
        for (String id : new ArrayList<>(owed.keySet())) {
            if (!game.catalog.hasItem(id)) {
                owed.remove(id);
                continue;
            }
            int count = owed.get(id);
            int left = game.inventory.add(game.item(id), count);
            if (left < count) {
                out.add(game.item(id).name());
            }
            if (left == 0) {
                owed.remove(id);
            } else {
                owed.put(id, left);
            }
        }
        return out;
    }

    /**
     * A festival's result in a year for the board's records page: "WON", "2ND"..., "JOINED" (no
     * place given, or a festival without winners), or "-" when not joined.
     */
    public String resultLine(String festival, int year) {
        int place = place(festival, year);
        if (place < 0) {
            return "-";
        }
        if (place == 0 || !hasTrophy(festival)) {
            return "JOINED";
        }
        return place == 1 ? "WON" : ordinal(place);
    }

    /**
     * A festival's best ever in its own terms, for the records page, or "" before it has one:
     * rings found, the judge's points, a time (the race's and the maze's way out), flocks waved
     * to, and the Ice Cap Festival's run and catch apart.
     */
    public String bestLine(String festival) {
        int score = best(festival), time = bestTime(festival);
        return switch (festival) {
            case FestivalBook.RING_HUNT -> score > 0 ? score + " RINGS" : "";
            case FestivalBook.PARADE, FestivalBook.FAIR -> score > 0 ? score + " POINTS" : "";
            case FestivalBook.RACE -> time > 0 ? seconds(time) + " SECONDS" : "";
            case FestivalBook.FLICKIES -> score > 0 ? score + (score == 1 ? " FLOCK WAVED BACK" : " FLOCKS WAVED BACK")
                    : "";
            case FestivalBook.SCRAP_BRAIN -> time > 0 ? "OUT IN " + watch(time)
                    : score > 0 ? score + " RINGS" : "";
            case FestivalBook.ICE_CAP -> {
                int fishing = best(Snowboard.FISHING_BEST);
                String run = score > 0 ? "RUN " + score : "";
                String fish = fishing > 0 ? "CATCH " + fishing : "";
                yield run.isEmpty() || fish.isEmpty() ? run + fish : run + ", " + fish;
            }
            default -> "";
        };
    }

    /** A stopwatch from ticks: "0:42" (minutes and seconds). */
    static String watch(int ticks) {
        int s = Math.max(0, ticks / 60);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    /** "1ST", "2ND"... */
    static String ordinal(int place) {
        return place + switch (place) {
            case 1 -> "ST";
            case 2 -> "ND";
            case 3 -> "RD";
            default -> "TH";
        };
    }

    /** Seconds and tenths from ticks: "42.5". */
    static String seconds(int ticks) {
        int tenths = ticks / 6;
        return tenths / 10 + "." + tenths % 10;
    }

    /** Trophies won, in the book's order (the shelf beside the board shows them). */
    public List<String> trophies() {
        List<String> out = new ArrayList<>();
        for (Festival f : book.all()) {
            if (hasTrophy(f.id) && prizes.contains("trophy." + f.id)) {
                out.add(f.id);
            }
        }
        return out;
    }

    /**
     * Whether a festival is won for a trophy: the six contests. The Night of the Flickies and the
     * Star Light Feast have no winner (the migration and the gifts are their rewards).
     */
    public static boolean hasTrophy(String festival) {
        return switch (festival) {
            case FestivalBook.FLICKIES, FestivalBook.FEAST -> false;
            default -> true;
        };
    }

    // ------------------------------------------------------------------ the day

    /** Today's festival, or null. */
    public Festival today(Game game) {
        return book.today(game.calendar);
    }

    /** Whether the valley is gathered for today's festival right now. */
    public boolean gathered(Festival f, Game game) {
        int m = game.calendar.minutes();
        return f != null && f.on(game.calendar) && m >= f.open - GATHER_EARLY && m < f.close;
    }

    /**
     * Where the valley stands for a festival: the host by the festival's sign, everyone else in a
     * crowd either side of it. The totem stays on its hill, pets on the farm.
     */
    @Override
    public Spot spot(VillagerDef v, Game game) {
        Festival f = today(game);
        if (!gathered(f, game) || v.isPet() || v.body().equals("totem")) {
            return null;
        }
        People people = game.section(People.class);
        if (people == null || !people.present(v, game)) {
            return null;
        }
        if (v.id.equals(host(f, game))) {
            return Spot.valley(f.anchor, -18);
        }
        int index = 0;
        for (VillagerDef other : people.cast.all()) {
            if (other == v) {
                break;
            }
            if (people.present(other, game) && !other.isPet() && !other.body().equals("totem")
                    && !other.id.equals(host(f, game))) {
                index++;
            }
        }
        return Spot.valley(f.anchor, crowdOffset(index));
    }

    /** Alternating either side of the sign, further out each pair: 46, -64, 82, -100... */
    static int crowdOffset(int index) {
        int ring = index / 2;
        return (index % 2 == 0 ? 1 : -1) * (46 + ring * 36) + (index % 2 == 0 ? 0 : -18);
    }

    /** Who hosts a festival (stands by its sign and opens it). A farming hero hands over to another. */
    public static String host(Festival f, Game game) {
        String host = switch (f.id) {
            case FestivalBook.RING_HUNT, FestivalBook.RACE -> "tails";
            case FestivalBook.PARADE -> "dandel";
            case FestivalBook.FLICKIES -> "pip";
            case FestivalBook.FAIR, FestivalBook.SCRAP_BRAIN -> "robotnik";
            case FestivalBook.ICE_CAP -> "frost";
            default -> "clementine";
        };
        return host.equals(game.farmer) ? "sonic" : host;
    }

    /**
     * The morning card's line on a festival day (and its eve), or null. One short line (the card
     * centres it on a 400-pixel screen): where it is held is on the board and at the door.
     */
    public String morningNote(Game game) {
        Festival f = today(game);
        if (f != null) {
            return f.name + " TODAY AT " + Festival.clock(f.open);
        }
        Festival eve = book.tomorrow(game.calendar);
        return eve == null ? null : "TOMORROW: THE " + eve.name;
    }

    // ------------------------------------------------------------------ the Star Light Feast's secret friend

    /** This year's secret friend, or null before the letter (Winter 18). */
    public String secretFriend(Game game) {
        return secretYear == game.calendar.year() ? secretFriend : null;
    }

    /**
     * Draws the year's secret friend: someone in the valley who can be given a present (not a
     * pet, not the totem), the same for a given year and farm. Returns the villager id or null.
     */
    static String drawSecretFriend(Game game, People people) {
        List<VillagerDef> pool = new ArrayList<>();
        for (VillagerDef v : people.cast.all()) {
            if (people.present(v, game) && !v.isPet() && !v.body().equals("totem")) {
                pool.add(v);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        int roll = Board.mix(game.calendar.year() * 977 + game.farmName.hashCode(), 31);
        return pool.get(roll % pool.size()).id;
    }

    /** Debug: sets this year's secret friend. */
    void debugSecret(String villager, int year) {
        secretFriend = villager;
        secretYear = year;
    }

    /** Who gives the farmer their present at the feast: someone else in the valley, or null. */
    public String secretGiver(Game game) {
        People people = game.section(People.class);
        String friend = secretFriend(game);
        if (people == null || friend == null) {
            return null;
        }
        List<VillagerDef> pool = new ArrayList<>();
        for (VillagerDef v : people.cast.all()) {
            if (people.present(v, game) && !v.isPet() && !v.body().equals("totem") && !v.id.equals(friend)) {
                pool.add(v);
            }
        }
        return pool.isEmpty() ? null : pool.get(Board.mix(game.calendar.year(), 37) % pool.size()).id;
    }

    // ------------------------------------------------------------------ overnight and morning

    /**
     * Overnight (the calendar is already on the new day): on Winter 18 the secret friend is drawn
     * and their letter's flag set, so the Flicky post brings it in the morning.
     */
    @Override
    public void nextDay(Game game) {
        Calendar c = game.calendar;
        People people = game.section(People.class);
        if (c.season() == Calendar.WINTER && c.day() == SECRET_DAY && secretYear != c.year() && people != null) {
            String friend = drawSecretFriend(game, people);
            if (friend != null) {
                secretFriend = friend;
                secretYear = c.year();
                game.flags.add(SECRET_FLAG + friend);
            }
        }
    }

    /** The board's morning (idempotent per day): returns notices. */
    public List<String> morning(Game game) {
        int today = game.calendar.dayNumber();
        if (boardDay == today) {
            return new ArrayList<>();
        }
        boardDay = today;
        return board.morning(game, game.section(People.class));
    }

    /** Delivering a board request by talking to whoever asked. */
    @Override
    public Line errand(VillagerDef villager, Game game) {
        return board.deliver(villager, game, game.section(People.class));
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void save(Map<String, String> out) {
        out.put("version", Integer.toString(VERSION));
        out.put("boardday", Integer.toString(boardDay));
        for (Map.Entry<String, Integer> e : places.entrySet()) {
            out.put("place." + e.getKey(), Integer.toString(e.getValue()));
        }
        for (Map.Entry<String, Integer> e : best.entrySet()) {
            out.put("best." + e.getKey(), Integer.toString(e.getValue()));
        }
        for (Map.Entry<String, Integer> e : times.entrySet()) {
            out.put("time." + e.getKey(), Integer.toString(e.getValue()));
        }
        if (!prizes.isEmpty()) {
            out.put("prizes", String.join(",", prizes));
        }
        if (secretFriend != null) {
            out.put("secret", secretFriend + "," + secretYear);
        }
        for (Map.Entry<String, Integer> e : owed.entrySet()) {
            out.put("owed." + e.getKey(), Integer.toString(e.getValue()));
        }
        board.save(out);
    }

    /**
     * Restores the section. A wrong version, a malformed number or a damaged request rejects the
     * save (it throws and the codec reports the file unreadable); unknown festivals and items are
     * dropped and numbers clamped.
     */
    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        places.clear();
        best.clear();
        times.clear();
        prizes.clear();
        owed.clear();
        secretFriend = null;
        secretYear = 0;
        boardDay = -1;
        board.load(Map.of(), catalog);
        if (in.isEmpty()) {
            return;
        }
        if (Integer.parseInt(in.getOrDefault("version", "0")) != VERSION) {
            throw new IllegalArgumentException("Unknown festivals save version");
        }
        boardDay = Math.max(-1, Integer.parseInt(in.getOrDefault("boardday", "-1")));
        for (Map.Entry<String, String> e : in.entrySet()) {
            String key = e.getKey(), value = e.getValue();
            if (key.startsWith("place.")) {
                String name = key.substring(6);
                int at = name.indexOf('@');
                int place = Integer.parseInt(value.trim());
                int year = at < 0 ? -1 : Integer.parseInt(name.substring(at + 1));
                if (at > 0 && book.get(name.substring(0, at)) != null && year >= 1) {
                    places.put(name, Math.max(0, Math.min(8, place)));
                }
            } else if (key.startsWith("best.")) {
                int score = Integer.parseInt(value.trim());
                if (knownBest(key.substring(5))) {
                    best.put(key.substring(5), Math.max(0, score));
                }
            } else if (key.equals("prizes")) {
                for (String prize : value.split(",")) {
                    if (!prize.isBlank()) {
                        prizes.add(prize.trim());
                    }
                }
            } else if (key.startsWith("time.")) {
                int ticks = Integer.parseInt(value.trim());
                if (book.get(key.substring(5)) != null && ticks > 0) {
                    times.put(key.substring(5), ticks);
                }
            } else if (key.startsWith("owed.")) {
                int count = Integer.parseInt(value.trim());
                if (catalog.hasItem(key.substring(5)) && count > 0) {
                    owed.put(key.substring(5), Math.min(999, count));
                }
            } else if (key.equals("secret")) {
                String[] p = value.split(",");
                if (p.length != 2 || p[0].isBlank()) {
                    throw new IllegalArgumentException("Damaged secret friend");
                }
                secretFriend = p[0];
                secretYear = Math.max(0, Integer.parseInt(p[1].trim()));
            }
        }
        board.load(in, catalog);
    }
}
