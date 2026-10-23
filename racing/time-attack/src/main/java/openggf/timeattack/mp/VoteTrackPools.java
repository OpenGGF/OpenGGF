package openggf.timeattack.mp;

import openggf.timeattack.TimeAttackTrackCatalog;
import openggf.timeattack.VotePool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Round-end vote pools as engine-free protocol track keys ({@code game:zone:act}): the curated
 * default pool of each game, a saved custom pool, and the validation every hosted pool passes
 * before a room is created. Pools draw from the room's own game only; every key must name an act
 * of {@link TimeAttackTrackCatalog#eligibleFor} (an act ending in the results tally).
 */
public final class VoteTrackPools {
    /** Most keys a room may carry ({@code RoomCreate} admits 32). */
    public static final int MAX_KEYS = 32;
    /** Fewest acts a custom pool holds, so a vote still offers a choice after any of them. */
    public static final int MIN_KEYS = 3;

    private VoteTrackPools() {
    }

    /** The curated default pool of {@code gameId}. */
    public static List<String> forGame(String gameId) {
        return TimeAttackTrackCatalog.tracksFor(gameId).stream()
                .map(TimeAttackTrackCatalog.Track::key)
                .toList();
    }

    /** The protocol keys of a saved pool of {@code gameId} (its acts are {@code zone:act}). */
    public static List<String> keysOf(String gameId, VotePool pool) {
        List<String> keys = new ArrayList<>();
        for (String act : pool.acts()) {
            keys.add(gameId + ":" + act);
        }
        return List.copyOf(keys);
    }

    /**
     * Why {@code keys} cannot be a room's vote pool for {@code gameId}, or empty when it can.
     * Checked: the 32-key limit, the minimum size, the key grammar, the room's game, the eligible
     * catalog, duplicates and, for a locked room, that the locked character may race every act.
     */
    public static Optional<String> rejection(String gameId, List<String> keys, String lockedCharacter) {
        if (keys == null || keys.isEmpty()) {
            return Optional.of("The vote pool is empty");
        }
        if (keys.size() > MAX_KEYS) {
            return Optional.of("The vote pool has " + keys.size() + " acts; rooms carry at most " + MAX_KEYS);
        }
        if (keys.size() < MIN_KEYS) {
            return Optional.of("The vote pool needs at least " + MIN_KEYS + " acts");
        }
        Set<String> seen = new HashSet<>();
        for (String key : keys) {
            String[] parts = key == null ? new String[0] : key.split(":", -1);
            if (parts.length != 3 || !parts[1].matches("[0-9]{1,3}") || !parts[2].matches("[0-9]{1,3}")) {
                return Optional.of("Malformed vote track \"" + key + "\" (expected game:zone:act)");
            }
            if (!parts[0].equals(gameId)) {
                return Optional.of("Vote track " + key + " is not a " + gameId.toUpperCase() + " act");
            }
            Optional<TimeAttackTrackCatalog.Track> track = TimeAttackTrackCatalog.forKey(key);
            if (track.isEmpty()) {
                return Optional.of("Vote track " + key + " is not an act Time Attack can finish");
            }
            if (!seen.add(key)) {
                return Optional.of(track.get().label() + " is in the vote pool twice");
            }
            if (lockedCharacter != null && !track.get().characters().contains(lockedCharacter)) {
                return Optional.of(track.get().label() + " cannot be raced as " + lockedCharacter.toUpperCase());
            }
        }
        return Optional.empty();
    }
}
