package sitarhero.model;

import java.util.HashSet;
import java.util.List;

/** An authored native-world stop; every required song clears it and side gigs never gate it. */
public record CareerWorld(String id, String title, List<String> requiredSongIds, List<String> sideSongIds) {
    public CareerWorld {
        validateId(id); validateText(title, 80);
        requiredSongIds = List.copyOf(requiredSongIds); sideSongIds = List.copyOf(sideSongIds);
        if (requiredSongIds.isEmpty() || requiredSongIds.size() + sideSongIds.size() > 79)
            throw new IllegalArgumentException("Invalid world setlist size");
        var assigned = new HashSet<String>();
        for (List<String> songs : List.of(requiredSongIds, sideSongIds)) for (String song : songs) {
            validateId(song);
            if (!assigned.add(song)) throw new IllegalArgumentException("Duplicate world song");
        }
    }

    static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid career ID");
    }
    static void validateText(String text, int max) {
        if (text == null || text.isBlank() || text.length() > max || !text.matches("[ -~]+"))
            throw new IllegalArgumentException("Invalid career label");
    }
}
