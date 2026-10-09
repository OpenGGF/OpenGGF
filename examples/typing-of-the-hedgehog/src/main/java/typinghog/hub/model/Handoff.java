package typinghog.hub.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import typinghog.save.LaunchIntent;

/**
 * What the hub remembers in storage across its hand-offs to gameplay and to other games.
 * A reopened hub starts fresh, so every reason to come back is written down first:
 * <ul>
 *   <li>{@code hub-launch.txt}: the intent the hub last launched (the course deletes
 *       {@code intent.txt} when it reads it; the hub keeps its own copy to file the result
 *       under the right difficulty);</li>
 *   <li>{@code pending.txt}: an act chosen in another game (or needing another team); the hub
 *       reopened by {@code relaunch} continues straight into it;</li>
 *   <li>{@code rush.txt}: a Boss Rush in progress, the bosses still to fight.</li>
 * </ul>
 * Engine-free.
 */
public final class Handoff {
    public static final String LAUNCHED = "hub-launch.txt";
    public static final String PENDING = "pending.txt";
    public static final String RUSH = "rush.txt";

    /**
     * The team each game's acts launch with: sidekick character codes, comma-separated, empty
     * for Sonic alone. Rails are validated with this team, so change it together with them
     * (for example {@code "tails"} for Sonic and Tails).
     */
    public static final String S1_SIDEKICKS = "";
    public static final String S2_SIDEKICKS = "";
    public static final String S3K_SIDEKICKS = "";
    public static final String MAIN_CHARACTER = "sonic";

    private Handoff() { }

    public static List<String> sidekicks(String game) {
        String list = switch (game) {
            case "s1" -> S1_SIDEKICKS;
            case "s2" -> S2_SIDEKICKS;
            case "s3k" -> S3K_SIDEKICKS;
            default -> "";
        };
        List<String> out = new ArrayList<>();
        for (String code : list.split(",")) if (!code.isBlank()) out.add(code.strip());
        return out;
    }

    /** True when a running session's team is the one {@code game}'s acts need. */
    public static boolean teamMatches(String game, String main, List<String> running) {
        return MAIN_CHARACTER.equals(main) && sidekicks(game).equals(running);
    }

    /** A fresh seed from the profile's launch count and the scene clock (SplitMix64). */
    public static long seed(long launch, long ticks) {
        long z = launch * 0x9E3779B97F4A7C15L + ticks + 0x632BE59BD9B4E019L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The intent for an act under the profile's options. */
    public static LaunchIntent intent(LaunchIntent.Mode mode, String game, int zone, int act, Profile p, int bossStars,
                                      long seed) {
        return new LaunchIntent(mode, game, zone, act, p.difficulty(), p.capitals(), p.punctuation(),
                p.dictionaries(), seed, Math.max(1, bossStars), p.noFail(), p.slowMotion(), p.voice());
    }

    // ---- pending selection ------------------------------------------------------------------

    /** An act to start as soon as the hub opens in {@code game}. */
    public record Pending(LaunchIntent.Mode mode, String game, int zone, int act) {
        public String encode() {
            return "mode=" + mode.name() + "\ngame=" + game + "\nzone=" + zone + "\nact=" + act + "\n";
        }

        /** Null for missing or damaged text. */
        public static Pending decode(String text) {
            Map<String, String> v = fields(text);
            try {
                String game = v.get("game");
                if (!List.of("s1", "s2", "s3k").contains(game)) return null;
                return new Pending(LaunchIntent.Mode.valueOf(v.get("mode")), game, Integer.parseInt(v.get("zone")),
                        Integer.parseInt(v.get("act")));
            } catch (RuntimeException damaged) {
                return null;
            }
        }
    }

    // ---- boss rush --------------------------------------------------------------------------

    /** A Boss Rush in one game: bosses fought so far, those left ({@code zone:act}) and the running score. */
    public record Rush(String game, List<String> remaining, int won, long score) {
        public Rush {
            remaining = List.copyOf(remaining);
        }

        public boolean finished() { return remaining.isEmpty(); }

        public int nextZone() { return Integer.parseInt(remaining.getFirst().split(":")[0]); }
        public int nextAct() { return Integer.parseInt(remaining.getFirst().split(":")[1]); }

        /** After a win over the next boss. */
        public Rush won(long points) {
            return new Rush(game, remaining.subList(1, remaining.size()), won + 1, score + points);
        }

        public String encode() {
            return "game=" + game + "\nremaining=" + String.join(",", remaining) + "\nwon=" + won + "\nscore=" + score + "\n";
        }

        public static Rush decode(String text) {
            Map<String, String> v = fields(text);
            try {
                String game = v.get("game");
                if (!List.of("s1", "s2", "s3k").contains(game)) return null;
                List<String> left = new ArrayList<>();
                for (String part : v.getOrDefault("remaining", "").split(",")) {
                    if (part.strip().matches("\\d{1,2}:\\d")) left.add(part.strip());
                }
                return new Rush(game, left, Integer.parseInt(v.getOrDefault("won", "0")),
                        Long.parseLong(v.getOrDefault("score", "0")));
            } catch (RuntimeException damaged) {
                return null;
            }
        }
    }

    static Map<String, String> fields(String text) {
        Map<String, String> v = new LinkedHashMap<>();
        if (text == null) return v;
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) v.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return v;
    }
}
