package openggf.timeattack;

import java.util.List;

/**
 * Curated v1 Time Attack track list (solo ghost racing phase-1 spec &sect;2):
 * signpost-terminated acts only, boss/capsule acts excluded.
 *
 * <p>{@code zone}/{@code act} are the ENGINE ints the engine's level loader takes for that
 * game &mdash; not ROM zone bytes. See the engine's {@code Sonic1ZoneConstants},
 * {@code Sonic2ZoneConstants} and {@code Sonic3kZoneConstants} for the mapping; the track
 * validation test loads every entry headless against a real ROM to catch a wrong int.
 *
 * <p>The mod validator admits no non-literal static state, so the lists are built per call.
 */
public final class TimeAttackTrackCatalog {

    /** One selectable Time Attack track. */
    public record Track(String gameId, int zone, int act, String label, List<String> characters) {
        public Track {
            characters = List.copyOf(characters);
        }
    }

    private TimeAttackTrackCatalog() {
    }

    public static List<Track> tracksFor(String gameId) {
        return switch (gameId) {
            case "s1" -> s1Tracks();
            case "s2" -> s2Tracks();
            case "s3k" -> s3kTracks();
            default -> List.of();
        };
    }

    // Sonic1ZoneConstants: GHZ=0, MZ=1, SYZ=2, LZ=3, SLZ=4, SBZ=5.
    private static List<Track> s1Tracks() {
        List<String> sonic = List.of("sonic");
        return List.of(
                new Track("s1", 0, 0, "GREEN HILL 1", sonic),
                new Track("s1", 0, 1, "GREEN HILL 2", sonic),
                new Track("s1", 1, 0, "MARBLE 1", sonic),
                new Track("s1", 1, 1, "MARBLE 2", sonic),
                new Track("s1", 2, 0, "SPRING YARD 1", sonic),
                new Track("s1", 2, 1, "SPRING YARD 2", sonic),
                new Track("s1", 3, 0, "LABYRINTH 1", sonic),
                new Track("s1", 3, 1, "LABYRINTH 2", sonic),
                new Track("s1", 4, 0, "STAR LIGHT 1", sonic),
                new Track("s1", 4, 1, "STAR LIGHT 2", sonic),
                new Track("s1", 5, 0, "SCRAP BRAIN 1", sonic));
    }

    // Sonic2ZoneConstants (engine loadZoneAndAct index, NOT the ROM zone byte):
    // EHZ=0, CPZ=1, ARZ=2, CNZ=3, HTZ=4, MCZ=5, OOZ=6, MTZ=7, SCZ=8, WFZ=9, DEZ=10.
    private static List<Track> s2Tracks() {
        List<String> team = List.of("sonic", "tails");
        return List.of(
                new Track("s2", 0, 0, "EMERALD HILL 1", team),
                new Track("s2", 1, 0, "CHEMICAL PLANT 1", team),
                new Track("s2", 2, 0, "AQUATIC RUIN 1", team),
                new Track("s2", 3, 0, "CASINO NIGHT 1", team),
                new Track("s2", 4, 0, "HILL TOP 1", team),
                new Track("s2", 5, 0, "MYSTIC CAVE 1", team),
                new Track("s2", 6, 0, "OIL OCEAN 1", team),
                new Track("s2", 7, 0, "METROPOLIS 1", team),
                new Track("s2", 7, 1, "METROPOLIS 2", team));
    }

    // Sonic3kZoneConstants: AIZ=0, HCZ=1, MGZ=2, CNZ=3, ICZ=5, MHZ=7 (act-1 signpost acts of the stable zones).
    private static List<Track> s3kTracks() {
        List<String> team = List.of("sonic", "tails", "knuckles");
        return List.of(
                new Track("s3k", 0, 0, "ANGEL ISLAND 1", team),
                new Track("s3k", 1, 0, "HYDROCITY 1", team),
                new Track("s3k", 2, 0, "MARBLE GARDEN 1", team),
                new Track("s3k", 3, 0, "CARNIVAL NIGHT 1", team),
                new Track("s3k", 5, 0, "ICECAP 1", team),
                new Track("s3k", 7, 0, "MUSHROOM HILL 1", team));
    }
}
