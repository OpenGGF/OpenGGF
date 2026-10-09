package typinghog.play;

import java.util.HashMap;
import java.util.Map;

/**
 * One game's sound-driver ids for the course's cues, read from {@code sounds/<game>.txt}
 * ({@code cue id} per line, hexadecimal ids). Cues the table lacks stay silent.
 */
public final class Sounds {
    /** The course's sound cues; each game maps them to its own ROM effects and jingles. */
    public enum Cue {
        KEY, ERROR, LOCK, KILL, HIT, RING, RING_SPILL, MONITOR, SHIELD, JUMP, SPRING, BOSS_HIT, BOSS_DEFEATED,
        STAND_START, STAND_CLEAR, GAME_OVER_MUSIC, ACT_CLEAR_MUSIC, BOSS_MUSIC, INVINCIBLE_MUSIC, EXTRA_LIFE_MUSIC,
        TALLY, TALLY_END
    }

    private final Map<Cue, Integer> ids = new HashMap<>();

    public static Sounds parse(String text) {
        Sounds sounds = new Sounds();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            if (f.length != 2) throw new IllegalArgumentException("sound line needs 'cue id': " + line);
            sounds.ids.put(Cue.valueOf(f[0]), Integer.decode(f[1]));
        }
        return sounds;
    }

    /** The driver id for a cue, or -1. */
    public int id(Cue cue) {
        return ids.getOrDefault(cue, -1);
    }
}
