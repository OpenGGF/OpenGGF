package sitarhero.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Tour tiers follow the supplied available library's order, never unavailable-ROM songs. */
public final class CareerProgress {
    private static final int TIER_SIZE = 3;
    private final PlayerProfile profile;

    public CareerProgress(PlayerProfile profile) { this.profile = Objects.requireNonNull(profile, "profile"); }

    /** Tier one is open; every preceding tier must have two distinct clears for this role/difficulty. */
    public boolean unlocked(List<SongSpec> availableSongs, String songId, String role, String difficulty) {
        PerformanceResult.validateKey(songId, role, difficulty);
        List<SongSpec> songs = distinct(availableSongs);
        int index = -1;
        for (int i = 0; i < songs.size(); i++) if (songs.get(i).id().equals(songId)) { index = i; break; }
        if (index < 0) return false;
        for (int tier = 0; tier < index / TIER_SIZE; tier++) {
            int count = 0;
            for (int i = tier * TIER_SIZE; i < (tier + 1) * TIER_SIZE; i++)
                if (isClear(songs.get(i).id(), role, difficulty)) count++;
            if (count < 2) return false;
        }
        return true;
    }

    public int cleared(List<SongSpec> availableSongs, String role, String difficulty) {
        PerformanceResult.validateSelection(role, difficulty);
        int count = 0;
        for (SongSpec song : distinct(availableSongs)) if (isClear(song.id(), role, difficulty)) count++;
        return count;
    }

    /** All supplied distinct songs cleared; an empty library is not a completed tour. */
    public boolean complete(List<SongSpec> availableSongs, String role, String difficulty) {
        PerformanceResult.validateSelection(role, difficulty);
        List<SongSpec> songs = distinct(availableSongs);
        return !songs.isEmpty() && cleared(songs, role, difficulty) == songs.size();
    }

    /** Shared stage label for each group of three, with further encores for a larger future library. */
    public String venue(List<SongSpec> availableSongs, String songId) {
        List<SongSpec> songs = distinct(availableSongs);
        for (int i = 0; i < songs.size(); i++) if (songs.get(i).id().equals(songId)) {
            int tier = i / TIER_SIZE;
            return switch (tier) {
                case 0 -> "Garage Sessions"; case 1 -> "Club Circuit";
                case 2 -> "Festival Lights"; case 3 -> "World Stage";
                default -> "Encore " + (tier + 1);
            };
        }
        return "Unavailable";
    }

    private boolean isClear(String songId, String role, String difficulty) {
        return profile.bestStars(songId, role, difficulty).filter(PerformanceResult::cleared).isPresent();
    }
    private static List<SongSpec> distinct(List<SongSpec> availableSongs) {
        var songs = new LinkedHashMap<String, SongSpec>();
        for (SongSpec song : Objects.requireNonNull(availableSongs, "availableSongs")) songs.putIfAbsent(song.id(), song);
        return List.copyOf(songs.values());
    }
}
