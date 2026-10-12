package infinite;

import com.openggf.mods.ModStorage;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Per-zone top-10 scores in owner-private storage. The host reads the old
 * {@code saves/infinite-sonic/leaderboard.txt} location when no owned file exists.
 * One run owns at most one entry: a run submits again after each death (and on leaving),
 * replacing its earlier entry, so CONTINUE and rewind never list the same run twice.
 * Not part of rewind: the table is the player's record, not course state.
 */
public final class Leaderboard {
    public static final int SIZE = 10;

    /** {@code run} identifies the run that submitted it this session; 0 for entries read from disk. */
    public record Entry(int zone, int score, int speedHundredths, long run) {
        public double speed() { return speedHundredths / 100.0; }
    }

    private final ModStorage storage;
    private boolean dirty;
    private boolean writable = true;
    private String notice;
    private List<Entry> entries;

    public Leaderboard(ModStorage storage) { this.storage = java.util.Objects.requireNonNull(storage); }

    /** The zone's best scores, highest first, at most {@link #SIZE}. */
    public synchronized List<Entry> top(int zone) {
        return load().stream().filter(e -> e.zone() == zone)
                .sorted(Comparator.comparingInt(Entry::score).reversed()).limit(SIZE).toList();
    }

    /** The zone's top score, ignoring {@code run}'s own entry; 0 when there is none. */
    public synchronized int best(int zone, long run) {
        return load().stream().filter(e -> e.zone() == zone && e.run() != run)
                .mapToInt(Entry::score).max().orElse(0);
    }

    /**
     * Records (or raises) {@code run}'s score and returns its 1-based rank in the zone's
     * top {@link #SIZE}, or 0 when it does not place. Zero scores are not recorded.
     */
    public synchronized int submit(int zone, long run, int score, double speed) {
        if (zone < 0 || zone >= InfiniteSonicMod.COURSE_ZONES || !Double.isFinite(speed)
                || speed <= 0 || speed * 100 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid leaderboard submission");
        if (score <= 0) return 0;
        var all = new ArrayList<>(load());
        Entry previous = all.stream().filter(e -> e.run() == run && e.zone() == zone).findFirst().orElse(null);
        if (previous != null && previous.score() >= score) return rank(zone, run);
        all.remove(previous);
        all.add(new Entry(zone, score, (int) Math.round(speed * 100), run));
        // Keep only each zone's top entries.
        var kept = new ArrayList<Entry>();
        for (int z : all.stream().mapToInt(Entry::zone).distinct().toArray()) {
            all.stream().filter(e -> e.zone() == z)
                    .sorted(Comparator.comparingInt(Entry::score).reversed()).limit(SIZE).forEach(kept::add);
        }
        entries = kept;
        dirty = true;
        flush();
        return rank(zone, run);
    }

    /** {@code run}'s 1-based rank in the zone's table, or 0 when it is not listed. */
    public synchronized int rank(int zone, long run) {
        var top = top(zone);
        for (int i = 0; i < top.size(); i++) if (top.get(i).run() == run) return i + 1;
        return 0;
    }

    private List<Entry> load() {
        if (entries != null) return entries;
        entries = new ArrayList<>();
        try {
            var text = storage.read("leaderboard.txt");
            if (text.isEmpty()) {
                writable = !storage.list().contains("leaderboard.txt");
                if (!writable) notice = "LEADERBOARD PRESERVED; RECORDS KEPT IN MEMORY";
                return entries;
            }
            for (String line : text.get().split("\\R")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3 || line.startsWith("#")) continue;
                try {
                    int zone = Integer.parseInt(parts[0]);
                    int score = Integer.parseInt(parts[1]);
                    int speed = Integer.parseInt(parts[2]);
                    if (zone >= 0 && zone < InfiniteSonicMod.COURSE_ZONES && score > 0 && speed > 0)
                        entries.add(new Entry(zone, score, speed, 0));
                } catch (NumberFormatException ignored) { /* Retain valid legacy rows. */ }
            }
            var kept = new ArrayList<Entry>();
            for (int zone = 0; zone < InfiniteSonicMod.COURSE_ZONES; zone++) {
                final int z = zone;
                entries.stream().filter(e -> e.zone() == z).sorted(Comparator.comparingInt(Entry::score)
                        .reversed()).limit(SIZE).forEach(kept::add);
            }
            entries = kept;
        } catch (UncheckedIOException unavailable) {
            writable = false;
            notice = "LEADERBOARD UNAVAILABLE; RECORDS KEPT IN MEMORY";
        }
        return entries;
    }

    public synchronized boolean dirty() { return dirty; }
    public synchronized String persistenceNotice() { return notice; }

    /** Retry publication without submitting or counting the run again. */
    public synchronized boolean flush() {
        if (!dirty) return true;
        if (!writable) return false;
        var text = new StringBuilder("# Infinite Sonic leaderboard: zone score speed-x100\n");
        for (Entry e : entries) text.append(e.zone()).append(' ').append(e.score()).append(' ')
                .append(e.speedHundredths()).append('\n');
        try {
            if (storage.write("leaderboard.txt", text.toString())) {
                dirty = false; notice = null; return true;
            }
        } catch (UncheckedIOException unavailable) { /* Keep the same in-memory table for retry. */ }
        notice = "COULD NOT SAVE LEADERBOARD; RETRYING";
        return false;
    }
}
