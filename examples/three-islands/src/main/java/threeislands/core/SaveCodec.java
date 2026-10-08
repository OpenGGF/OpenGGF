package threeislands.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A plain-text, versioned save. Unknown keys are ignored so later versions can add fields;
 * anything malformed is rejected as a whole rather than half-loaded.
 */
public final class SaveCodec {
    public static final String HEADER = "three-islands-save 1";
    public static final int MAX_LENGTH = 65536;

    private SaveCodec() {
    }

    public static String encode(Progress progress) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append("rng=").append(progress.rng.state()).append('\n');
        out.append("joined=").append(progress.joinedMask()).append('\n');
        out.append("rings=").append(progress.rings()).append('\n');
        out.append("emeralds=").append(progress.emeralds()).append('\n');
        out.append("cleared=").append(progress.clearedMask()).append('\n');
        out.append("island=").append(progress.island().ordinal()).append('\n');
        out.append("ticks=").append(progress.playTicks()).append('\n');
        out.append("resume=").append(progress.resumeZone()).append(',').append(progress.resumeX()).append('\n');
        out.append("finished=").append(progress.finished() ? 1 : 0).append('\n');
        for (HeroId id : HeroId.values()) {
            Hero hero = progress.hero(id);
            out.append("hero.").append(id.name()).append('=').append(hero.level()).append(',').append(hero.xp())
                    .append(',').append(hero.hp()).append(',').append(hero.ep()).append('\n');
        }
        for (Item item : Item.values()) {
            if (progress.count(item) > 0) out.append("item.").append(item.name()).append('=').append(progress.count(item))
                    .append('\n');
        }
        for (String scene : progress.seenScenes()) out.append("scene=").append(scene).append('\n');
        return out.toString();
    }

    public static Progress decode(String text) {
        if (text == null || text.length() > MAX_LENGTH) throw new IllegalArgumentException("Save is missing or too large");
        String[] lines = text.split("\n");
        if (lines.length == 0 || !lines[0].strip().equals(HEADER)) throw new IllegalArgumentException("Not a Three Islands save");
        Map<String, String> values = new HashMap<>();
        Progress progress = new Progress(1);
        progress.clearScenes();
        for (Item item : Item.values()) progress.setItemCount(item, 0);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("Malformed line " + (i + 1));
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            if (key.equals("scene")) {
                if (!value.matches("[a-z0-9_-]{1,40}")) throw new IllegalArgumentException("Bad scene name");
                progress.markSeen(value);
            } else if (key.startsWith("item.")) {
                Item item = Item.valueOf(key.substring(5));
                progress.setItemCount(item, Integer.parseInt(value));
            } else if (key.startsWith("hero.")) {
                HeroId id = HeroId.valueOf(key.substring(5));
                String[] parts = value.split(",");
                if (parts.length != 4) throw new IllegalArgumentException("Bad hero line");
                progress.hero(id).load(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } else {
                values.put(key, value);
            }
        }
        progress.rng.restore(Long.parseLong(values.getOrDefault("rng", "1")));
        progress.loadState(Integer.parseInt(values.getOrDefault("joined", "1")),
                Integer.parseInt(values.getOrDefault("rings", "0")),
                Integer.parseInt(values.getOrDefault("emeralds", "0")),
                Integer.parseInt(values.getOrDefault("cleared", "0")),
                Integer.parseInt(values.getOrDefault("island", "0")),
                Long.parseLong(values.getOrDefault("ticks", "0")));
        String[] resume = values.getOrDefault("resume", "-1,0").split(",");
        if (resume.length != 2) throw new IllegalArgumentException("Bad resume line");
        progress.loadResume(Integer.parseInt(resume[0]), Integer.parseInt(resume[1]),
                values.getOrDefault("finished", "0").equals("1"));
        return progress;
    }
}
