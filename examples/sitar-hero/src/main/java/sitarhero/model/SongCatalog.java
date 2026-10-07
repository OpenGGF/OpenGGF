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
                // Backdrops use the engine's progression indices, not ROM zone IDs.
                // New arrangements are explicitly finite 60-second excerpts; they
                // make no claim that a complete SMPS loop fits this duration.
                new SongSpec("marble", "Marble", "s1", 0x83, 3600, 1, 0),
                new SongSpec("spring-yard", "Spring Yard", "s1", 0x85, 3600, 2, 0),
                new SongSpec("labyrinth", "Labyrinth", "s1", 0x82, 3600, 3, 0),
                // S2 REV01 driver IDs differ from the disassembly's file names.
                // Keep CPZ as S2's existing opening selection; EHZ introduces
                // paired melodic chords before ARZ's fills and CNZ's syncopation.
                new SongSpec("chemical-plant", "Chemical Plant", "s2", 0x8C, 3717, 1, 0),
                new SongSpec("emerald-hill", "Emerald Hill", "s2", 0x81, 3600, 0, 0),
                new SongSpec("aquatic-ruin", "Aquatic Ruin", "s2", 0x86, 3600, 2, 0),
                new SongSpec("casino-night", "Casino Night", "s2", 0x83, 3600, 3, 0),
                new SongSpec("angel-island-1", "Angel Island Act 1", "s3k", 0x01, 3932, 0, 0),
                new SongSpec("hydrocity-1", "Hydrocity Act 1", "s3k", 0x03, 3600, 1, 0),
                new SongSpec("marble-garden-1", "Marble Garden Act 1", "s3k", 0x05, 3600, 2, 0),
                new SongSpec("flying-battery-1", "Flying Battery Act 1", "s3k", 0x09, 3600, 4, 0));
    }

    public static List<SongSpec> available(List<String> installedGames) {
        return all().stream().filter(song -> installedGames.contains(song.game())).toList();
    }
}
