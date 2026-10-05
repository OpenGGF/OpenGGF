package infinite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-zone top-10 scores, kept on disk under the engine's save root
 * ({@code saves/infinite-sonic/leaderboard.txt} by default) so they survive restarts.
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

    private final Path file;
    private List<Entry> entries;

    public Leaderboard(Path file) { this.file = file; }

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
        save();
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
        if (file == null || !Files.isRegularFile(file)) return entries;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3 || line.startsWith("#")) continue;
                try {
                    entries.add(new Entry(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), 0));
                } catch (NumberFormatException ignored) {
                    // Skip a damaged line rather than losing the rest of the table.
                }
            }
        } catch (IOException e) {
            warn(Level.WARNING, "Could not read Infinite Sonic leaderboard " + file, e);
        }
        return entries;
    }

    // Mod classes may not hold static state, so the logger is looked up when needed.
    private static void warn(Level level, String message, Throwable error) {
        Logger.getLogger(Leaderboard.class.getName()).log(level, message, error);
    }

    private void save() {
        if (file == null) return;
        var text = new StringBuilder("# Infinite Sonic leaderboard: zone score speed-x100\n");
        for (Entry e : entries) text.append(e.zone()).append(' ').append(e.score()).append(' ')
                .append(e.speedHundredths()).append('\n');
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            warn(Level.WARNING, "Could not save Infinite Sonic leaderboard " + file, e);
        }
    }
}
