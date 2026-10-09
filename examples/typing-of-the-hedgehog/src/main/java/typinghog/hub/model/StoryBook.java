package typinghog.hub.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The story: one chapter per game, one stop per zone (the act holding the zone's boss), read
 * from {@code hub/story.txt} as {@code game zone act code title} lines in play order ({@code code}
 * is the map's short label, such as GHZ). Zones and acts are the running game's zone registry
 * indices (act 0 is act 1). Engine-free.
 *
 * <p>Progression: a chapter's first stop is always open; every later stop opens once each
 * earlier <em>playable</em> stop of its chapter is cleared. A stop whose act has no rail yet is
 * "under construction": shown but never playable, and it never blocks the stops after it.
 */
public final class StoryBook {
    /** One story stop. */
    public record Stop(String game, int zone, int act, String code, String title) {
        public String key() { return game + ":" + zone + ":" + act; }
    }

    /** What the map shows for a stop. */
    public enum State { CLEARED, OPEN, LOCKED, UNDER_CONSTRUCTION }

    private final List<Stop> stops;

    private StoryBook(List<Stop> stops) {
        this.stops = List.copyOf(stops);
    }

    public static StoryBook parse(String text) {
        List<Stop> out = new ArrayList<>();
        int number = 0;
        for (String raw : (text == null ? "" : text).split("\n")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+", 5);
            if (f.length != 5 || !List.of("s1", "s2", "s3k").contains(f[0])) {
                throw new IllegalArgumentException("story line " + number + " needs 'game zone act code title': " + line);
            }
            out.add(new Stop(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3], f[4].strip()));
        }
        return new StoryBook(out);
    }

    public List<Stop> stops() { return stops; }

    /** A game's chapter, in play order. */
    public List<Stop> chapter(String game) {
        List<Stop> out = new ArrayList<>();
        for (Stop stop : stops) if (stop.game().equals(game)) out.add(stop);
        return out;
    }

    /** The stop for an act, or null when the act is not a story stop. */
    public Stop find(String game, int zone, int act) {
        for (Stop stop : stops) {
            if (stop.game().equals(game) && stop.zone() == zone && stop.act() == act) return stop;
        }
        return null;
    }

    /**
     * A stop's state. {@code cleared} holds the game's cleared {@code zone:act} keys;
     * {@code playable} says whether a stop's act has a rail.
     */
    public static State state(List<Stop> chapter, int index, Set<String> cleared, Predicate<Stop> playable) {
        Stop stop = chapter.get(index);
        if (!playable.test(stop)) return State.UNDER_CONSTRUCTION;
        if (cleared.contains(Profile.act(stop.zone(), stop.act()))) return State.CLEARED;
        for (int i = 0; i < index; i++) {
            Stop before = chapter.get(i);
            if (playable.test(before) && !cleared.contains(Profile.act(before.zone(), before.act()))) {
                return State.LOCKED;
            }
        }
        return State.OPEN;
    }

    /** The first open stop, else the last cleared one, else 0: where the map cursor starts. */
    public static int resumeIndex(List<Stop> chapter, Set<String> cleared, Predicate<Stop> playable) {
        int lastCleared = -1;
        for (int i = 0; i < chapter.size(); i++) {
            State s = state(chapter, i, cleared, playable);
            if (s == State.OPEN) return i;
            if (s == State.CLEARED) lastCleared = i;
        }
        return Math.max(0, lastCleared);
    }
}
