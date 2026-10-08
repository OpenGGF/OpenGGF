package sitarhero.catalogue;

import sitarhero.model.SongArrangement;
import sitarhero.model.SongArrangement.Section;

import java.util.List;

/**
 * Complete substantive songs in both music tables of the retail locked-on ROM.
 * Runtime music bytes remain exclusively ROM-backed; these are score/control-flow metadata.
 * Evidence: docs/architecture/designs/2026-10-07-sitar-hero-s3k-song-catalogue.md.
 * Channel indices are zero-based logical FM/PSG indices, never header slot ordinals.
 */
public final class Sonic3kCatalogue {
    private Sonic3kCatalogue() { }

    /** Exact progressed driver-duration units, including fractional-quarter pickups. */
    public record NativeForm(int introUnits, int loopUnits, int endUnits, int unitsPerBeat) { }
    /** Divide beat by tempoAnchorBeatDivisor(id) to obtain an exact musical quarter beat. */
    public record TempoAnchor(int beat, int serviceFrame, int unitsPerBeat) { }
    private record Parts(List<Section> lead, List<Section> rhythm, List<Section> synth) { }
    private record Entry(SongArrangement arrangement, NativeForm form) { }

    private static List<Entry> entries() {
        return List.of(
            entry("angel-island-1", "Angel Island Zone Act 1", 0x001, 0, 0,
                    0, 3933, 0, 0, 3456, 0, List.of(12, 6, 6, 12, 6, 6, 12), angelIsland()),
            entry("angel-island-2", "Angel Island Zone Act 2", 0x002, 0, 1,
                    0, 3765, 0, 0, 3456, 0, List.of(12, 12, 12, 6, 12, 6, 12), parts(1, 3, 4, 0, 1)),
            entry("hydrocity-1", "Hydrocity Zone Act 1", 0x003, 1, 0,
                    0, 3410, 0, 0, 2304, 0, List.of(20, 4, 32, 16, 20, 24, 4), parts(0, 3, 4, 0, 1)),
            entry("hydrocity-2", "Hydrocity Zone Act 2", 0x004, 1, 1,
                    0, 3143, 0, 0, 2688, 0, List.of(6, 2, 4, 6, 6, 6, 6), parts(0, 3, 4, 0, 1)),
            entry("marble-garden-1", "Marble Garden Zone Act 1", 0x005, 2, 0,
                    0, 3584, 0, 0, 2688, 0, List.of(12, 8, 4, 12, 8, 12, 4), marbleGarden()),
            entry("marble-garden-2", "Marble Garden Zone Act 2", 0x006, 2, 1,
                    0, 3529, 0, 0, 2688, 0, List.of(24, 20, 16, 12, 12, 12, 24), marbleGarden()),
            entry("carnival-night-1", "Carnival Night Zone Act 1", 0x007, 3, 0,
                    0, 4053, 0, 0, 2976, 0, List.of(12, 12, 36, 12, 12, 12, 12), parts(0, 2, 3, 0, 1)),
            entry("carnival-night-2", "Carnival Night Zone Act 2", 0x008, 3, 1,
                    0, 4053, 0, 0, 2976, 0, List.of(12, 12, 36, 12, 12, 12, 12), parts(0, 2, 3, 0, 1)),
            entry("flying-battery-1", "Flying Battery Zone Act 1 (S&K)", 0x009, 4, 0,
                    24, 2400, 0, 24, 2400, 0, List.of(6, 6, 6, 6, 24, 24, 24), parts(0, 2, 3, -1, -1)),
            entry("flying-battery-2", "Flying Battery Zone Act 2", 0x00A, 4, 1,
                    96, 2400, 0, 96, 2400, 0, List.of(6, 6, 6, 6, 24, 6, 6), parts(0, 2, 3, -1, -1)),
            entry("icecap-1", "IceCap Zone Act 1", 0x00B, 5, 0,
                    0, 4999, 0, 0, 4608, 0, List.of(24, 24, 12, 6, 6, 6, 6), parts(1, 3, 4, 0, 1)),
            entry("icecap-2", "IceCap Zone Act 2", 0x00C, 5, 1,
                    0, 5832, 0, 0, 5376, 0, List.of(384, 276, 6, 6, 6, 6, 12), parts(1, 3, 4, 0, 1)),
            entry("launch-base-1", "Launch Base Zone Act 1", 0x00D, 6, 0,
                    590, 3411, 0, 481, 2784, 0, List.of(36, 60, 6, 12, 6, 12, 6), parts(0, 2, 3, 0, 1)),
            entry("launch-base-2", "Launch Base Zone Act 2", 0x00E, 6, 1,
                    590, 3411, 0, 481, 2784, 0, List.of(36, 60, 6, 12, 6, 12, 6), parts(0, 2, 3, 0, 1)),
            entry("mushroom-hill-1", "Mushroom Hill Zone Act 1", 0x00F, 7, 0,
                    186, 2470, 0, 144, 1920, 0, List.of(12, 12, 12, 12, 12, 12, 12), mushroomHill(18)),
            entry("mushroom-hill-2", "Mushroom Hill Zone Act 2", 0x010, 7, 1,
                    119, 2364, 0, 96, 1920, 0, List.of(12, 12, 12, 12, 4, 4, 4), mushroomHill(16)),
            entry("sandopolis-1", "Sandopolis Zone Act 1", 0x011, 8, 0,
                    414, 2479, 0, 384, 2304, 0, List.of(12, 12, 12, 48, 12, 12, 12), parts(0, 2, 3, 0, -1)),
            entry("sandopolis-2", "Sandopolis Zone Act 2", 0x012, 8, 1,
                    439, 2634, 0, 384, 2304, 0, List.of(24, 24, 24, 12, 12, 24, 24), parts(0, 2, 3, -1, -1)),
            entry("lava-reef-1", "Lava Reef Zone Act 1", 0x013, 9, 0,
                    0, 2255, 0, 0, 1920, 0, List.of(12, 6, 6, 48, 24, 12, 6), lavaReef()),
            entry("lava-reef-2", "Lava Reef Zone Act 2 / Hidden Palace", 0x014, 9, 1,
                    360, 2398, 0, 288, 1920, 0, List.of(18, 6, 72, 18, 6, 72, 18), parts(0, 3, 4, 0, 1)),
            entry("sky-sanctuary", "Sky Sanctuary Zone (S&K)", 0x015, 10, 0,
                    0, 3511, 0, 0, 3456, 0, List.of(24, 24, 6, 6, 5, 5, 26), parts(4, 2, -1, 0, -1)),
            entry("death-egg-1", "Death Egg Zone Act 1", 0x016, 11, 0,
                    0, 2379, 0, 0, 2304, 0, List.of(6, 6, 6, 6, 24, 6, 6), parts(0, 2, 3, -1, -1)),
            entry("death-egg-2", "Death Egg Zone Act 2", 0x017, 11, 1,
                    0, 2304, 0, 0, 2304, 0, List.of(24, 24, 24, 24, 24, 24, 24), parts(0, 2, 3, -1, -1)),
            entry("s3k-miniboss", "Mini-Boss (S&K)", 0x018, 0, 0,
                    96, 1536, 0, 96, 1536, 0, List.of(6, 6, 24, 6, 6, 24, 6), parts(0, 2, 3, -1, -1)),
            entry("s3k-boss", "Zone Boss", 0x019, 0, 1,
                    0, 1920, 0, 0, 1920, 0, List.of(12, 12, 12, 12, 12, 12, 12), parts(0, 2, 3, 0, -1)),
            entry("doomsday", "Doomsday Zone", 0x01A, 12, 0,
                    800, 2948, 0, 768, 2832, 0, List.of(24, 36, 12, 36, 12, 24, 12), parts(0, 2, 3, 0, 1)),
            entry("s3k-pachinko", "Bonus Stage — Pachinko", 0x01B, 10, 0,
                    211, 2941, 0, 192, 2688, 0, List.of(12, 12, 12, 8, 4, 12, 12), parts(0, 2, 3, 0, -1)),
            entry("s3k-special-stage", "Blue Sphere Special Stage", 0x01C, 0, 0,
                    337, 3816, 0, 288, 3264, 0, List.of(6, 12, 6, 12, 12, 12, 12), parts(0, 2, 3, 0, 1)),
            entry("s3k-slots", "Bonus Stage — Slots", 0x01D, 10, 0,
                    439, 2634, 0, 384, 2304, 0, List.of(12, 12, 18, 6, 6, 6, 6), parts(1, 2, 3, 0, 1)),
            entry("s3k-gumball", "Bonus Stage — Gumball", 0x01E, 0, 0,
                    218, 1740, 0, 192, 1536, 0, List.of(24, 24, 24, 24, 24, 12, 18), parts(2, 3, -1, 0, 1)),
            entry("s3k-knuckles", "Knuckles’ Theme (S&K)", 0x01F, 0, 0,
                    0, 922, 0, 0, 864, 0, List.of(60, 6, 12, 6, 12, 24, 24), parts(0, 3, 4, 0, 1)),
            entry("azure-lake", "Azure Lake (Competition)", 0x020, 0, 0,
                    96, 3264, 0, 96, 3264, 0, List.of(24, 24, 24, 6, 6, 6, 6), parts(0, 2, 3, 0, -1)),
            entry("balloon-park", "Balloon Park (Competition)", 0x021, 0, 0,
                    50, 3172, 0, 48, 3072, 0, List.of(6, 6, 12, 12, 12, 24, 12), parts(0, 3, 4, 0, 1)),
            entry("desert-palace", "Desert Palace (Competition)", 0x022, 10, 0,
                    0, 2667, 0, 0, 2302, 0, List.of(24, 24, 24, 24, 18, 6, 12), parts(1, 3, 4, 0, 1)),
            entry("chrome-gadget", "Chrome Gadget (Competition)", 0x023, 6, 0,
                    0, 3592, 0, 0, 3072, 0, List.of(18, 6, 12, 6, 6, 18, 6), parts(1, 3, 4, 0, 1)),
            entry("endless-mine", "Endless Mine (Competition)", 0x024, 10, 0,
                    108, 3450, 0, 96, 3072, 0, List.of(6, 6, 84, 24, 24, 24, 12), parts(0, 2, 3, 0, -1)),
            entry("s3k-title", "Title Theme (S&K)", 0x025, 0, 0,
                    0, 0, 1141, 0, 0, 1140, List.of(6, 12, 18, 24, 12, 6, 18), parts(0, 3, 4, 0, 1)),
            entry("s3-credits", "Sonic 3 Credits", 0x026, 0, 0,
                    2921, 2921, 0, 2784, 2784, 0, List.of(24, 96, 60, 18, 18, 24, 24), parts(2, 1, -1, 0, 1)),
            entry("s3k-competition-menu", "Competition Menu", 0x02D, 0, 0,
                    0, 3241, 0, 0, 2304, 0, List.of(24, 18, 6, 12, 12, 24, 24), parts(2, 3, 4, 1, -1)),
            entry("s3k-data-select", "Data Select (S&K)", 0x02F, 0, 0,
                    56, 3584, 0, 42, 2688, 0, List.of(18, 6, 6, 12, 6, 18, 6), parts(1, 3, 4, 0, 1)),
            entry("s3k-final-boss", "Final Boss", 0x030, 10, 0,
                    964, 2527, 0, 636, 1668, 0, List.of(36, 27, 9, 36, 27, 9, 36), parts(0, 2, 3, 0, 1)),
            entry("s3k-ending", "S&K Ending / Game Complete", 0x032, 10, 0,
                    0, 0, 614, 0, 0, 594, List.of(60, 12, 12, 6, 6, 24, 12), parts(0, 3, 4, 0, 1)),
            entry("s3k-credits", "S&K Final Credits", 0x033, 10, 0,
                    0, 0, 10248, 0, 0, 9588, List.of(4, 4, 4, 5, 6, 23, 18), creditsMedley()),
            entry("flying-battery-1-s3", "Flying Battery Zone Act 1 (S3)", 0x109, 4, 0,
                    24, 2400, 0, 24, 2400, 0, List.of(6, 6, 6, 6, 24, 24, 24), parts(0, 2, 3, -1, -1)),
            entry("sky-sanctuary-s3", "Sky Sanctuary Zone (S3)", 0x115, 10, 0,
                    0, 3511, 0, 0, 3456, 0, List.of(24, 24, 6, 6, 6, 6, 24), parts(3, 2, -1, 0, -1)),
            entry("s3-knuckles", "Knuckles’ Theme (S3)", 0x11F, 0, 0,
                    2081, 2081, 0, 1536, 1536, 0, List.of(12, 8, 4, 12, 12, 8, 4), parts(0, 1, -1, -1, -1)),
            entry("s3-title", "Title Theme (S3)", 0x125, 0, 0,
                    0, 0, 973, 0, 0, 965, List.of(6, 6, 12, 18, 6, 12, 12), partsWithNoise(0, 3, -1)),
            entry("s3-miniboss", "Mini-Boss (S3)", 0x12E, 0, 0,
                    0, 3661, 0, 0, 2688, 0, List.of(12, 24, 108, 12, 8, 4, 12), parts(2, 3, 4, 0, -1)),
            entry("s3-data-select", "Data Select (S3)", 0x12F, 0, 0,
                    56, 3584, 0, 42, 2688, 0, List.of(18, 6, 6, 12, 6, 18, 6), parts(1, 3, 4, 0, 1)),
            entry("s3-ending", "Sonic 3 Ending / Game Complete", 0x132, 0, 0,
                    0, 0, 609, 0, 0, 570, List.of(54, 6, 12, 12, 6, 6, 24), partsWithNoise(0, 2, 3))
        );
    }

    public static List<SongArrangement> all() { return entries().stream().map(Entry::arrangement).toList(); }

    /** Exact units are authoritative where the compatibility record's integer beats round up. */
    public static NativeForm nativeForm(String id) {
        return entries().stream().filter(entry -> entry.arrangement().id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown S3K song: " + id)).form();
    }

    /** Launch Base's DAC/FM introduction precedes PSG1/PSG2's 480/481-unit entries. */
    public static int dacIntroUnits(String id) {
        return switch (id) {
            case "launch-base-1", "launch-base-2" -> 384;
            default -> nativeForm(id).introUnits();
        };
    }

    /** Credits contain a quarter-beat pickup at the second native tempo change. */
    public static int tempoAnchorBeatDivisor(String id) { return "s3k-credits".equals(id) ? 4 : 1; }

    /** Native FF 00 boundaries, plus the final native stop; ordinary constant-tempo songs are empty. */
    public static List<TempoAnchor> tempoAnchors(String id) {
        if (!"s3k-credits".equals(id)) return List.of();
        return List.of(new TempoAnchor(0, 0, 24), new TempoAnchor(136, 866, 24),
                new TempoAnchor(521, 3495, 24), new TempoAnchor(784, 5124, 24),
                new TempoAnchor(912, 5943, 24), new TempoAnchor(1056, 6835, 24),
                new TempoAnchor(1598, 10247, 24));
    }

    /** Named retail DAC sample families; -1 excludes speech, scratches and ambient samples. */
    public static int drumLane(int noteId) {
        // _smps2asm_inc.asm SonicDriverVer 3/4 and zDACBanks / drum descriptors.
        // B2/B3 are echoed CLAPS, not kicks; S3 Knuckles therefore has no invented kick.
        return switch (noteId) {
            case 0x86, 0x9D, 0xA1, 0xA3, 0xA4, 0xA7, 0xA8, 0xB4, 0xB5,
                    0xB7, 0xB8, 0xB9, 0xBD, 0xC0, 0xC1, 0xC2, 0xC3, 0xC4 -> 4;
            case 0x81, 0x87, 0x8F, 0x94, 0x95, 0x96, 0x97, 0x9B, 0x9F,
                    0xA6, 0xAB, 0xB2, 0xB3, 0xBC -> 0;
            case 0x84, 0x85, 0x92, 0x93, 0x99, 0xAE, 0xB0 -> 1;
            case 0x82, 0x83, 0x90, 0x91, 0x98, 0x9A, 0xAC, 0xAD, 0xAF -> 2;
            case 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x9C,
                    0x9E, 0xA0, 0xA2, 0xB1, 0xBF -> 3;
            default -> -1;
        };
    }

    private static Entry entry(String id, String label, int music, int zone, int act,
                               int introFrames, int loopFrames, int endFrames,
                               int introUnits, int loopUnits, int endUnits,
                               List<Integer> firstDac, Parts parts) {
        // Musical quarters use 24 duration units in these owning scores. Retain exact
        // pickups in NativeForm: e.g. Final Boss 636 + 1668 and Data Select 42 + 2688.
        // The shared integer fields are covering counts, not evidence of integral forms.
        int introBeats = (introUnits + 23) / 24;
        int loopBeats = Math.max(1, (Math.max(loopUnits, endUnits) + 23) / 24);
        return new Entry(new SongArrangement(id, label, "s3k", music, zone, act,
                introFrames, loopFrames, endFrames, introBeats, loopBeats, 24, firstDac,
                parts.lead(), parts.rhythm(), parts.synth(), !firstDac.isEmpty()),
                new NativeForm(introUnits, loopUnits, endUnits, 24));
    }

    private static Section fm(int from, int to, int primary, int harmony, boolean chords, boolean hopo) {
        return new Section(from, to, "FM", primary, harmony, chords, hopo, false);
    }
    private static List<Section> fm(int primary, int harmony, boolean chords, boolean hopo) {
        return List.of(fm(0, Integer.MAX_VALUE, primary, harmony, chords, hopo));
    }
    private static List<Section> psg(int primary, int harmony) {
        return primary < 0 ? List.of() : List.of(new Section(0, Integer.MAX_VALUE,
                "PSG", primary, harmony, harmony >= 0, true, false));
    }
    private static Parts parts(int lead, int rhythm, int harmony, int synth, int synthHarmony) {
        return new Parts(fm(lead, -1, false, true), fm(rhythm, harmony, harmony >= 0, false), psg(synth, synthHarmony));
    }
    private static Parts partsWithNoise(int lead, int rhythm, int harmony) {
        // S3 title/completion's PSG3 is the actual noise rhythm, never an invented pitched voice.
        return new Parts(fm(lead, -1, false, true), fm(rhythm, harmony, harmony >= 0, false),
                List.of(new Section(0, Integer.MAX_VALUE, "PSG", 2, -1, false, false, true)));
    }
    private static Parts angelIsland() {
        return new Parts(List.of(fm(0, 64, 1, -1, false, true), fm(64, 96, 2, -1, false, true),
                fm(96, Integer.MAX_VALUE, 1, 2, true, true)),
                fm(3, 4, true, false), psg(0, 1));
    }
    private static Parts marbleGarden() {
        // FM3/4 introduce the tune; FM2 then takes the lead, with FM5's delayed double.
        return new Parts(List.of(fm(0, 16, 2, 3, true, true), fm(16, Integer.MAX_VALUE, 1, -1, false, true)),
                fm(2, 3, true, false), List.of());
    }
    private static Parts mushroomHill(int entryBeat) {
        // The harmonic introduction is on FM3/4 before FM1's chromatic pickup.
        return new Parts(List.of(fm(0, entryBeat, 2, 3, true, true),
                fm(entryBeat, Integer.MAX_VALUE, 0, -1, false, true)), fm(2, 3, true, false), List.of());
    }
    private static Parts lavaReef() {
        return new Parts(List.of(fm(0, 15, 3, 4, true, true), fm(15, Integer.MAX_VALUE, 1, -1, false, true)),
                fm(3, 4, true, false), psg(0, 1));
    }
    private static Parts creditsMedley() {
        // Native medley: Knuckles, Mushroom Hill, Lava Reef, Flying Battery,
        // Sandopolis, Death Egg, Sky Sanctuary, then the game-complete theme.
        // FM1 is bass in Lava Reef and Sky Sanctuary; follow their actual leads.
        return new Parts(List.of(fm(0, 34, 1, -1, false, true), fm(34, 98, 0, -1, false, true),
                fm(98, 132, 1, -1, false, true), fm(132, 264, 0, -1, false, true),
                fm(264, 352, 3, -1, false, true), fm(352, Integer.MAX_VALUE, 0, -1, false, true)),
                List.of(fm(0, 34, 3, 4, true, false), fm(34, 98, 2, 3, true, false),
                        fm(98, 132, 3, 4, true, false), fm(132, 196, 2, 3, true, false),
                        fm(196, 264, 1, -1, false, false), fm(264, 352, 2, -1, false, false),
                        fm(352, Integer.MAX_VALUE, 3, 4, true, false)), psg(0, -1));
    }
}
