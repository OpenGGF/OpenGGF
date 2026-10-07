package sitarhero.model;

import java.util.List;

/** Unified song library from any nonempty subset of the three supported ROMs. */
public final class SongCatalog {
    private SongCatalog() { }

    public static List<SongSpec> all() {
        return List.of(
                // First complete melodic cycle, measured through the production ROM
                // sequencer: GHZ intro+loop132beats; CPZ intro+loop144; AIZ loop144.
                new SongSpec("green-hill", "Green Hill", "s1", 0x81, 3168, 0, 0),
                new SongSpec("chemical-plant", "Chemical Plant", "s2", 0x8C, 3717, 1, 0),
                new SongSpec("angel-island-1", "Angel Island Act 1", "s3k", 0x01, 3932, 0, 0));
    }

    public static List<SongSpec> available(List<String> installedGames) {
        return all().stream().filter(song -> installedGames.contains(song.game())).toList();
    }
}
