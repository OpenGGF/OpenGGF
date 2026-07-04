package com.openggf.game.timeattack;

import java.util.List;
import java.util.Map;

/**
 * Curated v1 Time Attack track list (solo ghost racing phase-1 spec &sect;2):
 * signpost-terminated acts only, boss/capsule acts excluded.
 *
 * <p>{@code zone}/{@code act} are the ENGINE ints taken by
 * {@code LevelManager.loadZoneAndAct(zone, act)} for that game &mdash; not ROM
 * zone bytes. See {@code Sonic1ZoneConstants}, {@code Sonic2ZoneConstants},
 * and {@code Sonic3kZoneConstants} for the authoritative mapping;
 * {@code TestTimeAttackTrackCatalogRomValidation} in the test tree loads
 * every entry here headless against a real ROM to catch a wrong int.
 */
public final class TimeAttackTrackCatalog {

    /** One selectable Time Attack track. */
    public record Track(String gameId, int zone, int act, String label, List<String> characters) {
        public Track {
            characters = List.copyOf(characters);
        }
    }

    private static final List<String> SONIC_ONLY = List.of("sonic");
    private static final List<String> SONIC_TAILS = List.of("sonic", "tails");
    private static final List<String> SONIC_TAILS_KNUCKLES = List.of("sonic", "tails", "knuckles");

    // Sonic1ZoneConstants: GHZ=0, MZ=1, SYZ=2, LZ=3, SLZ=4, SBZ=5.
    private static final List<Track> S1_TRACKS = List.of(
            new Track("s1", 0, 0, "GREEN HILL 1", SONIC_ONLY),
            new Track("s1", 0, 1, "GREEN HILL 2", SONIC_ONLY),
            new Track("s1", 1, 0, "MARBLE 1", SONIC_ONLY),
            new Track("s1", 1, 1, "MARBLE 2", SONIC_ONLY),
            new Track("s1", 2, 0, "SPRING YARD 1", SONIC_ONLY),
            new Track("s1", 2, 1, "SPRING YARD 2", SONIC_ONLY),
            new Track("s1", 3, 0, "LABYRINTH 1", SONIC_ONLY),
            new Track("s1", 3, 1, "LABYRINTH 2", SONIC_ONLY),
            new Track("s1", 4, 0, "STAR LIGHT 1", SONIC_ONLY),
            new Track("s1", 4, 1, "STAR LIGHT 2", SONIC_ONLY),
            new Track("s1", 5, 0, "SCRAP BRAIN 1", SONIC_ONLY));

    // Sonic2ZoneConstants (engine loadZoneAndAct index, NOT the ROM zone byte):
    // EHZ=0, CPZ=1, ARZ=2, CNZ=3, HTZ=4, MCZ=5, OOZ=6, MTZ=7, SCZ=8, WFZ=9, DEZ=10.
    private static final List<Track> S2_TRACKS = List.of(
            new Track("s2", 0, 0, "EMERALD HILL 1", SONIC_TAILS),
            new Track("s2", 1, 0, "CHEMICAL PLANT 1", SONIC_TAILS),
            new Track("s2", 2, 0, "AQUATIC RUIN 1", SONIC_TAILS),
            new Track("s2", 3, 0, "CASINO NIGHT 1", SONIC_TAILS),
            new Track("s2", 4, 0, "HILL TOP 1", SONIC_TAILS),
            new Track("s2", 5, 0, "MYSTIC CAVE 1", SONIC_TAILS),
            new Track("s2", 6, 0, "OIL OCEAN 1", SONIC_TAILS),
            new Track("s2", 7, 0, "METROPOLIS 1", SONIC_TAILS),
            new Track("s2", 7, 1, "METROPOLIS 2", SONIC_TAILS));

    // Sonic3kZoneConstants: AIZ=0, HCZ=1, MGZ=2, CNZ=3, ICZ=5, MHZ=7 (act-1 signpost acts of the stable zones).
    private static final List<Track> S3K_TRACKS = List.of(
            new Track("s3k", 0, 0, "ANGEL ISLAND 1", SONIC_TAILS_KNUCKLES),
            new Track("s3k", 1, 0, "HYDROCITY 1", SONIC_TAILS_KNUCKLES),
            new Track("s3k", 2, 0, "MARBLE GARDEN 1", SONIC_TAILS_KNUCKLES),
            new Track("s3k", 3, 0, "CARNIVAL NIGHT 1", SONIC_TAILS_KNUCKLES),
            new Track("s3k", 5, 0, "ICECAP 1", SONIC_TAILS_KNUCKLES),
            new Track("s3k", 7, 0, "MUSHROOM HILL 1", SONIC_TAILS_KNUCKLES));

    private static final Map<String, List<Track>> BY_GAME = Map.of(
            "s1", S1_TRACKS,
            "s2", S2_TRACKS,
            "s3k", S3K_TRACKS);

    private TimeAttackTrackCatalog() {
    }

    public static List<Track> tracksFor(String gameId) {
        return BY_GAME.getOrDefault(gameId, List.of());
    }
}
