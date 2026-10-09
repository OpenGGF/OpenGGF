package typinghog.play;

import java.util.HashMap;
import java.util.Map;

/**
 * Where each game keeps the course's prop art, read from {@code props/<game>.txt}: one
 * {@code name artKey frame} line per prop ({@code monitor_shield monitor 5}). Props missing
 * from the table draw nothing but still work.
 */
public final class Props {
    /** A prop's stock art key and mapping frame. */
    public record Art(String artKey, int frame) { }

    private final Map<String, Art> art = new HashMap<>();

    public static Props parse(String text) {
        Props props = new Props();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            if (f.length != 3) throw new IllegalArgumentException("props line needs 'name artKey frame': " + line);
            props.art.put(f[0], new Art(f[1], Integer.parseInt(f[2])));
        }
        return props;
    }

    public Art get(String name) {
        return art.get(name);
    }
}
