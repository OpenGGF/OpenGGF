package sitarhero.catalogue;

import sitarhero.model.SongArrangement;
import sitarhero.model.SongArrangement.Section;

import java.util.List;

/**
 * Complete musical selections from the shipped Sonic 1 REV01 bank, not audio excerpts.
 * Native owners: sound/music/Mus81..Mus93, SetDuration, TempoWait and cfJumpTo.
 * The research tool tools/audio/s1_song_forms.py reads the user's ROM independently.
 * Channel numbers are zero based; FM6 is pitched in Special Stage, not a drum track.
 * Non-zone selections deliberately use the compatible Green Hill Act 1 concert stage.
 * See docs/architecture/designs/2026-10-07-sitar-hero-s1-song-catalogue.md for the
 * entire bank inventory, temporal quantization, Credits tempo map and native bugs.
 */
public final class Sonic1Catalogue {
    private Sonic1Catalogue() { }

    /** A native service boundary and the SMPS duration units in that musical quarter. */
    public record TempoAnchor(int beat, int serviceFrame, int unitsPerBeat) { }

    /**
     * Credits' actual tempo/quarter transitions; ordinary tracks retain a constant
     * quarter grouping. Services are zero based, matching the preparation clock.
     * Divider2 sections use raw quarters12 (or16 for GHZ), scaling to24 (or32)
     * countdown units; the final title section restores divider1 and quarter24.
     * The final anchor includes shipped PSG2's late stopped tail, without fixing it.
     */
    public static List<TempoAnchor> tempoAnchors(String id) {
        if (!"s1-credits".equals(id)) return List.of();
        return List.of(new TempoAnchor(0, 0, 24), new TempoAnchor(84, 2056, 12),
                new TempoAnchor(172, 4318, 12), new TempoAnchor(204, 5171, 12),
                new TempoAnchor(212, 5394, 16), new TempoAnchor(224, 5969, 24),
                new TempoAnchor(275, 7600, 24));
    }

    public static List<SongArrangement> all() {
        return List.of(
                new SongArrangement("green-hill", "Green Hill", "s1", 0x81, 0, 0,
                        864, 2304, 0, 36, 96, 16, List.of(8, 8, 8, 8, 8, 8, 8),
                        List.of(fm(0, 4, 0, 2, false, true), fm(4, 36, 3, 4, true, false),
                                fm(36, END, 0, -1, false, true)),
                        List.of(fm(0, 4, 1, -1, false, false), fm(4, 12, 0, 2, false, false),
                                fm(12, 36, 1, -1, false, false), fm(36, 100, 3, 4, true, false),
                                fm(100, END, 4, 3, true, false)),
                        List.of(psg(0, 36, 1, -1, false, false), psg(36, END, 0, 1, true, false)), true),
                loop("marble", "Marble", 0x83, 1, 135, 2160, 5, 80, 12,
                        List.of(3, 3, 12, 12, 12, 12, 12),
                        fm(0, END, 0, 2, false, true), fm(0, END, 3, 4, true, false),
                        psg(0, END, 0, 1, false, false)),
                loop("spring-yard", "Spring Yard", 0x85, 2, 144, 2304, 4, 64, 12,
                        List.of(6, 6, 6, 6, 6, 6, 4),
                        fm(0, END, 0, -1, false, true), fm(0, END, 3, 4, true, false),
                        psg(0, END, 0, -1, false, false)),
                loop("labyrinth", "Labyrinth", 0x82, 3, 115, 2074, 4, 72, 12,
                        List.of(6, 6, 6, 12, 6, 12, 18),
                        fm(0, END, 0, -1, false, true), fm(0, END, 4, -1, false, false),
                        psg(0, END, 0, 1, true, false)),
                // FM5 doubles the early melody and later carries an independent response.
                loop("star-light", "Star Light", 0x84, 4, 115, 2535, 4, 88, 12,
                        List.of(12, 12, 12, 12, 12, 12, 12),
                        fm(0, END, 0, 4, false, true), fm(0, END, 2, 3, true, false),
                        psg(0, END, 0, 1, true, false)),
                // The 72-second whole form includes its opening descent, main melody,
                // arpeggio transition and reprise. Two complete loops need 144 seconds.
                loop("scrap-brain", "Scrap Brain", 0x86, 5, 0, 4320, 0, 144, 12,
                        List.of(3, 6, 6, 3, 3, 15, 24),
                        fm(0, END, 0, 2, false, true), fm(0, END, 3, 4, true, false),
                        psg(0, END, 1, 0, true, false)),
                new SongArrangement("s1-special-stage", "Sonic 1 Special Stage", "s1", 0x89, 0, 0,
                        0, 1975, 0, 0, 36, 24, List.of(),
                        List.of(fm(0, END, 5, 0, false, true)), // real FM6 melody / FM1 lower double
                        List.of(fm(0, END, 2, 3, true, false)), // FM3/4/5 chord responses
                        List.of(psg(0, END, 0, 1, false, false)), false),
                // These complete title/menu arrangements are short but are not reward
                // jingles. Keep their composed cadence and stopped tracks, without padding.
                finite("s1-title", "Sonic 1 Title", 0x8A, 541, 18, 24,
                        List.of(12, 12, 12, 12, 12, 12, 12),
                        List.of(fm(0, END, 0, 4, false, true)),
                        List.of(fm(0, END, 2, 3, true, false)),
                        List.of(psg(0, END, 2, -1, false, true))),
                finite("s1-ending", "Sonic 1 Ending", 0x8B, 1081, 36, 24,
                        List.of(12, 12, 12, 12, 12, 12, 8),
                        List.of(fm(0, 20, 0, 4, false, true), fm(20, END, 3, 4, false, true)),
                        List.of(fm(0, END, 2, -1, false, false)),
                        List.of(psg(0, END, 0, 1, false, false))),
                new SongArrangement("s1-boss", "Sonic 1 Boss", "s1", 0x8C, 0, 2,
                        0, 1280, 0, 0, 40, 12, List.of(6, 6, 6, 6, 6, 6, 6),
                        List.of(fm(0, END, 4, -1, false, true)), // FM5 carries the driving lead
                        List.of(fm(0, END, 0, 3, false, false)), // detuned accompaniment doubles
                        List.of(psg(0, END, 0, 1, true, false)), true),
                new SongArrangement("final-zone", "Final Zone", "s1", 0x8D, 6, 0,
                        115, 1152, 0, 4, 40, 12, List.of(6, 6, 6, 3, 9, 3, 3),
                        List.of(fm(0, END, 0, 4, false, true)), // FM5 is the detuned melody double
                        List.of(fm(0, END, 2, 3, true, false)), List.of(), true),
                finite("s1-continue", "Sonic 1 Continue", 0x90, 561, 20, 24,
                        List.of(12, 12, 12, 12, 12, 12, 12),
                        List.of(fm(0, END, 0, -1, false, true)),
                        List.of(fm(0, END, 2, 3, true, false)), List.of()),
                // Native FixBugs=0 / FixMusicAndSFXDataBugs=0: late, inaudible PSG2
                // notes still advance until service7600. Stop at the native final owner,
                // not the earlier FM/DAC end, and never loop this medley artificially.
                finite("s1-credits", "Sonic 1 Credits", 0x91, 7601, 275, 24,
                        List.of(6, 6, 6, 6, 12, 6, 12),
                        List.of(fm(0, 4, 3, 4, true, false), fm(4, 204, 0, -1, false, true),
                                fm(204, 215, 1, -1, false, true), fm(215, 244, 0, -1, false, true),
                                fm(244, END, 3, -1, false, true)),
                        List.of(fm(0, 36, 1, -1, false, false), fm(36, 84, 3, 4, true, false),
                                fm(84, 212, 1, -1, false, false), fm(212, 224, 2, 4, false, false),
                                fm(224, END, 2, -1, false, false)),
                        List.of(psg(0, 204, 0, 1, false, false), psg(204, END, 0, -1, false, false)))
        );
    }

    private static final int END = Integer.MAX_VALUE;

    private static SongArrangement loop(String id, String label, int musicId, int zone,
                                        int introFrames, int loopFrames, int introBeats, int loopBeats,
                                        int units, List<Integer> dac, Section lead, Section rhythm, Section synth) {
        return new SongArrangement(id, label, "s1", musicId, zone, 0, introFrames, loopFrames, 0,
                introBeats, loopBeats, units, dac, List.of(lead), List.of(rhythm), List.of(synth), true);
    }

    private static SongArrangement finite(String id, String label, int musicId, int frames, int beats,
                                          int units, List<Integer> dac, List<Section> lead,
                                          List<Section> rhythm, List<Section> synth) {
        return new SongArrangement(id, label, "s1", musicId, 0, 0, 0, 0, frames,
                0, beats, units, dac, lead, rhythm, synth, true);
    }

    private static Section fm(int from, int to, int channel, int harmony, boolean chords, boolean hopo) {
        return new Section(from, to, "FM", channel, harmony, chords, hopo, false);
    }

    private static Section psg(int from, int to, int channel, int harmony, boolean chords, boolean noise) {
        return new Section(from, to, "PSG", channel, harmony, chords, !noise, noise);
    }
}
