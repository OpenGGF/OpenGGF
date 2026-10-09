package typinghog.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.IntPredicate;

/**
 * Every word list the mod ships, grouped into pools, with seeded picking that avoids repeats
 * and leading characters already on screen. Engine-free: the mod reads the resource files once
 * at registration and hands their text here.
 *
 * <p>Pools: {@code common} and {@code tricky} (plain words), {@code lore-s1/s2/s3k} (display
 * capitalisation, may contain spaces), {@code phrases}, {@code rescues}, plus the tagged
 * {@code taunts} and {@code trivia} tables. A user word list joins {@code common} when the
 * player enables it.
 */
public final class Lexicon {
    /** A quiz question with its correct answer first and two wrong ones. */
    public record Trivia(String game, String question, String correct, String wrong1, String wrong2) { }

    /** A Robotnik line and where it fits ({@code any}, a game id, or {@code game:zone}). */
    public record Taunt(String tag, String text) { }

    private final Map<String, List<String>> pools = new HashMap<>();
    private final List<Taunt> taunts = new ArrayList<>();
    private final List<Trivia> trivia = new ArrayList<>();

    /** Adds plain one-entry-per-line text to a pool ({@code #} comments and blanks skipped). */
    public Lexicon pool(String name, String text) {
        List<String> entries = pools.computeIfAbsent(name, ignored -> new ArrayList<>());
        for (String line : text.split("\n")) {
            String entry = line.strip();
            if (!entry.isEmpty() && !entry.startsWith("#")) entries.add(entry);
        }
        return this;
    }

    /** Adds {@code tag|text} taunt lines. */
    public Lexicon taunts(String text) {
        for (String line : text.split("\n")) {
            String entry = line.strip();
            int bar = entry.indexOf('|');
            if (entry.isEmpty() || entry.startsWith("#") || bar <= 0) continue;
            taunts.add(new Taunt(entry.substring(0, bar).strip(), entry.substring(bar + 1).strip()));
        }
        return this;
    }

    /** Adds {@code game|question|correct|wrong1|wrong2} lines. */
    public Lexicon trivia(String text) {
        for (String line : text.split("\n")) {
            String entry = line.strip();
            if (entry.isEmpty() || entry.startsWith("#")) continue;
            String[] parts = entry.split("\\|");
            if (parts.length != 5) continue;
            trivia.add(new Trivia(parts[0].strip(), parts[1].strip(), parts[2].strip(), parts[3].strip(), parts[4].strip()));
        }
        return this;
    }

    public List<String> entries(String pool) {
        return Collections.unmodifiableList(pools.getOrDefault(pool, List.of()));
    }

    public List<Taunt> taunts() { return Collections.unmodifiableList(taunts); }
    public List<Trivia> trivia() { return Collections.unmodifiableList(trivia); }

    /** Seeded picking with a short memory so a word does not return until others have had a turn. */
    public final class Picker {
        private final SplittableRandom random;
        private final List<String> recent = new ArrayList<>();
        private final int memory;

        public Picker(long seed, int memory) {
            this.random = new SplittableRandom(seed);
            this.memory = memory;
        }

        /**
         * A random entry from the pools whose typed length (under the default rules) lies in
         * {@code [minLength, maxLength]} and whose lead character passes {@code leadFree};
         * falls back to ignoring the lead, then the length, before giving up with null.
         */
        public String pick(List<String> poolNames, int minLength, int maxLength, IntPredicate leadFree) {
            List<String> candidates = new ArrayList<>();
            for (String name : poolNames) candidates.addAll(pools.getOrDefault(name, List.of()));
            if (candidates.isEmpty()) return null;
            String chosen = choose(candidates, minLength, maxLength, leadFree);
            if (chosen == null) chosen = choose(candidates, minLength, maxLength, lead -> true);
            if (chosen == null) chosen = choose(candidates, 1, Integer.MAX_VALUE, lead -> true);
            if (chosen != null) {
                recent.add(chosen);
                if (recent.size() > memory) recent.removeFirst();
            }
            return chosen;
        }

        private String choose(List<String> candidates, int minLength, int maxLength, IntPredicate leadFree) {
            List<String> fit = new ArrayList<>();
            for (String entry : candidates) {
                Prompt form;
                try {
                    form = Prompt.of(entry, false, false);
                } catch (IllegalArgumentException empty) {
                    continue;
                }
                int length = form.typed().length();
                if (length < minLength || length > maxLength || !leadFree.test(form.lead())) continue;
                if (!recent.contains(entry)) fit.add(entry);
            }
            return fit.isEmpty() ? null : fit.get(random.nextInt(fit.size()));
        }

        public int nextInt(int bound) { return random.nextInt(bound); }
    }

    /** Words made only of {@code letters} (a Typing School lesson), from the plain pools. */
    public List<String> lessonWords(String letters, int minLength, int maxLength) {
        List<String> out = new ArrayList<>();
        for (String name : List.of("common", "tricky")) {
            for (String word : pools.getOrDefault(name, List.of())) {
                if (word.length() < minLength || word.length() > maxLength) continue;
                boolean fits = true;
                for (int i = 0; i < word.length() && fits; i++) fits = letters.indexOf(word.charAt(i)) >= 0;
                if (fits) out.add(word);
            }
        }
        return out;
    }
}
