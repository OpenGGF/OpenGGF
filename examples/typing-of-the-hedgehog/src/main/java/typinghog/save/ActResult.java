package typinghog.save;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a typed act ended, written by the course to {@code result.txt} before it returns to the
 * hub, and read (then deleted) by the hub when it reopens.
 *
 * @param keyHits   per-character correct keystrokes, as {@code char:count,...}
 * @param keyMisses per-character errors (the character that was expected), same form
 */
public record ActResult(LaunchIntent.Mode mode, String game, int zone, int act, boolean cleared, long score,
                        double wpm, double accuracy, int bestChain, int kills, int hitsTaken, int rescues,
                        int rings, String grade, long tallyTotal, boolean emerald, String keyHits,
                        String keyMisses) {
    public static final String FILE = "result.txt";

    public String encode() {
        var values = new LinkedHashMap<String, String>();
        values.put("mode", mode.name());
        values.put("game", game);
        values.put("zone", Integer.toString(zone));
        values.put("act", Integer.toString(act));
        values.put("cleared", Boolean.toString(cleared));
        values.put("score", Long.toString(score));
        values.put("wpm", Double.toString(wpm));
        values.put("accuracy", Double.toString(accuracy));
        values.put("bestChain", Integer.toString(bestChain));
        values.put("kills", Integer.toString(kills));
        values.put("hitsTaken", Integer.toString(hitsTaken));
        values.put("rescues", Integer.toString(rescues));
        values.put("rings", Integer.toString(rings));
        values.put("grade", grade);
        values.put("tallyTotal", Long.toString(tallyTotal));
        values.put("emerald", Boolean.toString(emerald));
        values.put("keyHits", keyHits);
        values.put("keyMisses", keyMisses);
        var out = new StringBuilder();
        values.forEach((k, v) -> out.append(k).append('=').append(v).append('\n'));
        return out.toString();
    }

    public static ActResult decode(String text) {
        Map<String, String> v = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) v.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return new ActResult(LaunchIntent.Mode.valueOf(v.getOrDefault("mode", "ARCADE")), v.getOrDefault("game", "s1"),
                Integer.parseInt(v.getOrDefault("zone", "0")), Integer.parseInt(v.getOrDefault("act", "0")),
                Boolean.parseBoolean(v.getOrDefault("cleared", "false")), Long.parseLong(v.getOrDefault("score", "0")),
                Double.parseDouble(v.getOrDefault("wpm", "0")), Double.parseDouble(v.getOrDefault("accuracy", "1")),
                Integer.parseInt(v.getOrDefault("bestChain", "0")), Integer.parseInt(v.getOrDefault("kills", "0")),
                Integer.parseInt(v.getOrDefault("hitsTaken", "0")), Integer.parseInt(v.getOrDefault("rescues", "0")),
                Integer.parseInt(v.getOrDefault("rings", "0")), v.getOrDefault("grade", "E"),
                Long.parseLong(v.getOrDefault("tallyTotal", "0")), Boolean.parseBoolean(v.getOrDefault("emerald", "false")),
                v.getOrDefault("keyHits", ""), v.getOrDefault("keyMisses", ""));
    }
}
