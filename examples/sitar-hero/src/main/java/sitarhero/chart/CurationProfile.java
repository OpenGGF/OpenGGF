package sitarhero.chart;

import com.openggf.mods.scene.SceneNoteEvent;
import sitarhero.model.Role;
import sitarhero.model.SongSpec;

import java.util.List;

/**
 * Authored arrangement choices for the single arcade difficulty. These metadata are
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

    record Section(int firstBeat, int lastBeat, SourceSelector primary, SourceSelector harmony,
                   boolean chords, boolean hopo, boolean noise, int densityDivision) {
        boolean contains(long beat) { return beat >= firstBeat && beat < lastBeat; }
    }

    private static SourceSelector source(SceneNoteEvent.Kind kind, int channel) {
        return new SourceSelector(kind, channel, -1, -1);
    }

    private static Section section(int from, int to, SceneNoteEvent.Kind kind, int channel, int harmony,
                                   boolean chords, boolean hopo, boolean noise) {
        return new Section(from, to, source(kind, channel), harmony >= 0 ? source(kind, harmony) : null,
                chords, hopo, noise, 2);
    }

    static List<Section> sections(SongSpec song, Role role) {
        if (!List.of("green-hill", "chemical-plant", "angel-island-1").contains(song.id()))
            throw new IllegalArgumentException("No authored chart profile for " + song.id());
        if (role == Role.BONGOS) return List.of(new Section(0, Integer.MAX_VALUE,
                source(SceneNoteEvent.Kind.DAC, 5), null, false, false, false, 4));
        if (role == Role.SITAR) {
            if ("green-hill".equals(song.id())) return List.of(
                    section(0, 4, SceneNoteEvent.Kind.FM, 0, 2, false, true, false),
                    section(4, 36, SceneNoteEvent.Kind.FM, 3, 4, true, false, false),
                    section(36, Integer.MAX_VALUE, SceneNoteEvent.Kind.FM, 0, -1, false, true, false));
            if ("angel-island-1".equals(song.id())) return List.of(
                    section(0, 64, SceneNoteEvent.Kind.FM, 1, -1, false, true, false),
                    section(64, 96, SceneNoteEvent.Kind.FM, 2, -1, false, true, false),
                    section(96, Integer.MAX_VALUE, SceneNoteEvent.Kind.FM, 1, 2, true, true, false));
            return List.of(section(0, 32,
                            SceneNoteEvent.Kind.FM, 0, -1, false, true, false),
                    section(32, Integer.MAX_VALUE,
                            SceneNoteEvent.Kind.FM, 0, -1, false, true, false));
        }
        if (role == Role.HARP && "green-hill".equals(song.id())) return List.of(
                section(0, 4, SceneNoteEvent.Kind.FM, 1, -1, false, false, false),
                section(4, 12, SceneNoteEvent.Kind.FM, 0, 2, false, false, false),
                section(12, 36, SceneNoteEvent.Kind.FM, 1, -1, false, false, false),
                section(36, 100, SceneNoteEvent.Kind.FM, 3, 4, true, false, false),
                section(100, Integer.MAX_VALUE, SceneNoteEvent.Kind.FM, 4, 3, true, false, false));
        if (role == Role.HARP) return List.of(
                section(0, 96,
                        SceneNoteEvent.Kind.FM, 3, 4, true, false, false),
                section(96, Integer.MAX_VALUE,
                        SceneNoteEvent.Kind.FM, 4, 3, true, false, false));
        if ("chemical-plant".equals(song.id())) {
            // CPZ PSG1/2 immediately stop. This is its real PSG3 hi-hat, with an
            // accent/response lane pattern rather than an invented pitched synth.
            return List.of(section(0, Integer.MAX_VALUE, SceneNoteEvent.Kind.PSG, 2, -1,
                    false, false, true));
        }
        if ("green-hill".equals(song.id())) return List.of(
                section(0, 36, SceneNoteEvent.Kind.PSG, 1, -1, false, true, false),
                section(36, Integer.MAX_VALUE, SceneNoteEvent.Kind.PSG, 0, 1, true, true, false));
        return List.of(section(0, Integer.MAX_VALUE, SceneNoteEvent.Kind.PSG, 0, 1, true, true, false));
    }

    /** Source-stream quarter grouping; the sample duration is measured from real DAC onsets. */
    static int unitsPerBeat(SongSpec song) { return "green-hill".equals(song.id()) ? 16 : 24; }

    /** GHZ 576-unit intro+1536-unit loop; CPZ 768+2688; AIZ 3456-unit whole loop. */
    static int wholeSongBeats(SongSpec song) { return "green-hill".equals(song.id()) ? 132 : 144; }

    /** The first seven inter-attack durations in each ROM DAC stream. */
    static List<Integer> firstDacUnits(SongSpec song) {
        return switch (song.id()) {
            case "green-hill" -> List.of(8, 8, 8, 8, 8, 8, 8);
            case "chemical-plant" -> List.of(24, 12, 24, 12, 12, 12, 24);
            case "angel-island-1" -> List.of(12, 6, 6, 12, 6, 6, 12);
            default -> throw new IllegalArgumentException("No ROM tempo profile for " + song.id());
        };
    }

    /** Two-bar phrases at authored eight-bar intervals, kept separate from density thinning. */
    static int starPhrase(long beat) {
        for (int phrase = 0; phrase < 8; phrase++) {
            long start = 16 + phrase * 32L;
            if (beat >= start && beat < start + 8) return phrase;
        }
        return -1;
    }

    /** Named DAC identities from the shipped driver bank, not macro aliases or pitch ranks. */
    static int drumLane(SongSpec song, int noteId) {
        if (!"s3k".equals(song.game())) {
            return switch (noteId) { case 0x81 -> 4; case 0x82 -> 0; default -> -1; };
        }
        return switch (noteId) {
            case 0x86 -> 4; // dKickS3
            case 0x81, 0x87 -> 0; // dSnareS3 / muffled snare
            case 0x84, 0x85 -> 1; // low/floor tom
            case 0x82, 0x83 -> 2; // high/mid tom
            case 0x8A, 0x8B, 0x8C, 0x8D, 0x8E -> 3; // metal-hit family
            default -> -1;
        };
    }
}
