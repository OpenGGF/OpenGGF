package typinghog.save;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the hub asked the next level to be: the mode, act, difficulty and options. The hub
 * writes it to the mod's storage just before {@code startStockAct}; the course reads it when
 * the level starts and clears it, so a level started any other way plays nothing typed.
 *
 * <p>Stored as {@code key=value} lines in {@code intent.txt}.
 */
public record LaunchIntent(Mode mode, String game, int zone, int act, Difficulty difficulty,
                           boolean capitals, boolean punctuation, String dictionaries, long seed,
                           int bossStars, boolean noFail, boolean slowMotion) {
    public static final String FILE = "intent.txt";

    public enum Mode { STORY, ARCADE, BOSS_RUSH, ENDLESS }

    public enum Difficulty {
        EASY(0.70, 1), NORMAL(1.0, 2), HARD(1.25, 3), EXPERT(1.5, 4);
        /** Enemy approach speed relative to Normal. */
        public final double speed;
        /** Highest word tier (1 short words .. 4 long words and phrases) used for rank-and-file badniks. */
        public final int tier;
        Difficulty(double speed, int tier) { this.speed = speed; this.tier = tier; }
    }

    public LaunchIntent {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(dictionaries, "dictionaries");
    }

    public String encode() {
        var values = new LinkedHashMap<String, String>();
        values.put("mode", mode.name());
        values.put("game", game);
        values.put("zone", Integer.toString(zone));
        values.put("act", Integer.toString(act));
        values.put("difficulty", difficulty.name());
        values.put("capitals", Boolean.toString(capitals));
        values.put("punctuation", Boolean.toString(punctuation));
        values.put("dictionaries", dictionaries);
        values.put("seed", Long.toString(seed));
        values.put("bossStars", Integer.toString(bossStars));
        values.put("noFail", Boolean.toString(noFail));
        values.put("slowMotion", Boolean.toString(slowMotion));
        var out = new StringBuilder();
        values.forEach((k, v) -> out.append(k).append('=').append(v).append('\n'));
        return out.toString();
    }

    public static LaunchIntent decode(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) values.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return new LaunchIntent(Mode.valueOf(values.getOrDefault("mode", "ARCADE")),
                values.getOrDefault("game", "s1"),
                Integer.parseInt(values.getOrDefault("zone", "0")),
                Integer.parseInt(values.getOrDefault("act", "0")),
                Difficulty.valueOf(values.getOrDefault("difficulty", "NORMAL")),
                Boolean.parseBoolean(values.getOrDefault("capitals", "false")),
                Boolean.parseBoolean(values.getOrDefault("punctuation", "false")),
                values.getOrDefault("dictionaries", "standard"),
                Long.parseLong(values.getOrDefault("seed", "0")),
                Integer.parseInt(values.getOrDefault("bossStars", "1")),
                Boolean.parseBoolean(values.getOrDefault("noFail", "false")),
                Boolean.parseBoolean(values.getOrDefault("slowMotion", "false")));
    }
}
