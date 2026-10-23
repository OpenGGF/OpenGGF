package openggf.timeattack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Time Attack tracks: the curated default list ({@link #tracksFor}, solo ghost racing phase-1
 * spec &sect;2 &mdash; signpost acts of the stable zones) and the complete eligible catalog
 * ({@link #eligibleFor}): every act a Time Attack run can finish, i.e. every act that ends in the
 * game's results tally. Special and bonus stages, and acts that end in a cutscene, a seamless
 * reload into another act or the ending without a tally, are not tracks.
 *
 * <p>{@code zone}/{@code act} are the ENGINE ints the engine's level loader takes for that
 * game &mdash; not ROM zone bytes. See the engine's {@code Sonic1ZoneConstants},
 * {@code Sonic2ZoneConstants} and {@code Sonic3kZoneConstants} for the mapping; the track
 * validation test loads every entry headless against a real ROM to catch a wrong int.
 *
 * <p>Eligibility evidence (pinned disassemblies):
 * <ul>
 *   <li><b>S1</b>: the tally ({@code id_GotThroughCard}) is launched only by
 *   {@code GotThroughAct (_incObj/0D Signpost.asm:158-206)} from the signpost and by the prison
 *   capsule ({@code _incObj/3E Prison Capsule.asm:224}). Signposts are placed in GHZ1-2, MZ1-2,
 *   SYZ1-2, LZ1-2, SLZ1-2 and SBZ1-2, prison capsules in every act 3. SBZ2 tallies before its
 *   collapse cutscene ({@code Got_Bonus (_incObj/3A Got Through Card.asm:123-153)}). SBZ3
 *   ends in {@code DLE_SBZ3 (_inc/DynamicLevelEvents.asm:271-283)}, a restart into FZ with no
 *   tally; FZ ends in the ending.</li>
 *   <li><b>S2</b>: the 1P tally is launched by {@code Load_EndOfAct (s2.asm:34848-34860)}, reached
 *   from the signpost (Obj0D) and the egg prison (Obj3E, {@code s2.asm:85138}). In one-player
 *   mode the signpost parks itself in every act 2 except MTZ2 ({@code loc_19208
 *   (s2.asm:34650-34657)}), so act 2 of EHZ, CPZ, ARZ, CNZ, HTZ, MCZ and OOZ and MTZ3 end at
 *   their placed egg prison. SCZ, WFZ and DEZ have neither object and end without a tally.</li>
 *   <li><b>S3K</b>: see {@link #s3kEligible()}.</li>
 * </ul>
 *
 * <p>The mod validator admits no non-literal static state, so the lists are built per call.
 */
public final class TimeAttackTrackCatalog {

    /** How a track's act ends: the object that launches its results tally. */
    public enum Finish { SIGNPOST, CAPSULE, BOSS }

    /** One selectable Time Attack track. */
    public record Track(String gameId, int zone, int act, String label, List<String> characters,
                        Finish finish) {
        public Track {
            characters = List.copyOf(characters);
        }

        /** The engine-free protocol key ({@code game:zone:act}). */
        public String key() {
            return gameId + ":" + zone + ":" + act;
        }
    }

    private TimeAttackTrackCatalog() {
    }

    /** The curated default list (the default vote pool and the menu's tracks). */
    public static List<Track> tracksFor(String gameId) {
        List<Track> curated = new ArrayList<>();
        for (Track track : eligibleFor(gameId)) {
            if (curated(track)) {
                curated.add(track);
            }
        }
        return List.copyOf(curated);
    }

    /** Every act of {@code gameId} a Time Attack run can finish, in game order. */
    public static List<Track> eligibleFor(String gameId) {
        return switch (gameId == null ? "" : gameId) {
            case "s1" -> s1Eligible();
            case "s2" -> s2Eligible();
            case "s3k" -> s3kEligible();
            default -> List.of();
        };
    }

    /** The eligible track for {@code game:zone:act}, if there is one. */
    public static Optional<Track> eligible(String gameId, int zone, int act) {
        for (Track track : eligibleFor(gameId)) {
            if (track.zone() == zone && track.act() == act) {
                return Optional.of(track);
            }
        }
        return Optional.empty();
    }

    /** The eligible track for a protocol key ({@code game:zone:act}); empty when malformed. */
    public static Optional<Track> forKey(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 3 || !parts[1].matches("[0-9]{1,3}") || !parts[2].matches("[0-9]{1,3}")) {
            return Optional.empty();
        }
        return eligible(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /** True when {@code key} names an eligible act. */
    public static boolean isEligibleKey(String key) {
        return forKey(key).isPresent();
    }

    /**
     * The curated v1 list: the signpost acts of the stable zones. Its keys are the default vote
     * pools rooms received before custom pools existed; keep them unchanged.
     */
    private static boolean curated(Track track) {
        return switch (track.gameId()) {
            case "s1" -> track.finish() == Finish.SIGNPOST
                    && !(track.zone() == 5 && track.act() == 1);
            case "s2" -> track.finish() == Finish.SIGNPOST;
            case "s3k" -> track.act() == 0 && (track.zone() == 0 || track.zone() == 1
                    || track.zone() == 2 || track.zone() == 3 || track.zone() == 5 || track.zone() == 7);
            default -> false;
        };
    }

    // Sonic1ZoneConstants: GHZ=0, MZ=1, SYZ=2, LZ=3, SLZ=4, SBZ=5.
    private static List<Track> s1Eligible() {
        List<String> sonic = List.of("sonic");
        return List.of(
                new Track("s1", 0, 0, "GREEN HILL 1", sonic, Finish.SIGNPOST),
                new Track("s1", 0, 1, "GREEN HILL 2", sonic, Finish.SIGNPOST),
                new Track("s1", 0, 2, "GREEN HILL 3", sonic, Finish.CAPSULE),
                new Track("s1", 1, 0, "MARBLE 1", sonic, Finish.SIGNPOST),
                new Track("s1", 1, 1, "MARBLE 2", sonic, Finish.SIGNPOST),
                new Track("s1", 1, 2, "MARBLE 3", sonic, Finish.CAPSULE),
                new Track("s1", 2, 0, "SPRING YARD 1", sonic, Finish.SIGNPOST),
                new Track("s1", 2, 1, "SPRING YARD 2", sonic, Finish.SIGNPOST),
                new Track("s1", 2, 2, "SPRING YARD 3", sonic, Finish.CAPSULE),
                new Track("s1", 3, 0, "LABYRINTH 1", sonic, Finish.SIGNPOST),
                new Track("s1", 3, 1, "LABYRINTH 2", sonic, Finish.SIGNPOST),
                new Track("s1", 3, 2, "LABYRINTH 3", sonic, Finish.CAPSULE),
                new Track("s1", 4, 0, "STAR LIGHT 1", sonic, Finish.SIGNPOST),
                new Track("s1", 4, 1, "STAR LIGHT 2", sonic, Finish.SIGNPOST),
                new Track("s1", 4, 2, "STAR LIGHT 3", sonic, Finish.CAPSULE),
                new Track("s1", 5, 0, "SCRAP BRAIN 1", sonic, Finish.SIGNPOST),
                new Track("s1", 5, 1, "SCRAP BRAIN 2", sonic, Finish.SIGNPOST));
    }

    // Sonic2ZoneConstants (engine loadZoneAndAct index, NOT the ROM zone byte):
    // EHZ=0, CPZ=1, ARZ=2, CNZ=3, HTZ=4, MCZ=5, OOZ=6, MTZ=7, SCZ=8, WFZ=9, DEZ=10.
    private static List<Track> s2Eligible() {
        List<String> team = List.of("sonic", "tails");
        return List.of(
                new Track("s2", 0, 0, "EMERALD HILL 1", team, Finish.SIGNPOST),
                new Track("s2", 0, 1, "EMERALD HILL 2", team, Finish.CAPSULE),
                new Track("s2", 1, 0, "CHEMICAL PLANT 1", team, Finish.SIGNPOST),
                new Track("s2", 1, 1, "CHEMICAL PLANT 2", team, Finish.CAPSULE),
                new Track("s2", 2, 0, "AQUATIC RUIN 1", team, Finish.SIGNPOST),
                new Track("s2", 2, 1, "AQUATIC RUIN 2", team, Finish.CAPSULE),
                new Track("s2", 3, 0, "CASINO NIGHT 1", team, Finish.SIGNPOST),
                new Track("s2", 3, 1, "CASINO NIGHT 2", team, Finish.CAPSULE),
                new Track("s2", 4, 0, "HILL TOP 1", team, Finish.SIGNPOST),
                new Track("s2", 4, 1, "HILL TOP 2", team, Finish.CAPSULE),
                new Track("s2", 5, 0, "MYSTIC CAVE 1", team, Finish.SIGNPOST),
                new Track("s2", 5, 1, "MYSTIC CAVE 2", team, Finish.CAPSULE),
                new Track("s2", 6, 0, "OIL OCEAN 1", team, Finish.SIGNPOST),
                new Track("s2", 6, 1, "OIL OCEAN 2", team, Finish.CAPSULE),
                new Track("s2", 7, 0, "METROPOLIS 1", team, Finish.SIGNPOST),
                new Track("s2", 7, 1, "METROPOLIS 2", team, Finish.SIGNPOST),
                new Track("s2", 7, 2, "METROPOLIS 3", team, Finish.CAPSULE));
    }

    /**
     * S3K (Sonic3kZoneConstants: AIZ=0, HCZ=1, MGZ=2, CNZ=3, FBZ=4, ICZ=5, LBZ=6, MHZ=7, SOZ=8,
     * LRZ=9, SSZ=10, DEZ=11; the Lava Reef boss act is ROM zone $16 act 0, engine zone 22).
     *
     * <p>{@code Obj_LevelResults} is allocated only by the signpost ({@code Obj_EndSignResults
     * (sonic3k.asm:176326-176337)}, raised through {@code Obj_EndSignControl}), the egg capsule
     * ({@code sub_868F8 (sonic3k.asm:181991-182009)}, {@code loc_869AA (sonic3k.asm:182055-182066)}),
     * {@code loc_72B46 (sonic3k.asm:152165-152185)} (LBZ2's first final boss) and Mecha Sonic's
     * {@code loc_7D056} path ({@code sonic3k.asm:166764-166776}, SSZ act 1 only).
     * {@code Obj_LevelResultsWait2} ({@code loc_2DCE2 (sonic3k.asm:62738-62751)}) then ends the
     * act, or for an apparent act 1 turns it into act 2 ({@code loc_2DD06 (sonic3k.asm:62753-62768)}):
     * a hosted run of an act 1 completes at that tally, before or during the seamless reload.
     * AIZ1 and ICZ1 reach their act-1 tally after reloading into act-2 data; a direct act-2 entry
     * skips that miniboss ({@code AIZ2_SonicResize1}, {@code Obj_ICZMiniboss}'s {@code $501} check),
     * so act 2 runs reach their own capsule. Not tracks: LRZ2 (its end is the {@code Act3_flag}
     * reload into the boss act, {@code loc_63C14 (sonic3k.asm:131250-131258)}, or Knuckles'
     * {@code Obj_StartNewLevel} into HPZ), HPZ, SSZ act 2 (Knuckles' ending,
     * {@code loc_7BCFC (sonic3k.asm:165095-165115)}), DEZ2 ({@code loc_7F310
     * (sonic3k.asm:169871-169877)}), the Death Egg boss act and DDZ, which end without a tally.
     *
     * <p>Characters: Knuckles never plays the Lava Reef boss act (its cutscene object deletes itself
     * for him, {@code sonic3k.asm:131177-131179}) and goes from HPZ to SSZ act 2, never act 1 or DEZ
     * ({@code sub_DA4E (sonic3k.asm:17516-17532)}); Sonic and Tails (alone or together) play SSZ
     * act 1 and DEZ1.
     */
    private static List<Track> s3kEligible() {
        List<String> team = List.of("sonic", "tails", "knuckles");
        List<String> sonicTails = List.of("sonic", "tails");
        return List.of(
                new Track("s3k", 0, 0, "ANGEL ISLAND 1", team, Finish.SIGNPOST),
                new Track("s3k", 0, 1, "ANGEL ISLAND 2", team, Finish.CAPSULE),
                new Track("s3k", 1, 0, "HYDROCITY 1", team, Finish.SIGNPOST),
                new Track("s3k", 1, 1, "HYDROCITY 2", team, Finish.CAPSULE),
                new Track("s3k", 2, 0, "MARBLE GARDEN 1", team, Finish.SIGNPOST),
                new Track("s3k", 2, 1, "MARBLE GARDEN 2", team, Finish.CAPSULE),
                new Track("s3k", 3, 0, "CARNIVAL NIGHT 1", team, Finish.SIGNPOST),
                new Track("s3k", 3, 1, "CARNIVAL NIGHT 2", team, Finish.CAPSULE),
                new Track("s3k", 4, 0, "FLYING BATTERY 1", team, Finish.SIGNPOST),
                new Track("s3k", 4, 1, "FLYING BATTERY 2", team, Finish.CAPSULE),
                new Track("s3k", 5, 0, "ICECAP 1", team, Finish.SIGNPOST),
                new Track("s3k", 5, 1, "ICECAP 2", team, Finish.CAPSULE),
                new Track("s3k", 6, 0, "LAUNCH BASE 1", team, Finish.SIGNPOST),
                new Track("s3k", 6, 1, "LAUNCH BASE 2", team, Finish.BOSS),
                new Track("s3k", 7, 0, "MUSHROOM HILL 1", team, Finish.SIGNPOST),
                new Track("s3k", 7, 1, "MUSHROOM HILL 2", team, Finish.CAPSULE),
                new Track("s3k", 8, 0, "SANDOPOLIS 1", team, Finish.SIGNPOST),
                new Track("s3k", 8, 1, "SANDOPOLIS 2", team, Finish.CAPSULE),
                new Track("s3k", 9, 0, "LAVA REEF 1", team, Finish.SIGNPOST),
                new Track("s3k", 22, 0, "LAVA REEF BOSS", sonicTails, Finish.CAPSULE),
                new Track("s3k", 10, 0, "SKY SANCTUARY", sonicTails, Finish.BOSS),
                new Track("s3k", 11, 0, "DEATH EGG 1", sonicTails, Finish.SIGNPOST));
    }
}
