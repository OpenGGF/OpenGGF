package typinghog.rails;

import java.util.LinkedHashMap;
import java.util.Map;

/** A selected stock-level section, with explicit flight/finale treatment. Positions come from ROM terrain. */
public record SectionPlan(String game, int zone, int act, Mode mode) {
    public enum Mode { GROUND, ESCORT, FINALE }

    public SectionPlan {
        if (game == null || !(game.equals("s1") || game.equals("s2") || game.equals("s3k"))
                || zone < 0 || act < 0 || mode == null) {
            throw new IllegalArgumentException("A section needs a stock game, zone, act and mode");
        }
    }

    public String key() { return game + ":" + zone + ":" + act; }

    /** Lines: game zone act GROUND|ESCORT|FINALE. Duplicate identities are always an authoring error. */
    public static Map<String, SectionPlan> parse(String text) {
        Map<String, SectionPlan> plans = new LinkedHashMap<>();
        int number = 0;
        for (String raw : text.split("\n")) {
            number++;
            String line = raw.split("#", 2)[0].strip();
            if (line.isEmpty()) continue;
            String[] fields = line.split("\\s+");
            if (fields.length != 4) throw new IllegalArgumentException("Section line " + number + " needs game zone act mode");
            SectionPlan plan = new SectionPlan(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                    Mode.valueOf(fields[3]));
            if (plans.putIfAbsent(plan.key(), plan) != null)
                throw new IllegalArgumentException("Duplicate section " + plan.key());
        }
        return Map.copyOf(plans);
    }
}
