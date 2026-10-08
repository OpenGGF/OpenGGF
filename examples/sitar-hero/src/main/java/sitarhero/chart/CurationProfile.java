package sitarhero.chart;

import com.openggf.mods.scene.SceneNoteEvent;
import sitarhero.model.Role;
import sitarhero.model.SongSpec;
import sitarhero.model.SongCatalog;
import sitarhero.model.SongArrangement;
import sitarhero.catalogue.Sonic3kCatalogue;
import java.util.ArrayList;

import java.util.List;

/**
 * Authored arrangements shared by the four difficulties. These metadata are
 * code/chart data only: source offsets refer to runtime ROM streams, never copied bytes.
 * Lead, harmony and noise ownership differ by song, and within GHZ's intro/main parts.
 */
final class CurationProfile {
    private CurationProfile() { }

    record SourceSelector(SceneNoteEvent.Kind kind, int channel, int firstOffset, int lastOffset) {
        boolean matches(SceneNoteEvent event) {
            return event.kind() == kind && event.channel() == channel
                    && (firstOffset < 0 || event.sourceOffset() >= firstOffset)
                    && (lastOffset < 0 || event.sourceOffset() <= lastOffset);
        }
    }

    record Section(double firstBeat, double lastBeat, SourceSelector primary, SourceSelector harmony,
                   boolean chords, boolean hopo, boolean noise, int densityDivision) {
        boolean contains(double beat) { return beat >= firstBeat && beat < lastBeat; }
    }

    private static SourceSelector source(SceneNoteEvent.Kind kind, int channel) {
        return new SourceSelector(kind, channel, -1, -1);
    }

    private static Section section(double from, double to, SceneNoteEvent.Kind kind, int channel, int harmony,
                                   boolean chords, boolean hopo, boolean noise) {
        return new Section(from, to, source(kind, channel), harmony >= 0 ? source(kind, harmony) : null,
                chords, hopo, noise, 2);
    }

    static List<Section> sections(SongSpec song, Role role) {
        if (!song.availableRoles().contains(role))
            throw new IllegalArgumentException("ROM has no " + role + " part for " + song.id());
        var form = SongCatalog.arrangement(song.id()).orElseThrow(
                () -> new IllegalArgumentException("No authored chart profile for " + song.id()));
        if (role.drums()) return List.of(section(0, Double.POSITIVE_INFINITY,
                SceneNoteEvent.Kind.DAC, 5, -1, false, false, false));
        return fullSections(form, role);
    }

    private static List<Section> fullSections(SongArrangement form, Role role) {
        var result = new ArrayList<Section>();
        if (form.loopFrames() == 0) {
            for (var part : form.sections(role)) result.add(convert(part, part.firstBeat(), part.lastBeat()));
            return List.copyOf(result);
        }
        var musical = SongCatalog.musicalForm(form.id());
        double intro = musical.introBeats(), loop = musical.loopBeats();
        for (var part : form.sections(role)) {
            if (part.firstBeat() < intro)
                result.add(convert(part, part.firstBeat(), Math.min(part.lastBeat(), intro)));
        }
        int repeats = (form.durationFrames() - form.introFrames() + form.loopFrames() - 1) / form.loopFrames() + 1;
        for (int repeat = 0; repeat < repeats; repeat++) {
            for (var part : form.sections(role)) {
                double from = Math.max(intro, part.firstBeat()), to = Math.min(intro + loop, part.lastBeat());
                if (from < to) result.add(convert(part, from + repeat * loop, to + repeat * loop));
            }
        }
        return List.copyOf(result);
    }
    private static Section convert(SongArrangement.Section part, double from, double to) {
        return section(from, to, SceneNoteEvent.Kind.valueOf(part.kind()), part.channel(), part.harmony(),
                part.chords(), part.hopo(), part.noise());
    }

    static int unitsPerBeat(SongSpec song) {
        return SongCatalog.arrangement(song.id()).orElseThrow().unitsPerBeat();
    }

    /** Independent duration-unit witnesses for shortened preparations. */
    static List<Integer> firstDacUnits(SongSpec song) {
        return SongCatalog.arrangement(song.id()).orElseThrow().firstDacUnits();
    }

    /** Two-bar phrases at authored eight-bar intervals, kept separate from density thinning. */
    static int starPhrase(long beat) {
        if (beat < 16) return -1;
        long phrase = (beat - 16) / 32;
        return (beat - 16) % 32 < 8 ? Math.toIntExact(phrase) : -1;
    }

    /** Named DAC identities from the shipped driver bank, not macro aliases or pitch ranks. */
    static int drumLane(SongSpec song, int noteId) {
        if ("s1".equals(song.game())) {
            // DACUpdateTrack remaps $88..$8B to sample$83 at native timpani rates.
            return switch (noteId) { case 0x81 -> 4; case 0x82 -> 0;
                case 0x83, 0x88, 0x89, 0x8A, 0x8B -> 2; default -> -1; };
        }
        if ("s2".equals(song.game())) {
            // SMPS2 DAC bank: dKick/dSnare/dClap; dHiTom and dMid/Low/FloorTom.
            // Unlike S3K, $86 is a high tom and $8C is a mid tom, not a kick/metal hit.
            return switch (noteId) {
                case 0x81 -> 4;
                case 0x82, 0x83 -> 0; // snare/clap
                case 0x85, 0x88, 0x89, 0x8A, 0x8B -> 1; // real timpani family
                case 0x84, 0x87, 0x8F, 0x90, 0x91 -> 3; // scratch and real bongo family
                case 0x8D, 0x8E -> 2;
                case 0x86, 0x8C -> 2;
                default -> -1;
            };
        }
        return Sonic3kCatalogue.drumLane(noteId);
    }
}
