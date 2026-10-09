package starpost.people;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * One neighbour: who they are, how they look (a ROM sprite), where they live and go, what they
 * like, and everything they say. Built fluently by the cast classes in {@code starpost.people.cast}:
 *
 * <pre>
 * VillagerDef v = cast.villager("dandel", "DANDEL").body("animal:pocky").home("seed_stall")
 *         .birthday(SPRING, 15).animal().loves("ring_radish");
 * v.line("FRESH SEEDS! PLEASE BUY SOMETHING.").spring();
 * </pre>
 */
public final class VillagerDef {
    public final String id;
    public final String name;
    private String about = "";
    private String body = "animal:flicky";
    private String home = "plaza";
    private int birthdaySeason;
    private int birthdayDay = 1;
    private boolean animal;
    private boolean hero;
    private boolean pet;
    private int maxHearts = 8;
    private String perk = "";
    private int arrivalDay;       // day number of arrival (0: from the start)
    private final Map<String, Taste> itemTastes = new LinkedHashMap<>();
    private final Map<Kind, Taste> kindTastes = new EnumMap<>(Kind.class);
    private final Schedule schedule = new Schedule();
    private final List<Line> lines = new ArrayList<>();
    private final Map<Taste, List<Line>> giftLines = new EnumMap<>(Taste.class);
    private final List<Line> birthdayGiftLines = new ArrayList<>();
    private final List<Line> againLines = new ArrayList<>();
    private final List<Line> thanksLines = new ArrayList<>();

    VillagerDef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // ------------------------------------------------------------------ building

    /** One line for the social page. */
    public VillagerDef about(String text) {
        about = text;
        return this;
    }

    /**
     * The sprite: {@code hero:sonic|tails|knuckles} (S3K), {@code robotnik} (Sonic 1's on-foot
     * Eggman), {@code eggrobo} (S3K), {@code animal:NAME} (Sonic 1's freed animals),
     * {@code totem} (Green Hill's totem pole), {@code motobug}.
     */
    public VillagerDef body(String spriteKey) {
        body = spriteKey;
        return this;
    }

    public VillagerDef home(String anchor) {
        home = anchor;
        return this;
    }

    public VillagerDef birthday(int season, int day) {
        birthdaySeason = season;
        birthdayDay = day;
        return this;
    }

    /** Speaks in pictures until the Chirp Translator. */
    public VillagerDef animal() {
        animal = true;
        return this;
    }

    /** One of the three heroes: absent when they are the farmer. */
    public VillagerDef hero() {
        hero = true;
        return this;
    }

    /** The farm's pet: lives on the farm, is petted rather than talked to. */
    public VillagerDef pet() {
        pet = true;
        return this;
    }

    /** Can become a Partner at 10 hearts, with a daily perk. */
    public VillagerDef partner(String perkText) {
        maxHearts = 10;
        perk = perkText;
        return this;
    }

    /** Moves into the valley on this day number (days since the first morning, from 0). */
    public VillagerDef arrives(int dayNumber) {
        arrivalDay = dayNumber;
        return this;
    }

    public VillagerDef loves(String... ids) {
        return taste(Taste.LOVE, ids);
    }

    public VillagerDef likes(String... ids) {
        return taste(Taste.LIKE, ids);
    }

    public VillagerDef dislikes(String... ids) {
        return taste(Taste.DISLIKE, ids);
    }

    public VillagerDef hates(String... ids) {
        return taste(Taste.HATE, ids);
    }

    /** A taste for every item of a kind (item tastes override it). */
    public VillagerDef kind(Kind kind, Taste taste) {
        kindTastes.put(kind, taste);
        return this;
    }

    private VillagerDef taste(Taste taste, String... ids) {
        for (String item : ids) {
            itemTastes.put(item, taste);
        }
        return this;
    }

    public Schedule.Plan plan() {
        return schedule.plan();
    }

    /** A daily line. */
    public Line line(String text) {
        Line line = new Line(text);
        lines.add(line);
        return line;
    }

    /** How they react to a gift of a taste. */
    public Line gift(Taste taste, String text) {
        Line line = new Line(text);
        giftLines.computeIfAbsent(taste, t -> new ArrayList<>()).add(line);
        return line;
    }

    /** How they react to any gift on their birthday (loved or not, it's the thought). */
    public Line birthdayGift(String text) {
        Line line = new Line(text);
        birthdayGiftLines.add(line);
        return line;
    }

    /** What they say when you talk to them again the same day. */
    public Line again(String text) {
        Line line = new Line(text);
        againLines.add(line);
        return line;
    }

    /** A thank-you note they mail the morning after a gift they loved. */
    public Line thanks(String text) {
        Line line = new Line(text);
        thanksLines.add(line);
        return line;
    }

    // ------------------------------------------------------------------ queries

    public String about() {
        return about;
    }

    public String body() {
        return body;
    }

    public String home() {
        return home;
    }

    public int birthdaySeason() {
        return birthdaySeason;
    }

    public int birthdayDay() {
        return birthdayDay;
    }

    public boolean isAnimal() {
        return animal;
    }

    public boolean isHero() {
        return hero;
    }

    public boolean isPet() {
        return pet;
    }

    public int maxHearts() {
        return maxHearts;
    }

    public boolean canPartner() {
        return maxHearts == 10;
    }

    public String perk() {
        return perk;
    }

    public int arrivalDay() {
        return arrivalDay;
    }

    public Schedule schedule() {
        return schedule;
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    public List<Line> giftLines(Taste taste) {
        return Collections.unmodifiableList(giftLines.getOrDefault(taste, List.of()));
    }

    public List<Line> birthdayGiftLines() {
        return Collections.unmodifiableList(birthdayGiftLines);
    }

    public List<Line> againLines() {
        return Collections.unmodifiableList(againLines);
    }

    public List<Line> thanksLines() {
        return Collections.unmodifiableList(thanksLines);
    }

    /** How the villager feels about an item: its own entry, else its kind's, else neutral. */
    public Taste taste(Item item) {
        Taste own = itemTastes.get(item.id());
        if (own != null) {
            return own;
        }
        return kindTastes.getOrDefault(item.kind(), Taste.NEUTRAL);
    }

    /** The item ids they love, in the order written (some may belong to systems not built yet). */
    public List<String> lovedItems() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Taste> e : itemTastes.entrySet()) {
            if (e.getValue() == Taste.LOVE) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public Map<String, Taste> itemTastes() {
        return Collections.unmodifiableMap(itemTastes);
    }
}
