package flappytails;

import com.openggf.mods.scene.SceneStorage;
import com.openggf.mods.state.VersionedSettings;

/** Lifetime records. Rejected originals remain read-only; failed publication stays dirty. */
final class Records {
    static final String FILE = "records.txt";
    private static final int VERSION = 1;
    enum LoadDisposition { ABSENT, VALID, INCOMPATIBLE, CORRUPT }
    private LoadDisposition disposition = LoadDisposition.ABSENT;
    private boolean dirty;
    private final int[] best = new int[Mode.values().length];
    private int furthestZone;
    private int flights;
    private int gates;
    private int rings;

    static Records load(SceneStorage storage) {
        Records records = new Records();
        try {
            var input = storage.read(FILE);
            if (input.isEmpty() && !storage.list().contains(FILE)) return records;
            records.disposition = LoadDisposition.CORRUPT;
            String text = input.orElse("");
            var keys = new java.util.HashSet<String>();
            for (String line : text.lines().toList()) {
                int equals = line.indexOf('=');
                if (equals <= 0 || !keys.add(line.substring(0, equals).trim())) return records;
            }
            VersionedSettings saved = VersionedSettings.parse(text);
            if (!saved.entries().containsKey(VersionedSettings.VERSION_KEY)) return records;
            if (saved.version() != VERSION) {
                records.disposition = LoadDisposition.INCOMPATIBLE;
                return records;
            }
            for (Mode mode : Mode.values()) records.best[mode.ordinal()] = number(saved, "best." + mode.name());
            records.furthestZone = Math.min(Zone.TOUR_LENGTH - 1, number(saved, "furthestZone"));
            records.flights = number(saved, "flights");
            records.gates = number(saved, "gates");
            records.rings = number(saved, "rings");
            records.disposition = LoadDisposition.VALID;
            return records;
        } catch (RuntimeException unreadable) {
            Records rejected = new Records();
            rejected.disposition = LoadDisposition.CORRUPT;
            return rejected;
        }
    }

    /** True only after publication (or when there is nothing dirty). Never replaces rejected input. */
    boolean save(SceneStorage storage) {
        if (!saveEligible()) return false;
        if (!dirty) return true;
        VersionedSettings out = VersionedSettings.empty(VERSION);
        for (Mode mode : Mode.values()) out = out.with("best." + mode.name(), Integer.toString(best[mode.ordinal()]));
        out = out.with("furthestZone", Integer.toString(furthestZone)).with("flights", Integer.toString(flights))
                .with("gates", Integer.toString(gates)).with("rings", Integer.toString(rings));
        try {
            if (!storage.write(FILE, out.serialize())) return false;
            dirty = false;
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    /** Practice counts toward totals, independently of its starting zone, without a best or medal. */
    boolean finish(Run run) {
        dirty = true;
        flights = total(flights, 1);
        gates = total(gates, run.score());
        rings = total(rings, run.ringsTaken());
        int reached = Math.min(Zone.TOUR_LENGTH - 1, Zone.tourIndex(Math.max(0, run.firstGate + run.score())));
        if (Zone.lap(run.firstGate + run.score()) > 0) reached = Zone.TOUR_LENGTH - 1;
        furthestZone = Math.max(furthestZone, reached);
        if (run.practice) return false;
        int slot = run.mode.ordinal();
        if (run.score() > best[slot]) { best[slot] = run.score(); return true; }
        return false;
    }

    boolean saveEligible() { return disposition == LoadDisposition.ABSENT || disposition == LoadDisposition.VALID; }
    LoadDisposition loadDisposition() { return disposition; }
    boolean dirty() { return dirty; }
    int best(Mode mode) { return best[mode.ordinal()]; }
    int furthestZone() { return furthestZone; }
    int flights() { return flights; }
    int gates() { return gates; }
    int rings() { return rings; }
    private static int total(int previous, int added) { return (int) Math.min(Integer.MAX_VALUE, (long) previous + added); }

    private static int number(VersionedSettings settings, String key) {
        String value = settings.entries().get(key);
        if (value == null) return 0; // supported additive V1 omissions
        int number = Integer.parseInt(value.trim());
        if (number < 0) throw new IllegalArgumentException("Negative record");
        return number;
    }
}
