package typinghog.hub.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import typinghog.save.LaunchIntent;

/**
 * The player's saved progress and options, kept in the mod's storage as {@code profile.txt}:
 * versioned {@code key=value} lines. Engine-free.
 *
 * <p>Robust by construction: unknown keys are kept aside and written back unchanged (a newer
 * hub's fields survive an older one), each line parses on its own so one damaged value costs
 * only that value, and a missing or unreadable file is a fresh profile.
 *
 * <ul>
 *   <li>options: {@code difficulty}, {@code dict.lore}, {@code dict.tricky}, {@code dict.user},
 *       {@code capitals}, {@code punctuation}, {@code noFail}, {@code slowMotion}, {@code voice}
 *       ({@code MEGADRIVE}, {@code CLEAN} or {@code OFF});</li>
 *   <li>story: {@code cleared.<game>} and {@code emeralds.<game>} as {@code zone:act} lists;</li>
 *   <li>Boss Rush: {@code stars.<game>:<zone>:<act>};</li>
 *   <li>records: {@code record.<table>} as up to ten {@code score/grade/wpm} entries, best first;</li>
 *   <li>typing: {@code keys.hits}, {@code keys.misses} ({@link KeyTally} form) and
 *       {@code life.*} totals;</li>
 *   <li>Typing School: {@code grade.<DRILL>} best letter.</li>
 * </ul>
 */
public final class Profile {
    public static final String FILE = "profile.txt";
    /** 2: {@code voice} became MEGADRIVE, CLEAN or OFF (version 1's true/false still reads). */
    public static final int VERSION = 2;
    public static final int RECORD_SLOTS = 10;
    public static final int EMERALDS = 7;

    /** One record table row. */
    public record Entry(long score, String grade, double wpm) {
        String encode() {
            return score + "/" + grade + "/" + String.format(Locale.ROOT, "%.1f", wpm);
        }

        static Entry decode(String text) {
            String[] parts = text.strip().split("/");
            if (parts.length != 3) return null;
            try {
                return new Entry(Long.parseLong(parts[0]), parts[1].isBlank() ? "E" : parts[1], Double.parseDouble(parts[2]));
            } catch (NumberFormatException damaged) {
                return null;
            }
        }
    }

    private LaunchIntent.Difficulty difficulty = LaunchIntent.Difficulty.NORMAL;
    private boolean lore = true;
    private boolean tricky;
    private boolean userWords;
    private boolean capitals;
    private boolean punctuation;
    private boolean noFail;
    private boolean slowMotion;
    private LaunchIntent.Voice voice = LaunchIntent.Voice.MEGADRIVE;
    private long launches;
    private long demos;

    private final Map<String, Set<String>> cleared = new TreeMap<>();
    private final Map<String, Set<String>> emeralds = new TreeMap<>();
    private final Map<String, Integer> stars = new TreeMap<>();
    private final Map<String, List<Entry>> records = new TreeMap<>();
    private final Map<String, String> grades = new TreeMap<>();
    private final KeyTally keyHits = new KeyTally();
    private final KeyTally keyMisses = new KeyTally();
    private long lifeChars;
    private long lifeErrors;
    private long lifeWords;
    private double lifeMinutes;
    private long actsPlayed;
    private long actsCleared;
    private final Map<String, String> unknown = new LinkedHashMap<>();

    // ---- codec ------------------------------------------------------------------------------

    /** Parses saved text; null, blank or damaged text gives a fresh profile. */
    public static Profile decode(String text) {
        Profile p = new Profile();
        if (text == null) return p;
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            try {
                p.read(key, value);
            } catch (RuntimeException damaged) {
                // One damaged value costs only that value.
            }
        }
        return p;
    }

    private void read(String key, String value) {
        switch (key) {
            case "version" -> { }
            case "difficulty" -> difficulty = LaunchIntent.Difficulty.valueOf(value);
            case "dict.lore" -> lore = Boolean.parseBoolean(value);
            case "dict.tricky" -> tricky = Boolean.parseBoolean(value);
            case "dict.user" -> userWords = Boolean.parseBoolean(value);
            case "capitals" -> capitals = Boolean.parseBoolean(value);
            case "punctuation" -> punctuation = Boolean.parseBoolean(value);
            case "noFail" -> noFail = Boolean.parseBoolean(value);
            case "slowMotion" -> slowMotion = Boolean.parseBoolean(value);
            case "voice" -> voice = switch (value) {
                case "true" -> LaunchIntent.Voice.MEGADRIVE;
                case "false" -> LaunchIntent.Voice.OFF;
                default -> LaunchIntent.Voice.parse(value);
            };
            case "launches" -> launches = Math.max(0, Long.parseLong(value));
            case "demos" -> demos = Math.max(0, Long.parseLong(value));
            case "keys.hits" -> keyHits.addEncoded(value);
            case "keys.misses" -> keyMisses.addEncoded(value);
            case "life.chars" -> lifeChars = Math.max(0, Long.parseLong(value));
            case "life.errors" -> lifeErrors = Math.max(0, Long.parseLong(value));
            case "life.words" -> lifeWords = Math.max(0, Long.parseLong(value));
            case "life.minutes" -> lifeMinutes = finite(Double.parseDouble(value));
            case "life.acts" -> actsPlayed = Math.max(0, Long.parseLong(value));
            case "life.cleared" -> actsCleared = Math.max(0, Long.parseLong(value));
            default -> {
                if (key.startsWith("cleared.")) cleared.put(key.substring(8), acts(value));
                else if (key.startsWith("emeralds.")) emeralds.put(key.substring(9), acts(value));
                else if (key.startsWith("stars.")) stars.put(key.substring(6), Math.max(0, Integer.parseInt(value)));
                else if (key.startsWith("record.")) records.put(key.substring(7), entries(value));
                else if (key.startsWith("grade.")) {
                    if (Grades.valid(value)) grades.put(key.substring(6), value);
                } else unknown.put(key, value);
            }
        }
    }

    private static double finite(double v) {
        return Double.isFinite(v) && v >= 0 ? v : 0;
    }

    private static Set<String> acts(String value) {
        Set<String> out = new TreeSet<>();
        for (String part : value.split(",")) {
            String act = part.strip();
            if (act.matches("\\d{1,2}:\\d")) out.add(act);
        }
        return out;
    }

    private static List<Entry> entries(String value) {
        List<Entry> out = new ArrayList<>();
        for (String part : value.split(",")) {
            Entry e = Entry.decode(part);
            if (e != null) out.add(e);
        }
        out.sort(Comparator.comparingLong(Entry::score).reversed());
        return out.size() > RECORD_SLOTS ? new ArrayList<>(out.subList(0, RECORD_SLOTS)) : out;
    }

    public String encode() {
        StringBuilder out = new StringBuilder();
        out.append("# Typing of the Hedgehog profile\n");
        put(out, "version", VERSION);
        put(out, "difficulty", difficulty.name());
        put(out, "dict.lore", lore);
        put(out, "dict.tricky", tricky);
        put(out, "dict.user", userWords);
        put(out, "capitals", capitals);
        put(out, "punctuation", punctuation);
        put(out, "noFail", noFail);
        put(out, "slowMotion", slowMotion);
        put(out, "voice", voice.name());
        put(out, "launches", launches);
        put(out, "demos", demos);
        cleared.forEach((game, set) -> { if (!set.isEmpty()) put(out, "cleared." + game, String.join(",", set)); });
        emeralds.forEach((game, set) -> { if (!set.isEmpty()) put(out, "emeralds." + game, String.join(",", set)); });
        stars.forEach((key, n) -> put(out, "stars." + key, n));
        records.forEach((key, list) -> {
            if (list.isEmpty()) return;
            List<String> parts = new ArrayList<>();
            for (Entry e : list) parts.add(e.encode());
            put(out, "record." + key, String.join(",", parts));
        });
        grades.forEach((drill, grade) -> put(out, "grade." + drill, grade));
        if (!keyHits.isEmpty()) put(out, "keys.hits", keyHits.encode());
        if (!keyMisses.isEmpty()) put(out, "keys.misses", keyMisses.encode());
        put(out, "life.chars", lifeChars);
        put(out, "life.errors", lifeErrors);
        put(out, "life.words", lifeWords);
        put(out, "life.minutes", String.format(Locale.ROOT, "%.4f", lifeMinutes));
        put(out, "life.acts", actsPlayed);
        put(out, "life.cleared", actsCleared);
        unknown.forEach((k, v) -> put(out, k, v));
        return out.toString();
    }

    private static void put(StringBuilder out, String key, Object value) {
        out.append(key).append('=').append(value).append('\n');
    }

    // ---- options ----------------------------------------------------------------------------

    public LaunchIntent.Difficulty difficulty() { return difficulty; }
    public void difficulty(LaunchIntent.Difficulty value) { difficulty = java.util.Objects.requireNonNull(value); }
    public boolean lore() { return lore; }
    public void lore(boolean v) { lore = v; }
    public boolean tricky() { return tricky; }
    public void tricky(boolean v) { tricky = v; }
    public boolean userWords() { return userWords; }
    public void userWords(boolean v) { userWords = v; }
    public boolean capitals() { return capitals; }
    public void capitals(boolean v) { capitals = v; }
    public boolean punctuation() { return punctuation; }
    public void punctuation(boolean v) { punctuation = v; }
    public boolean noFail() { return noFail; }
    public void noFail(boolean v) { noFail = v; }
    public boolean slowMotion() { return slowMotion; }
    public void slowMotion(boolean v) { slowMotion = v; }
    public LaunchIntent.Voice voice() { return voice; }
    public void voice(LaunchIntent.Voice v) { voice = java.util.Objects.requireNonNull(v); }

    /** The dictionaries in {@code LaunchIntent} form: {@code standard} plus any enabled extras. */
    public String dictionaries() {
        StringBuilder out = new StringBuilder("standard");
        if (lore) out.append(",lore");
        if (tricky) out.append(",tricky");
        if (userWords) out.append(",user");
        return out.toString();
    }

    /** Counts a launch and returns its ordinal, which the seed mixes in. */
    public long nextLaunch() { return ++launches; }
    public long launches() { return launches; }

    /** Counts an attract demo and returns how many came before it (demo 0 is the first). */
    public long nextDemo() { return demos++; }
    public long demos() { return demos; }

    // ---- story ------------------------------------------------------------------------------

    public static String act(int zone, int act) { return zone + ":" + act; }

    public Set<String> cleared(String game) {
        return java.util.Collections.unmodifiableSet(cleared.getOrDefault(game, Set.of()));
    }

    public boolean isCleared(String game, int zone, int act) {
        return cleared.getOrDefault(game, Set.of()).contains(act(zone, act));
    }

    /** Marks a story clear; true when it is the first. */
    public boolean clear(String game, int zone, int act) {
        return cleared.computeIfAbsent(game, ignored -> new TreeSet<>()).add(act(zone, act));
    }

    /** Awards an emerald for this act; true when it is new. */
    public boolean awardEmerald(String game, int zone, int act) {
        return emeralds.computeIfAbsent(game, ignored -> new TreeSet<>()).add(act(zone, act));
    }

    public boolean hasEmerald(String game, int zone, int act) {
        return emeralds.getOrDefault(game, Set.of()).contains(act(zone, act));
    }

    /** Emeralds shown for a game: one per emerald act, at most seven. */
    public int emeraldCount(String game) {
        return Math.min(EMERALDS, emeralds.getOrDefault(game, Set.of()).size());
    }

    public int totalEmeralds() {
        int sum = 0;
        for (String game : emeralds.keySet()) sum += emeraldCount(game);
        return sum;
    }

    // ---- boss rush --------------------------------------------------------------------------

    /** A boss's star level: 0 until its first Boss Rush win. */
    public int stars(String game, int zone, int act) {
        return stars.getOrDefault(game + ":" + act(zone, act), 0);
    }

    /** Raises a boss's star level after a Boss Rush win and returns the new level. */
    public int raiseStars(String game, int zone, int act) {
        return stars.merge(game + ":" + act(zone, act), 1, Integer::sum);
    }

    // ---- records ----------------------------------------------------------------------------

    /** Story and Arcade share one table per act and difficulty. */
    public static String actTable(String game, int zone, int act, LaunchIntent.Difficulty d) {
        return "act." + game + "." + zone + "." + act + "." + d.name();
    }

    public static String endlessTable(String game, int zone, int act, LaunchIntent.Difficulty d) {
        return "endless." + game + "." + zone + "." + act + "." + d.name();
    }

    public static String rushTable(String game, LaunchIntent.Difficulty d) {
        return "rush." + game + "." + d.name();
    }

    public List<Entry> records(String table) {
        return List.copyOf(records.getOrDefault(table, List.of()));
    }

    /** Files an entry; returns its 1-based place, or 0 when it did not make the table. */
    public int file(String table, Entry entry) {
        List<Entry> list = records.computeIfAbsent(table, ignored -> new ArrayList<>());
        int place = 0;
        while (place < list.size() && list.get(place).score() >= entry.score()) place++;
        if (place >= RECORD_SLOTS) return 0;
        list.add(place, entry);
        while (list.size() > RECORD_SLOTS) list.removeLast();
        return place + 1;
    }

    /** The best entry across every difficulty of an act's table, or null. */
    public Entry bestForAct(String game, int zone, int act) {
        Entry best = null;
        for (LaunchIntent.Difficulty d : LaunchIntent.Difficulty.values()) {
            List<Entry> list = records.getOrDefault(actTable(game, zone, act, d), List.of());
            if (!list.isEmpty() && (best == null || list.getFirst().score() > best.score())) best = list.getFirst();
        }
        return best;
    }

    // ---- typing -----------------------------------------------------------------------------

    public KeyTally keyHits() { return keyHits; }
    public KeyTally keyMisses() { return keyMisses; }

    /** Adds a session's typing: correct characters, errors, completed words and active minutes. */
    public void addTyping(long chars, long errors, long words, double minutes) {
        lifeChars += Math.max(0, chars);
        lifeErrors += Math.max(0, errors);
        lifeWords += Math.max(0, words);
        lifeMinutes += finite(minutes);
    }

    public void countAct(boolean wasCleared) {
        actsPlayed++;
        if (wasCleared) actsCleared++;
    }

    public long lifeChars() { return lifeChars; }
    public long lifeErrors() { return lifeErrors; }
    public long lifeWords() { return lifeWords; }
    public double lifeMinutes() { return lifeMinutes; }
    public long actsPlayed() { return actsPlayed; }
    public long actsCleared() { return actsCleared; }

    /** Lifetime words per minute (five characters a word), 0 before any typing. */
    public double lifetimeWpm() {
        return lifeMinutes <= 1e-9 ? 0 : lifeChars / 5.0 / lifeMinutes;
    }

    public double lifetimeAccuracy() {
        long total = lifeChars + lifeErrors;
        return total == 0 ? 1.0 : (double) lifeChars / total;
    }

    /** Lifetime accuracy for one key, or -1 with no data. */
    public double keyAccuracy(char key) {
        long hit = keyHits.get(key), miss = keyMisses.get(key);
        return hit + miss == 0 ? -1 : (double) hit / (hit + miss);
    }

    // ---- school -----------------------------------------------------------------------------

    public String grade(String drill) { return grades.get(drill); }

    /** Records a drill grade, keeping the best; true when it improved. */
    public boolean recordGrade(String drill, String grade) {
        if (!Grades.valid(grade)) return false;
        String old = grades.get(drill);
        if (old != null && Grades.rank(old) >= Grades.rank(grade)) return false;
        grades.put(drill, grade);
        return true;
    }

    /** Every key this profile's tables use, for tools. */
    public Set<String> recordTables() { return new LinkedHashSet<>(records.keySet()); }
}
