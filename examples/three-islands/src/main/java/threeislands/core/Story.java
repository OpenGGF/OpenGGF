package threeislands.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The story script, read from {@code text/story.txt}. A line {@code @name} starts a scene;
 * {@code Speaker: words} is dialogue and {@code > words} is narration. Lines starting with
 * {@code #} are comments.
 */
public final class Story {
    /** One line of a scene; {@code speaker} is null for narration. */
    public record Line(String speaker, String text) {
    }

    private final Map<String, List<Line>> scenes = new LinkedHashMap<>();

    public Story(byte[] script) {
        String current = null;
        int number = 0;
        for (String raw : new String(script, StandardCharsets.UTF_8).split("\n")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("@")) {
                current = line.substring(1).strip();
                if (!current.matches("[a-z0-9_-]{1,40}") || scenes.containsKey(current)) {
                    throw new IllegalArgumentException("Bad or duplicate scene name on line " + number);
                }
                scenes.put(current, new ArrayList<>());
            } else if (current == null) {
                throw new IllegalArgumentException("Story text before any @scene on line " + number);
            } else if (line.startsWith(">")) {
                scenes.get(current).add(new Line(null, line.substring(1).strip()));
            } else {
                int colon = line.indexOf(':');
                if (colon <= 0 || colon > 16) throw new IllegalArgumentException("Expected 'Speaker: text' on line " + number);
                scenes.get(current).add(new Line(line.substring(0, colon).strip(), line.substring(colon + 1).strip()));
            }
        }
    }

    public boolean has(String scene) {
        return scenes.containsKey(scene) && !scenes.get(scene).isEmpty();
    }

    public List<Line> scene(String scene) {
        List<Line> lines = scenes.get(scene);
        return lines == null ? List.of() : List.copyOf(lines);
    }

    public List<String> names() {
        return List.copyOf(scenes.keySet());
    }
}
