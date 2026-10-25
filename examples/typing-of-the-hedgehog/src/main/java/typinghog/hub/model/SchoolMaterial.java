package typinghog.hub.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import typinghog.core.Lexicon;

/**
 * What each Typing School drill asks the player to type: words from the mod's dictionaries
 * ({@link Lexicon#lessonWords} for the keyboard-row lessons), the authored lines in
 * {@code hub/school.txt}, Robotnik's taunts and quiz questions. Engine-free.
 *
 * <p>{@code school.txt} holds {@code [section]} headers followed by one entry per line;
 * {@code #} starts a comment. Sections: {@code home}, {@code top}, {@code bottom},
 * {@code numbers}, {@code capitals}, {@code punctuation}, and Tails' lines {@code tails.*}.
 */
public final class SchoolMaterial {
    static final String HOME = "asdfghjkl";
    static final String TOP = "qwertyuiop";
    static final String BOTTOM = "zxcvbnm";

    private final Lexicon lexicon;
    private final Map<String, List<String>> sections;

    public SchoolMaterial(Lexicon lexicon, String schoolText) {
        this.lexicon = lexicon;
        this.sections = parseSections(schoolText);
    }

    public static Map<String, List<String>> parseSections(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        List<String> current = null;
        for (String raw : (text == null ? "" : text).split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                current = out.computeIfAbsent(line.substring(1, line.length() - 1).strip(), k -> new ArrayList<>());
            } else if (current != null) {
                current.add(line);
            }
        }
        return out;
    }

    public Lexicon lexicon() { return lexicon; }

    /** An authored section's lines (empty when absent). */
    public List<String> lines(String section) {
        return Collections.unmodifiableList(sections.getOrDefault(section, List.of()));
    }

    /** One of Tails' lines for {@code key} ({@code tails.<key>}), picked by {@code n}; "" when none. */
    public String tails(String key, int n) {
        List<String> lines = sections.getOrDefault("tails." + key, List.of());
        return lines.isEmpty() ? "" : lines.get(Math.floorMod(n, lines.size()));
    }

    /**
     * The prompts for a drill. Ring Rush gets enough for a minute at any speed; Reflex gets
     * single letters; Boss Training alternates phrase, talk-back and quiz rounds.
     */
    public List<DrillSession.Item> items(Drill drill, SplittableRandom random, KeyTally hits, KeyTally misses) {
        return switch (drill) {
            case HOME_ROW -> words(rowWords(HOME, "", 0, random), lines("home"), drill.count, random);
            case TOP_ROW -> words(rowWords(HOME + TOP, TOP, 2, random), lines("top"), drill.count, random);
            case BOTTOM_ROW -> words(rowWords(HOME + BOTTOM, BOTTOM, 1, random), lines("bottom"), drill.count, random);
            case NUMBERS -> numbers(drill.count, random);
            case CAPITALS -> capitals(drill.count, random);
            case PUNCTUATION -> punctuation(drill.count, random);
            case RING_RUSH -> plain(pick(filter(lexicon.entries("common"), 3, 8), 240, random));
            case SUDDEN_DEATH -> plain(pick(filter(lexicon.entries("common"), 4, 9), drill.count, random));
            case REFLEX -> letters(drill.count, random);
            case LONG_WORDS -> longWords(drill.count, random);
            case PHRASES -> phrases(drill.count, random);
            case BOSS_TRAINING -> bossRounds(random);
            case WEAK_KEYS -> weakWords(drill.count, random, hits, misses);
            case FINAL_EXAM -> exam(random);
        };
    }

    // ---- lessons ----------------------------------------------------------------------------

    /** Words made of {@code letters} that use at least {@code minFromNew} letters of {@code fresh}. */
    private List<String> rowWords(String letters, String fresh, int minFromNew, SplittableRandom random) {
        List<String> out = new ArrayList<>();
        for (String word : lexicon.lessonWords(letters, 2, 9)) {
            int fromNew = 0;
            for (char c : word.toCharArray()) if (fresh.indexOf(c) >= 0) fromNew++;
            if (fromNew >= minFromNew && !out.contains(word)) out.add(word);
        }
        return out;
    }

    private static List<DrillSession.Item> words(List<String> found, List<String> authored, int count,
                                                 SplittableRandom random) {
        List<String> pool = new ArrayList<>(found);
        // Authored lines lead the lesson, then dictionary words in a shuffled order.
        List<String> lead = pick(authored, Math.min(authored.size(), count / 3), random);
        List<String> rest = pick(pool, count - lead.size(), random);
        List<String> all = new ArrayList<>(lead);
        all.addAll(rest);
        int i = 0;
        while (all.size() < count && !authored.isEmpty()) all.add(authored.get(i++ % authored.size()));
        return plain(all);
    }

    private List<DrillSession.Item> numbers(int count, SplittableRandom random) {
        List<String> all = new ArrayList<>(pick(lines("numbers"), count / 2, random));
        while (all.size() < count) {
            int digits = 2 + random.nextInt(4);
            StringBuilder n = new StringBuilder();
            for (int d = 0; d < digits; d++) n.append((char) ('0' + random.nextInt(10)));
            all.add(n.toString());
        }
        Collections.shuffle(all, new java.util.Random(random.nextLong()));
        return plain(all);
    }

    private List<DrillSession.Item> capitals(int count, SplittableRandom random) {
        List<String> pool = new ArrayList<>(lines("capitals"));
        for (String game : List.of("lore-s1", "lore-s2", "lore-s3k")) {
            for (String entry : lexicon.entries(game)) {
                if (entry.length() >= 3 && entry.length() <= 14 && entry.matches("[A-Za-z ]+")
                        && !entry.equals(entry.toLowerCase(java.util.Locale.ROOT))) pool.add(entry);
            }
        }
        List<DrillSession.Item> out = new ArrayList<>();
        for (String s : pick(pool, count, random)) out.add(DrillSession.Item.word(s, true, false));
        return out;
    }

    private List<DrillSession.Item> punctuation(int count, SplittableRandom random) {
        List<String> pool = new ArrayList<>(lines("punctuation"));
        for (String phrase : lexicon.entries("phrases")) {
            if (phrase.length() <= 34 && phrase.matches(".*[,.!?'].*")) pool.add(phrase);
        }
        List<DrillSession.Item> out = new ArrayList<>();
        for (String s : pick(pool, count, random)) out.add(DrillSession.Item.phrase(s, false, true));
        return out;
    }

    // ---- drills -----------------------------------------------------------------------------

    private static List<DrillSession.Item> letters(int count, SplittableRandom random) {
        List<DrillSession.Item> out = new ArrayList<>();
        char last = 0;
        for (int i = 0; i < count; i++) {
            char c;
            do {
                c = (char) ('A' + random.nextInt(26));
            } while (c == last);
            last = c;
            out.add(DrillSession.Item.letter(String.valueOf(c)));
        }
        return out;
    }

    private List<DrillSession.Item> longWords(int count, SplittableRandom random) {
        List<String> pool = new ArrayList<>(filter(lexicon.entries("tricky"), 9, 16));
        if (pool.size() < count) pool.addAll(filter(lexicon.entries("common"), 9, 14));
        return plain(pick(pool, count, random));
    }

    private List<DrillSession.Item> phrases(int count, SplittableRandom random) {
        List<String> pool = new ArrayList<>();
        for (String phrase : lexicon.entries("phrases")) if (phrase.length() <= 40) pool.add(phrase);
        List<DrillSession.Item> out = new ArrayList<>();
        for (String s : pick(pool, count, random)) out.add(DrillSession.Item.phrase(s, false, false));
        return out;
    }

    private List<DrillSession.Item> bossRounds(SplittableRandom random) {
        List<String> long_ = new ArrayList<>();
        for (String phrase : lexicon.entries("phrases")) if (phrase.length() >= 18 && phrase.length() <= 40) long_.add(phrase);
        List<String> taunts = new ArrayList<>();
        for (Lexicon.Taunt t : lexicon.taunts()) if (t.text().length() <= 44) taunts.add(t.text());
        List<Lexicon.Trivia> quiz = new ArrayList<>(lexicon.trivia());
        List<String> phrasePick = pick(long_, 2, random);
        List<String> tauntPick = pick(taunts, 2, random);
        List<DrillSession.Item> out = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            out.add(DrillSession.Item.practice(phrasePick.isEmpty() ? "rings before things"
                    : phrasePick.get(round % phrasePick.size())));
            out.add(DrillSession.Item.talkBack(tauntPick.isEmpty() ? "You will never beat my robots"
                    : tauntPick.get(round % tauntPick.size())));
            if (quiz.isEmpty()) {
                out.add(DrillSession.Item.quiz("Who is Sonic's best friend?", List.of("Tails", "Robotnik", "Motobug")));
            } else {
                Lexicon.Trivia t = quiz.remove(random.nextInt(quiz.size()));
                out.add(DrillSession.Item.quiz(t.question(), List.of(t.correct(), t.wrong1(), t.wrong2())));
            }
        }
        return out;
    }

    /**
     * The keys this profile misses most: the five letters with the highest smoothed error rate
     * ((misses + 1) / (presses + 2)) among those pressed at least five times, filled from
     * {@code q z x j k} when there is not enough history yet.
     */
    public static String weakKeys(KeyTally hits, KeyTally misses, int n) {
        Map<Character, Double> rate = new HashMap<>();
        for (char c = 'a'; c <= 'z'; c++) {
            long hit = hits.get(c), miss = misses.get(c);
            if (hit + miss >= 5) rate.put(c, (miss + 1.0) / (hit + miss + 2.0));
        }
        List<Character> keys = new ArrayList<>(rate.keySet());
        keys.sort(Comparator.comparingDouble((Character c) -> -rate.get(c)).thenComparing(c -> c));
        StringBuilder out = new StringBuilder();
        for (char c : keys) {
            if (out.length() >= n) break;
            if (rate.get(c) > 0.04) out.append(c);
        }
        for (char c : "qzxjkvbpyw".toCharArray()) {
            if (out.length() >= n) break;
            if (out.indexOf(String.valueOf(c)) < 0) out.append(c);
        }
        return out.toString();
    }

    private List<DrillSession.Item> weakWords(int count, SplittableRandom random, KeyTally hits, KeyTally misses) {
        String weak = weakKeys(hits, misses, 5);
        List<String> scored = new ArrayList<>();
        Map<String, Integer> score = new HashMap<>();
        for (String word : filter(lexicon.entries("common"), 3, 9)) {
            int s = 0;
            for (char c : word.toCharArray()) if (weak.indexOf(c) >= 0) s++;
            if (s > 0) {
                scored.add(word);
                score.put(word, s);
            }
        }
        Collections.shuffle(scored, new java.util.Random(random.nextLong()));
        scored.sort(Comparator.comparingInt((String w) -> -score.get(w)));
        // The richest words first, mixed with a few lighter ones so it reads as words, not drills.
        List<String> top = new ArrayList<>(scored.subList(0, Math.min(scored.size(), count * 3)));
        return plain(pick(top, count, random));
    }

    private List<DrillSession.Item> exam(SplittableRandom random) {
        List<DrillSession.Item> out = new ArrayList<>();
        out.addAll(plain(pick(filter(lexicon.entries("common"), 4, 8), 6, random)));
        out.addAll(longWords(4, random));
        out.addAll(capitals(3, random));
        out.addAll(punctuation(3, random));
        out.addAll(numbers(2, random));
        out.addAll(phrases(2, random));
        return out;
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static List<String> filter(List<String> words, int min, int max) {
        List<String> out = new ArrayList<>();
        for (String w : words) if (w.length() >= min && w.length() <= max && w.matches("[a-z]+")) out.add(w);
        return out;
    }

    /** {@code n} entries of {@code pool} in a seeded order, repeating only when the pool runs short. */
    static List<String> pick(List<String> pool, int n, SplittableRandom random) {
        List<String> out = new ArrayList<>();
        if (pool.isEmpty() || n <= 0) return out;
        List<String> deck = new ArrayList<>();
        while (out.size() < n) {
            if (deck.isEmpty()) {
                deck.addAll(pool);
                Collections.shuffle(deck, new java.util.Random(random.nextLong()));
            }
            out.add(deck.removeLast());
        }
        return out;
    }

    private static List<DrillSession.Item> plain(List<String> words) {
        List<DrillSession.Item> out = new ArrayList<>();
        for (String w : words) out.add(DrillSession.Item.word(w, false, false));
        return out;
    }
}
