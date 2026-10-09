package flappytails;

import com.openggf.mods.scene.SceneStorage;
import com.openggf.mods.state.VersionedSettings;

/**
 * What the player keeps between flights, saved as {@code records.txt} in the mod's own storage
 * ({@code saves/mods/flappy-tails/}): best scores and medals per mode, the furthest zone reached
 * (which unlocks practice from it) and lifetime totals. The file is plain {@code key=value}
 * text with a format version, read with {@link VersionedSettings}; an unreadable or newer file
 * is ignored rather than trusted.
 */
final class Records {
    static final String FILE = "records.txt";
    private static final int VERSION = 1;

    private final int[] best = new int[Mode.values().length];
    private int furthestZone;
    private int flights;
    private int gates;
    private int rings;

    /** Reads saved records, or starts empty. */
    static Records load(SceneStorage storage) {
        Records records = new Records();
        String text = storage.read(FILE).orElse("");
        if (text.isBlank()) return records;
        try {
            VersionedSettings saved = VersionedSettings.parse(text).requireVersion(VERSION);
            for (Mode mode : Mode.values()) {
                records.best[mode.ordinal()] = number(saved, "best." + mode.name());
            }
            records.furthestZone = Math.min(Zone.TOUR_LENGTH - 1, number(saved, "furthestZone"));
            records.flights = number(saved, "flights");
            records.gates = number(saved, "gates");
            records.rings = number(saved, "rings");
        } catch (RuntimeException unreadable) {
            return new Records();
        }
        return records;
    }

    void save(SceneStorage storage) {
        VersionedSettings out = VersionedSettings.empty(VERSION);
        for (Mode mode : Mode.values()) {
            out = out.with("best." + mode.name(), Integer.toString(best[mode.ordinal()]));
        }
        out = out.with("furthestZone", Integer.toString(furthestZone))
                .with("flights", Integer.toString(flights))
                .with("gates", Integer.toString(gates))
                .with("rings", Integer.toString(rings));
        storage.write(FILE, out.serialize());
    }

    /**
     * Records a finished flight and reports whether it set a new best. Practice flights (which
     * start past Angel Island) count towards the totals but never set a best.
     */
    boolean finish(Run run) {
        flights++;
        gates += run.score();
        rings += run.ringsTaken();
        int reached = Math.min(Zone.TOUR_LENGTH - 1, Zone.tourIndex(Math.max(0, run.firstGate + run.score())));
        if (Zone.lap(run.firstGate + run.score()) > 0) reached = Zone.TOUR_LENGTH - 1;
        furthestZone = Math.max(furthestZone, reached);
        if (run.practice) return false;
        int slot = run.mode.ordinal();
        if (run.score() > best[slot]) {
            best[slot] = run.score();
            return true;
        }
        return false;
    }

    int best(Mode mode) { return best[mode.ordinal()]; }
    int furthestZone() { return furthestZone; }
    int flights() { return flights; }
    int gates() { return gates; }
    int rings() { return rings; }

    private static int number(VersionedSettings settings, String key) {
        String value = settings.entries().get(key);
        if (value == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
