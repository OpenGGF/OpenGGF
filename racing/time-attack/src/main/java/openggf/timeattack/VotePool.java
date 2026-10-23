package openggf.timeattack;

import com.openggf.net.identity.DisplayNames;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * A custom round-end vote pool the room owner saved for one game: a name and that game's acts as
 * {@code zone:act} engine ints. The pool is game-local (the game is the settings key), so it cannot
 * mix games. Names are canonical display text ({@link DisplayNames#sanitize}: NFC, single spaces,
 * at most 20 code points). Whether the acts are eligible is checked against the catalog when the
 * pool is edited and again when a room is created from it.
 *
 * @param name canonical, non-empty
 * @param acts distinct {@code zone:act} entries, at most {@link #MAX_ACTS}
 */
public record VotePool(String name, List<String> acts) {
    /** A room's vote pool carries at most 32 track keys. */
    public static final int MAX_ACTS = 32;

    public VotePool {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || !DisplayNames.isCanonical(name)) {
            throw new IllegalArgumentException("pool name must be canonical display text");
        }
        acts = List.copyOf(Objects.requireNonNull(acts, "acts"));
        if (acts.size() > MAX_ACTS) {
            throw new IllegalArgumentException("a pool holds at most " + MAX_ACTS + " acts");
        }
        if (new LinkedHashSet<>(acts).size() != acts.size()) {
            throw new IllegalArgumentException("duplicate act in pool");
        }
        for (String act : acts) {
            if (!isActText(act)) {
                throw new IllegalArgumentException("bad act " + act);
            }
        }
    }

    /** True for {@code zone:act} with 1-3 digit engine ints. */
    public static boolean isActText(String act) {
        return act != null && act.matches("[0-9]{1,3}:[0-9]{1,3}");
    }
}
