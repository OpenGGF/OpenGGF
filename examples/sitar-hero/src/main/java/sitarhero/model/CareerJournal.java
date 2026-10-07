package sitarhero.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Earned Career progression, shared across instruments, difficulty and cosmetic performers.
 * Personal-best scores stay in PlayerProfile; no old score/profile row implies a Career clear.
 */
public final class CareerJournal {
    private static final String HEADER = "sitar-career=1";
    private static final int MAX_TEXT = 16_384;
    private static final int MAX_LINES = 128;
    private static final int MAX_LINE = 96;
    private final List<CareerTour> tours = CareerTours.all();
    private final Set<String> knownSongs = new HashSet<>();
    private final Set<String> knownScenes = new HashSet<>();
    private final Set<String> clears = new TreeSet<>();
    private final Set<String> seenScenes = new TreeSet<>();

    public CareerJournal() {
        for (CareerTour tour : tours) {
            knownScenes.add(tour.id() + "-outro");
            for (CareerWorld world : tour.worlds()) {
                knownSongs.addAll(world.requiredSongIds()); knownSongs.addAll(world.sideSongIds());
                knownScenes.add(world.id() + "-intro");
            }
        }
    }

    /**
     * Return true for a newly cleared known song. earnedCareer is the scene's completed,
     * normal Career-play boundary; quick play, practice, demos and multiplayer supply false.
     * Side gigs may earn their own clear but never count toward world/tour completion.
     */
    public boolean record(PerformanceResult result, boolean earnedCareer) {
        return earnedCareer && result != null && result.cleared() && knownSongs.contains(result.songId()) && clears.add(result.songId());
    }

    public boolean cleared(String songId) { return knownSongs.contains(songId) && clears.contains(songId); }

    /** Counts only the required songs of a canonical authored world. */
    public int cleared(CareerWorld world) {
        if (!knownWorld(world)) return 0;
        int count = 0;
        for (String id : world.requiredSongIds()) if (clears.contains(id)) count++;
        return count;
    }

    /** Counts only required songs; optional side clears cannot inflate the tour's total. */
    public int cleared(CareerTour tour) {
        if (tour == null || !tours.contains(tour)) return 0;
        int count = 0;
        for (CareerWorld world : tour.worlds()) count += cleared(world);
        return count;
    }

    public boolean complete(CareerWorld world) {
        return knownWorld(world) && cleared(world) == world.requiredSongIds().size();
    }
    public boolean complete(CareerTour tour) {
        return tour != null && tours.contains(tour) && tour.worlds().stream().allMatch(this::complete);
    }

    /** The first native world is open; all earlier worlds must have every required song cleared. */
    public boolean unlocked(CareerTour tour, CareerWorld world) {
        if (tour == null || world == null || !tours.contains(tour)) return false;
        int index = tour.worlds().indexOf(world);
        if (index < 0) return false;
        for (int i = 0; i < index; i++) if (!complete(tour.worlds().get(i))) return false;
        return true;
    }

    public boolean seen(String sceneId) { return knownScenes.contains(sceneId) && seenScenes.contains(sceneId); }
    /** Return true only on the first mark of a known world intro or tour outro. */
    public boolean markSeen(String sceneId) { return knownScenes.contains(sceneId) && seenScenes.add(sceneId); }

    /** V1 schema: header, sorted clear=song-id rows, then sorted seen=scene-id rows. */
    public String encode() {
        var text = new StringBuilder(HEADER).append('\n');
        for (String id : clears) text.append("clear=").append(id).append('\n');
        for (String id : seenScenes) text.append("seen=").append(id).append('\n');
        return text.toString();
    }

    /**
     * Atomically replace from valid V1 input. Invalid/null/unsupported text preserves the
     * current journal. Only the 79 authored song IDs and 38 scene IDs are accepted;
     * duplicates, unknown fields/IDs, blank rows and text beyond the explicit bounds reject.
     */
    public void read(String text) {
        if (text == null || text.length() > MAX_TEXT) return;
        var lines = text.lines().limit(MAX_LINES + 1L).toList();
        if (lines.isEmpty() || lines.size() > MAX_LINES || !lines.getFirst().equals(HEADER)) return;
        var parsedClears = new TreeSet<String>(); var parsedScenes = new TreeSet<String>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > MAX_LINE) return;
            if (line.startsWith("clear=")) {
                String id = line.substring(6);
                if (!knownSongs.contains(id) || !parsedClears.add(id)) return;
            } else if (line.startsWith("seen=")) {
                String id = line.substring(5);
                if (!knownScenes.contains(id) || !parsedScenes.add(id)) return;
            } else return;
        }
        clears.clear(); clears.addAll(parsedClears);
        seenScenes.clear(); seenScenes.addAll(parsedScenes);
    }

    private boolean knownWorld(CareerWorld world) {
        return world != null && tours.stream().anyMatch(tour -> tour.worlds().contains(world));
    }
}
