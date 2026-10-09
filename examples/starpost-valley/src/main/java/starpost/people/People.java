package starpost.people;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.core.SaveSection;

/**
 * The valley's neighbours as rules and saved state (design doc §7 and §15): friendship points
 * (250 a heart, ten hearts; villagers without a Partner arc stop at eight), talking once a day,
 * gifts by taste (one a day, two a week, eight times on a birthday), slow decay without contact,
 * heart events seen, the morning post, and Partners. Engine-free: the screens and actors in this
 * package draw it, and {@code PeopleRulesTest} tests it without a ROM.
 *
 * <p>Story flags (the Chirp Translator, blueprints) are set in the game's own
 * {@link Game#flags}, so other systems can read them.
 */
public final class People implements SaveSection {
    public static final String PREFIX = "people";
    public static final int VERSION = 1;
    public static final int POINTS_PER_HEART = 250;
    public static final int MAX_HEARTS = 10;
    public static final int TALK_POINTS = 20;
    public static final int GIFTS_PER_WEEK = 2;
    public static final int BIRTHDAY_FACTOR = 8;
    public static final int DECAY = 2;
    /** The story flag Tails's 2-heart event sets: animals speak in words from then on. */
    public static final String TRANSLATOR = "chirp_translator";

    /** How a gift went. */
    public enum GiftStatus {
        GIVEN,
        NOT_A_GIFT,
        ALREADY_TODAY,
        WEEK_FULL
    }

    /** A gift's outcome: whether it was taken, the taste, the points, and what they said. */
    public record Gift(GiftStatus status, Taste taste, int points, Line line) {
    }

    /** A conversation's outcome: the line, whether it was the day's first talk, the points. */
    public record Talk(Line line, boolean firstToday, int points) {
    }

    public final Cast cast;
    private final Map<String, Bond> bonds = new LinkedHashMap<>();
    private final Map<String, Integer> seen = new LinkedHashMap<>();
    private final List<String> mailbox = new ArrayList<>();
    private final Set<String> sent = new LinkedHashSet<>();
    private int morningDay = -1;

    public People() {
        this(new Cast());
    }

    public People(Cast cast) {
        this.cast = cast;
        for (VillagerDef v : cast.all()) {
            bonds.put(v.id, new Bond());
        }
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    // ------------------------------------------------------------------ who and how

    public Bond bond(String id) {
        Bond bond = bonds.get(id);
        if (bond == null) {
            throw new IllegalArgumentException("Unknown villager " + id);
        }
        return bond;
    }

    public int hearts(String id) {
        Bond bond = bonds.get(id);
        return bond == null ? 0 : bond.hearts();
    }

    /** Whether a villager lives in the valley now: not the farmer, and arrived. */
    public boolean present(VillagerDef v, Game game) {
        return !(v.isHero() && v.id.equals(game.farmer)) && game.calendar.dayNumber() >= v.arrivalDay();
    }

    public boolean translator(Game game) {
        return game.flags.contains(TRANSLATOR);
    }

    /** Animals speak in pictures until the Chirp Translator. */
    public boolean speaksInPictures(VillagerDef v, Game game) {
        return v.isAnimal() && !translator(game);
    }

    public static boolean birthday(VillagerDef v, Calendar calendar) {
        return v.birthdaySeason() == calendar.season() && v.birthdayDay() == calendar.day();
    }

    /** The most points a villager can reach: ten hearts with a Partner arc, else eight. */
    public static int cap(VillagerDef v) {
        return v.maxHearts() * POINTS_PER_HEART;
    }

    public Situation situation(VillagerDef v, Game game) {
        Calendar c = game.calendar;
        Bond bond = v == null ? null : bonds.get(v.id);
        return new Situation(c.year(), c.season(), c.day(), c.weekday(), c.dayNumber(), c.minutes(), game.raining,
                game.farmer, game.flags, bond == null ? 0 : bond.hearts(), bond != null && bond.met,
                v != null && birthday(v, c), seen);
    }

    /** The villagers met so far, in cast order. */
    public List<VillagerDef> met() {
        List<VillagerDef> out = new ArrayList<>();
        for (VillagerDef v : cast.all()) {
            if (bonds.get(v.id).met) {
                out.add(v);
            }
        }
        return out;
    }

    public boolean seen(String event) {
        return seen.containsKey(event);
    }

    // ------------------------------------------------------------------ talking

    /**
     * Talks to a villager: the day's line (and +{@value #TALK_POINTS} the first time today), or a
     * short "again" line after that.
     */
    public Talk talk(String id, Game game) {
        VillagerDef v = cast.get(id);
        Bond bond = bond(id);
        Situation s = situation(v, game);
        if (bond.talkedToday) {
            Line again = pickAny(v.againLines(), s, mix(id, s.dayNumber(), 7));
            return new Talk(again != null ? again : new Line("..."), false, 0);
        }
        int index = dailyIndex(v, bond, s);
        Line line = index >= 0 ? v.lines().get(index) : new Line("...");
        if (index >= 0 && !line.special()) {
            bond.remember(index);
        }
        bond.talkedToday = true;
        bond.met = true;
        int before = bond.points;
        add(v, bond, TALK_POINTS);
        return new Talk(line, true, bond.points - before);
    }

    /** What the villager would say first today (without talking): the index into their lines, or -1. */
    int dailyIndex(VillagerDef v, Bond bond, Situation s) {
        List<Line> lines = v.lines();
        int bestRank = -1;
        List<Integer> best = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (!line.special() || !line.matches(s)) {
                continue;
            }
            // First-meeting lines always win before the first talk.
            int rank = line.rank();
            if (rank > bestRank) {
                bestRank = rank;
                best.clear();
            }
            if (rank == bestRank) {
                best.add(i);
            }
        }
        if (!best.isEmpty()) {
            return best.get(mix(v.id, s.dayNumber(), 1) % best.size());
        }
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).special() && lines.get(i).matches(s)) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) {
            return -1;
        }
        List<Integer> fresh = new ArrayList<>(pool);
        fresh.removeAll(bond.said);
        if (!fresh.isEmpty()) {
            pool = fresh;
        }
        int total = 0;
        for (int i : pool) {
            total += lines.get(i).weight();
        }
        int roll = mix(v.id, s.dayNumber(), 2) % total;
        for (int i : pool) {
            roll -= lines.get(i).weight();
            if (roll < 0) {
                return i;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ------------------------------------------------------------------ gifts

    public static boolean giftable(Item item) {
        return item != null && item.kind() != Kind.TOOL;
    }

    public static int points(Taste taste) {
        return switch (taste) {
            case LOVE -> 80;
            case LIKE -> 45;
            case NEUTRAL -> 20;
            case DISLIKE -> -20;
            case HATE -> -40;
        };
    }

    /** Whether a gift would be accepted now (without giving it). */
    public GiftStatus canGift(String id, Item item, Game game) {
        VillagerDef v = cast.get(id);
        Bond bond = bond(id);
        if (!giftable(item)) {
            return GiftStatus.NOT_A_GIFT;
        }
        if (bond.giftedToday) {
            return GiftStatus.ALREADY_TODAY;
        }
        if (!birthday(v, game.calendar) && bond.giftsThisWeek >= GIFTS_PER_WEEK) {
            return GiftStatus.WEEK_FULL;
        }
        return GiftStatus.GIVEN;
    }

    /**
     * Gives one of an item from the farmer's monitors: points by taste (times eight on their
     * birthday, which does not count toward the week's two), and their reaction.
     */
    public Gift gift(String id, Item item, Game game) {
        GiftStatus status = canGift(id, item, game);
        if (status != GiftStatus.GIVEN) {
            return new Gift(status, null, 0, null);
        }
        VillagerDef v = cast.get(id);
        Bond bond = bond(id);
        boolean birthday = birthday(v, game.calendar);
        Taste taste = v.taste(item);
        int change = points(taste) * (birthday ? BIRTHDAY_FACTOR : 1);
        game.inventory.remove(item.id(), 1);
        int before = bond.points;
        add(v, bond, change);
        bond.giftedToday = true;
        bond.met = true;
        if (!birthday) {
            bond.giftsThisWeek++;
        }
        if (taste == Taste.LOVE) {
            bond.knownLoves.add(item.id());
        }
        bond.lastGift = item.id();
        bond.lastTaste = taste;
        bond.lastGiftDay = game.calendar.dayNumber();
        Situation s = situation(v, game);
        Line line = birthday ? pickAny(v.birthdayGiftLines(), s, mix(id, s.dayNumber(), 3)) : null;
        if (line == null) {
            line = pickAny(v.giftLines(taste), s, mix(id, s.dayNumber(), 4));
        }
        return new Gift(GiftStatus.GIVEN, taste, bond.points - before, line != null ? line : new Line("..."));
    }

    // ------------------------------------------------------------------ points

    /** Adds (or takes) points, kept between zero and the villager's cap. */
    public void add(String id, int change) {
        add(cast.get(id), bond(id), change);
    }

    private static void add(VillagerDef v, Bond bond, int change) {
        bond.points = Math.max(0, Math.min(cap(v), bond.points + change));
    }

    /** Debug and tests: sets a villager's hearts (and marks them met). */
    public void setHearts(String id, int hearts) {
        VillagerDef v = cast.get(id);
        Bond bond = bond(id);
        bond.points = Math.max(0, Math.min(cap(v), hearts * POINTS_PER_HEART));
        bond.met = true;
    }

    // ------------------------------------------------------------------ overnight and morning

    /**
     * Overnight (the calendar is already on the new day): friendships fade by {@value #DECAY}
     * points without a word that day (Partners live with you and don't fade), the day's talk and
     * gift reset, Monday resets the week's gifts, and a loved gift earns a thank-you note.
     */
    @Override
    public void nextDay(Game game) {
        int today = game.calendar.dayNumber();
        for (VillagerDef v : cast.all()) {
            Bond bond = bonds.get(v.id);
            if (bond.met && !bond.talkedToday && !bond.partner) {
                bond.points = Math.max(0, bond.points - DECAY);
            }
            bond.talkedToday = false;
            bond.giftedToday = false;
            if (game.calendar.weekday() == 0) {
                bond.giftsThisWeek = 0;
            }
            if (bond.lastTaste == Taste.LOVE && bond.lastGiftDay == today - 1 && !v.thanksLines().isEmpty()
                    && present(v, game)) {
                post("thanks:" + v.id);
            }
        }
    }

    /**
     * The morning (the first free moment of a day; idempotent): the day's letters go in the
     * mailbox and Partners' perks apply. Returns the perks' notices for the screen to show.
     */
    public List<String> morning(Game game) {
        List<String> notices = new ArrayList<>();
        int today = game.calendar.dayNumber();
        if (morningDay == today) {
            return notices;
        }
        morningDay = today;
        Situation s = situation(null, game);
        for (Letter letter : cast.letters()) {
            String key = letter.yearly() ? letter.id + "@" + game.calendar.year() : letter.id;
            if (sent.contains(key) || !letter.due(s, this::hearts)) {
                continue;
            }
            VillagerDef from = cast.get(letter.from);
            if (from != null && !present(from, game)) {
                continue;
            }
            sent.add(key);
            post(letter.id);
        }
        int[] tomorrow = tomorrow(game.calendar);
        for (VillagerDef v : cast.all()) {
            if (bonds.get(v.id).met && present(v, game) && v.birthdaySeason() == tomorrow[0]
                    && v.birthdayDay() == tomorrow[1] && !v.isPet()) {
                post("birthday:" + v.id);
            }
        }
        for (VillagerDef v : cast.all()) {
            Bond bond = bonds.get(v.id);
            if (bond.partner && present(v, game)) {
                String notice = perk(v, game);
                if (notice != null) {
                    notices.add(notice);
                }
            }
        }
        return notices;
    }

    private static int[] tomorrow(Calendar c) {
        return c.day() < Calendar.DAYS_PER_SEASON ? new int[] {c.season(), c.day() + 1}
                : new int[] {(c.season() + 1) % 4, 1};
    }

    /** A Partner's daily help (design doc §7). Returns the notice, or null. */
    private static String perk(VillagerDef v, Game game) {
        switch (v.id) {
            case "tails" -> {
                // Tails tunes the Water Shield every morning: ten extra charges today.
                game.waterCharges += 10;
                return "TAILS TUNED YOUR WATER SHIELD: +10 WATER";
            }
            case "clementine" -> {
                String meal = game.catalog.hasItem("chili_dog") ? "chili_dog" : null;
                if (meal != null && game.inventory.fits(game.item(meal), 1)) {
                    game.inventory.add(game.item(meal), 1);
                    return "CLEMENTINE LEFT A CHILI DOG IN YOUR MONITOR";
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    public void post(String letterId) {
        if (!mailbox.contains(letterId)) {
            mailbox.add(letterId);
        }
    }

    public List<String> mailbox() {
        return java.util.Collections.unmodifiableList(mailbox);
    }

    /** The next unread letter's id, or null. */
    public String nextLetter() {
        return mailbox.isEmpty() ? null : mailbox.get(0);
    }

    public void read(String letterId) {
        mailbox.remove(letterId);
    }

    // ------------------------------------------------------------------ heart events

    /**
     * The heart event due now, or null: its villager present with enough hearts, not seen, at
     * the right time, in the right view, and the farmer within its radius of its anchor.
     */
    public HeartEvent dueEvent(Game game, boolean onFarm, float farmerX, ToIntFunction<String> anchorX) {
        List<HeartEvent> events = new ArrayList<>(cast.events());
        events.sort(Comparator.comparingInt(e -> e.hearts));
        for (HeartEvent event : events) {
            if (seen.containsKey(event.id) || event.onFarmView() != onFarm) {
                continue;
            }
            VillagerDef v = cast.get(event.villager);
            if (v == null || !present(v, game) || hearts(v.id) < event.hearts) {
                continue;
            }
            if (!event.timely(situation(v, game))) {
                continue;
            }
            if (!onFarm && event.anchor() != null) {
                int x = anchorX.applyAsInt(event.anchor());
                if (x < 0 || Math.abs(farmerX - x) > event.radius()) {
                    continue;
                }
            }
            return event;
        }
        return null;
    }

    /** Marks an event seen (when it starts, so it can never play twice). */
    public void begin(HeartEvent event, Game game) {
        seen.put(event.id, game.calendar.dayNumber());
        bond(event.villager).met = true;
    }

    /** At the end of an event: a Partner event makes them a Partner. */
    public void finish(HeartEvent event) {
        if (event.isPartner()) {
            bond(event.villager).partner = true;
        }
    }

    // ------------------------------------------------------------------ text

    /** Fills in {@code {FARMER}} and {@code {FARM}}. */
    public static String words(String text, Game game) {
        String farmer = switch (game.farmer) {
            case "tails" -> "TAILS";
            case "knuckles" -> "KNUCKLES";
            default -> "SONIC";
        };
        return text.replace("{FARMER}", farmer).replace("{FARM}", game.farmName);
    }

    /** A deterministic pick from the lines that match. */
    static Line pickAny(List<Line> lines, Situation s, int roll) {
        List<Line> pool = new ArrayList<>();
        for (Line line : lines) {
            if (line.matches(s)) {
                pool.add(line);
            }
        }
        return pool.isEmpty() ? null : pool.get(roll % pool.size());
    }

    /** A thank-you note's or a birthday reminder's words. */
    public Line thanksLine(VillagerDef v, Game game) {
        return pickAny(v.thanksLines(), situation(v, game), mix(v.id, game.calendar.dayNumber(), 5));
    }

    /** A stable, non-negative hash of a name, a day and a salt (no game randomness is used). */
    static int mix(String name, int day, int salt) {
        int h = name.hashCode() * 31 + day * 0x9E3779B1 + salt * 0x7F4A7C15;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h & 0x7FFFFFFF;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void save(Map<String, String> out) {
        out.put("version", Integer.toString(VERSION));
        out.put("morning", Integer.toString(morningDay));
        for (VillagerDef v : cast.all()) {
            Bond b = bonds.get(v.id);
            if (!b.met && b.points == 0) {
                continue;
            }
            out.put("bond." + v.id, b.points + "," + bit(b.met) + "," + bit(b.talkedToday) + "," + bit(b.giftedToday)
                    + "," + b.giftsThisWeek + "," + bit(b.partner));
            if (!b.knownLoves.isEmpty()) {
                out.put("loves." + v.id, String.join(",", b.knownLoves));
            }
            if (!b.said.isEmpty()) {
                List<String> said = new ArrayList<>();
                for (int i : b.said) {
                    said.add(Integer.toString(i));
                }
                out.put("said." + v.id, String.join(",", said));
            }
            if (b.lastGift != null) {
                out.put("gift." + v.id, b.lastGift + "," + b.lastTaste.name() + "," + b.lastGiftDay);
            }
        }
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            out.put("seen." + e.getKey(), Integer.toString(e.getValue()));
        }
        if (!mailbox.isEmpty()) {
            out.put("mail", String.join(",", mailbox));
        }
        if (!sent.isEmpty()) {
            out.put("sent", String.join(",", sent));
        }
    }

    /**
     * Restores the section. A wrong version or a malformed number rejects the save (it throws,
     * and the codec reports the file unreadable); unknown villagers, events, letters and items
     * are dropped, and every number is clamped to what the rules allow.
     */
    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        clear();
        if (in.isEmpty()) {
            return;
        }
        if (Integer.parseInt(in.getOrDefault("version", "0")) != VERSION) {
            throw new IllegalArgumentException("Unknown people save version");
        }
        morningDay = Math.max(-1, Integer.parseInt(in.getOrDefault("morning", "-1")));
        for (Map.Entry<String, String> e : in.entrySet()) {
            String key = e.getKey(), value = e.getValue();
            int dot = key.indexOf('.');
            String kind = dot < 0 ? key : key.substring(0, dot), name = dot < 0 ? "" : key.substring(dot + 1);
            switch (kind) {
                case "bond" -> loadBond(name, value);
                case "loves" -> {
                    VillagerDef v = cast.get(name);
                    if (v != null) {
                        for (String item : value.split(",")) {
                            if (catalog.hasItem(item) && v.lovedItems().contains(item)) {
                                bonds.get(name).knownLoves.add(item);
                            }
                        }
                    }
                }
                case "said" -> {
                    VillagerDef v = cast.get(name);
                    if (v != null) {
                        String[] parts = value.split(",");
                        for (int i = parts.length - 1; i >= 0; i--) {
                            int index = Integer.parseInt(parts[i].trim());
                            if (index >= 0 && index < v.lines().size()) {
                                bonds.get(name).remember(index);
                            }
                        }
                    }
                }
                case "gift" -> {
                    String[] parts = value.split(",");
                    Bond b = cast.get(name) == null ? null : bonds.get(name);
                    Taste taste = Taste.valueOf(parts[1]);
                    int day = Integer.parseInt(parts[2]);
                    if (b != null && catalog.hasItem(parts[0])) {
                        b.lastGift = parts[0];
                        b.lastTaste = taste;
                        b.lastGiftDay = day;
                    }
                }
                case "seen" -> {
                    int day = Integer.parseInt(value);
                    if (cast.event(name) != null) {
                        seen.put(name, Math.max(0, day));
                    }
                }
                case "mail" -> {
                    for (String id : value.split(",")) {
                        if (knownLetter(id)) {
                            post(id);
                        }
                    }
                }
                case "sent" -> {
                    for (String entry : value.split(",")) {
                        int at = entry.indexOf('@');
                        String id = at < 0 ? entry : entry.substring(0, at);
                        if (cast.letter(id) != null && (at < 0 || Integer.parseInt(entry.substring(at + 1)) > 0)) {
                            sent.add(entry);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    private void loadBond(String id, String value) {
        String[] p = value.split(",");
        if (p.length != 6) {
            throw new IllegalArgumentException("Damaged bond " + id);
        }
        int points = Integer.parseInt(p[0].trim());
        boolean met = flag(p[1]), talked = flag(p[2]), gifted = flag(p[3]), partner = flag(p[5]);
        int week = Integer.parseInt(p[4].trim());
        VillagerDef v = cast.get(id);
        if (v == null) {
            return;
        }
        Bond b = bonds.get(id);
        b.points = Math.max(0, Math.min(cap(v), points));
        b.met = met;
        b.talkedToday = talked;
        b.giftedToday = gifted;
        b.giftsThisWeek = Math.max(0, Math.min(GIFTS_PER_WEEK, week));
        b.partner = partner && v.canPartner();
    }

    /** Whether a mailbox entry can still be shown: a cast letter, or a note for a known villager. */
    boolean knownLetter(String id) {
        if (cast.letter(id) != null) {
            return true;
        }
        int colon = id.indexOf(':');
        return colon > 0 && (id.startsWith("thanks:") || id.startsWith("birthday:"))
                && cast.get(id.substring(colon + 1)) != null;
    }

    private void clear() {
        for (VillagerDef v : cast.all()) {
            bonds.put(v.id, new Bond());
        }
        seen.clear();
        mailbox.clear();
        sent.clear();
        morningDay = -1;
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }

    private static boolean flag(String text) {
        return switch (text.trim()) {
            case "1" -> true;
            case "0" -> false;
            default -> throw new IllegalArgumentException("Not a flag: " + text);
        };
    }
}
