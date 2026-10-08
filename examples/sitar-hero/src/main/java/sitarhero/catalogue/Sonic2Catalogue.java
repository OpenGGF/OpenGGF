package sitarhero.catalogue;

import sitarhero.model.SongArrangement;
import sitarhero.model.SongArrangement.Section;

import java.util.List;

/**
 * Every substantive song in the shipped S2 REV01 music bank. Only arrangement
 * metadata live here; the host loads all music, instruments and DAC bytes from ROM.
 * Forms and ownership: docs/architecture/designs/2026-10-07-sitar-hero-s2-song-catalogue.md.
 */
public final class Sonic2Catalogue {
    private Sonic2Catalogue() { }

    public static List<SongArrangement> all() {
        return List.of(
                loop("emerald-hill", "Emerald Hill", 0x81, 0, 0, 128, 1536, 1, 0x9E, 16,
                        List.of(12, 32, 20, 4, 8, 4, 8), fm(1, 2, true, true), fm(3, 4, true, false), psg(0, 1, true)),
                loop("chemical-plant", "Chemical Plant", 0x8C, 1, 0, 768, 2688, 1, 0xEE, 24,
                        List.of(24, 12, 24, 12, 12, 12, 24), fm(0, -1, false, true), fm(3, 4, true, false), noise()),
                loop("aquatic-ruin", "Aquatic Ruin", 0x86, 2, 0, 12, 1920, 1, 0xE0, 24,
                        List.of(6, 6, 18, 30, 18, 30, 18), fm(1, 2, false, true), fm(3, 4, true, false), psg(0, 1, true)),
                loop("casino-night", "Casino Night", 0x83, 3, 0, 24, 960, 1, 0x48, 12,
                        List.of(6, 6, 4, 2, 6, 6, 6), fm(1, 4, true, true), fm(2, 3, true, false), psg(0, 1, true)),
                loop("hill-top", "Hill Top", 0x94, 4, 0, 0, 1920, 1, 0xBE, 24,
                        List.of(36, 12, 48, 6, 30, 12, 24), fm(0, -1, false, true), fm(3, 4, true, false), List.of()),
                loop("mystic-cave", "Mystic Cave", 0x84, 5, 0, 96, 2304, 1, 0xB6, 24,
                        List.of(12, 8, 4, 8, 12, 4, 12), fm(3, 4, false, true), fm(1, -1, false, false), psg(1, -1, false)),
                loop("oil-ocean", "Oil Ocean", 0x8F, 6, 0, 300, 2016, 2, 0xD0, 12,
                        List.of(6, 3, 3, 6, 9, 6, 3), fm(0, -1, false, true), fm(3, 4, true, false), psg(1, -1, false)),
                loop("metropolis", "Metropolis", 0x82, 7, 0, 384, 2304, 1, 0xEA, 24,
                        List.of(12, 12, 12, 12, 12, 12, 12), fm(4, -1, false, true), fm(2, 3, true, false), psg(0, 1, true)),
                loop("sky-chase", "Sky Chase", 0x8E, 8, 0, 96, 768, 1, 0x5B, 12,
                        List.of(3, 9, 6, 12, 6, 3, 9), fm(0, -1, false, true), fm(3, -1, false, false), psg(1, -1, false)),
                loop("wing-fortress", "Wing Fortress", 0x90, 9, 0, 18, 1440, 1, 0x88, 12,
                        List.of(6, 6, 6, 90, 6, 6, 6), fm(0, 4, false, true), fm(2, -1, false, false), List.of()),
                loop("death-egg", "Death Egg", 0x87, 10, 0, 0, 1152, 1, 0x60, 12,
                        List.of(4, 4, 16, 4, 4, 64, 4), fm(0, -1, false, true), fm(2, 3, false, false), psg(0, 1, false)),
                loop("emerald-hill-2p", "Emerald Hill (2P)", 0x91, 0, 0, 48, 768, 1, 0x5B, 12,
                        List.of(3, 3, 6, 3, 3, 6, 3), fm(0, 2, true, true), fm(3, 4, true, false), psg(0, 1, true)),
                loop("mystic-cave-2p", "Mystic Cave (2P)", 0x85, 5, 0, 24, 2880, 1, 0xEC, 24,
                        List.of(12, 6, 6, 12, 6, 6, 12), fm(2, -1, false, true), fm(0, 3, true, false), psg(1, -1, false)),
                loop("casino-night-2p", "Casino Night (2P)", 0x80, 3, 0, 96, 1920, 1, 0xBD, 24,
                        List.of(12, 12, 12, 12, 12, 12, 12), fm(1, 3, false, true), fm(2, 4, true, false), psg(0, 1, true)),
                // HPZ's song survives in REV01, but its removed stage is not a loadable zone.
                // EHZ is an explicit compatible outdoor concert stage, also used for menu/ending songs.
                loop("hidden-palace-s2", "Hidden Palace (S2)", 0x9B, 0, 0, 144, 2592, 2, 0xE0, 12,
                        List.of(6, 12, 6, 12, 18, 6, 12), fm(0, 3, false, true), fm(2, 4, false, false), psg(2, -1, false)),
                loop("options-s2", "Options (S2)", 0x89, 0, 0, 0, 192, 1, 0x87, 12,
                        List.of(12, 4, 4, 4, 12, 4, 4), fm(1, 4, false, true), fm(2, -1, false, false), psg(0, -1, false)),
                // Full melody/harmony cycle is 2304 units. FM1's 384-unit bass and
                // FM5's 96-unit ostinato repeat inside seven-count control-flow groups.
                loop("special-stage-s2", "Special Stage (S2)", 0x88, 0, 0, 672, 2304, 1, 0xFF, 24,
                        List.of(24, 24, 24, 12, 12, 24, 24), fm(1, -1, false, true), fm(2, 3, true, false), psg(0, 1, false)),
                loop("boss-s2", "Boss (S2)", 0x8D, 0, 1, 0, 1920, 1, 0xE3, 24,
                        List.of(12, 12, 12, 12, 12, 12, 12), fm(4, 3, false, true), fm(1, 2, true, false), noise()),
                loop("final-battle-s2", "Final Battle (S2)", 0x8B, 10, 0, 0, 2208, 1, 0xA9, 24,
                        List.of(12, 4, 4, 4, 12, 4, 4), fm(2, 4, false, true), fm(0, 1, true, false), psg(0, 1, true)),
                // Despite the enum's RESULTS_2P name, this is a substantial looping
                // menu/results composition, not a short win/result cue.
                loop("two-player-menu-s2", "2 Player Menu (S2)", 0x92, 0, 0, 0, 1008, 1, 0x68, 12,
                        List.of(9, 3, 6, 6, 9, 3, 6), fm(0, 4, true, true), fm(2, 3, true, false), psg(0, 1, true)),
                ending("ending-s2", "Ending (S2)", 0x8A, 4406, 109, 12,
                        List.of(21, 3, 6, 6, 6, 3, 3), fm(0, -1, false, true), fm(3, 4, true, false), psg(2, -1, false)),
                ending("credits-s2", "Credits (S2)", 0xBD, 9527, 333, 24,
                        List.of(6, 6, 6, 6, 12, 6, 12), creditsLead(), fm(2, 3, true, false), psg(0, 1, true)));
    }

    private static SongArrangement loop(String id, String label, int music, int zone, int act,
                                        int introUnits, int loopUnits, int divider, int tempo, int quarter,
                                        List<Integer> dac, List<Section> lead, List<Section> rhythm, List<Section> synth) {
        // Native zBGMLoad seeds tempo; TempoWait's carry advances DurationTimeout.
        // A ceiling loop duration covers either accumulator phase on later loops.
        int introFrames = introUnits == 0 ? 0 : ceil((introUnits + 1) * 256, tempo) - 2;
        int loopFrames = ceil(loopUnits * 256, tempo);
        return new SongArrangement(id, label, "s2", music, zone, act, introFrames, loopFrames, 0,
                ceil(introUnits, divider * quarter), loopUnits / (divider * quarter), quarter, dac,
                lead, rhythm, synth, true);
    }

    private static SongArrangement ending(String id, String label, int music, int frames, int beats, int quarter,
                                          List<Integer> dac, List<Section> lead, List<Section> rhythm, List<Section> synth) {
        return new SongArrangement(id, label, "s2", music, 0, 0, 0, 0, frames, 0, beats, quarter, dac,
                lead, rhythm, synth, true);
    }
    private static int ceil(int value, int divisor) { return (value + divisor - 1) / divisor; }
    private static List<Section> fm(int channel, int harmony, boolean chords, boolean hopo) {
        return List.of(new Section(0, Integer.MAX_VALUE, "FM", channel, harmony, chords, hopo, false));
    }
    private static List<Section> psg(int channel, int harmony, boolean chords) {
        return List.of(new Section(0, Integer.MAX_VALUE, "PSG", channel, harmony, chords, true, false));
    }
    private static List<Section> noise() {
        return List.of(new Section(0, Integer.MAX_VALUE, "PSG", 2, -1, false, false, true));
    }
    private static List<Section> creditsLead() {
        // DAC-owned tempo changes at native units 864,4128,4704,6144:
        // $F0->$EA->$CD->$C5->$C0. The host must follow the real prepared onsets.
        return List.of(new Section(0, 36, "FM", 0, -1, false, true, false),
                new Section(36, 172, "FM", 0, -1, false, true, false),
                new Section(172, 196, "FM", 0, -1, false, true, false),
                new Section(196, 256, "FM", 0, -1, false, true, false),
                new Section(256, Integer.MAX_VALUE, "FM", 0, -1, false, true, false));
    }
}
