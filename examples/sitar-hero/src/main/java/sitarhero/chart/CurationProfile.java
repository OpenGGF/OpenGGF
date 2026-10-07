package sitarhero.chart;

import com.openggf.mods.scene.SceneNoteEvent;
import sitarhero.model.Role;
import sitarhero.model.SongSpec;

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
        if (!song.availableRoles().contains(role))
            throw new IllegalArgumentException("ROM has no " + role + " part for " + song.id());
        if (!List.of("green-hill", "chemical-plant", "angel-island-1").contains(song.id()))
            return excerptSections(song, role);
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

    /**
     * Selections researched in sound/music/Mus8x (S1), sound/music/{82,87,89}
     * (S2), and Sound/Music/{HCZ1,MGZ1,FBZ1 (Sonic & Knuckles)} (S3K), then
     * corroborated by the production ROM preparer. Channel numbers here are
     * zero-based logical tracks. Detuned/delayed doubles remain real voices;
     * a chord still requires two distinct pitches attacking at the same sample.
     */
    private static List<Section> excerptSections(SongSpec song, Role role) {
        // Keep the catalogue closed: a new song needs an authored arrangement.
        if (!List.of("marble", "spring-yard", "labyrinth", "emerald-hill", "aquatic-ruin",
                "casino-night", "hydrocity-1", "marble-garden-1", "flying-battery-1").contains(song.id()))
            throw new IllegalArgumentException("No authored chart profile for " + song.id());
        if (role == Role.BONGOS) return List.of(new Section(0, Integer.MAX_VALUE,
                source(SceneNoteEvent.Kind.DAC, 5), null, false, false, false, 4));
        return switch (song.id()) {
            case "marble" -> switch (role) {
                case SITAR -> fm(0, 2, false, true); // FM1 melody; FM3 detuned double
                case HARP -> fm(3, 4, true, false); // FM4/5 actual harmonic response
                case SYNTH -> psg(0, 1, false); // PSG2 is a delayed PSG1 double
                default -> throw new IllegalArgumentException();
            };
            case "spring-yard" -> switch (role) {
                case SITAR -> fm(0, -1, false, true);
                case HARP -> fm(3, 4, true, false);
                case SYNTH -> psg(0, -1, false); // PSG2 immediately stops
                default -> throw new IllegalArgumentException();
            };
            case "labyrinth" -> switch (role) {
                case SITAR -> fm(0, -1, false, true);
                case HARP -> fm(4, -1, false, false); // FM5 rhythmic stabs; FM3/4 are sparse pads
                case SYNTH -> psg(0, 1, true);
                default -> throw new IllegalArgumentException();
            };
            case "emerald-hill" -> switch (role) {
                case SITAR -> fm(1, 2, true, true); // FM1 is bass, FM2/3 carry the melody
                case HARP -> fm(3, 4, true, false);
                case SYNTH -> psg(0, 1, true);
                default -> throw new IllegalArgumentException();
            };
            case "aquatic-ruin" -> switch (role) {
                case SITAR -> fm(1, 2, false, true); // FM3 echoes the lead seven stream units later
                case HARP -> fm(3, 4, true, false);
                case SYNTH -> psg(0, 1, true);
                default -> throw new IllegalArgumentException();
            };
            case "casino-night" -> switch (role) {
                case SITAR -> fm(1, 4, true, true);
                case HARP -> fm(2, 3, true, false);
                case SYNTH -> psg(0, 1, true);
                default -> throw new IllegalArgumentException();
            };
            case "hydrocity-1" -> switch (role) {
                case SITAR -> fm(0, 2, false, true); // FM3 is a one-unit delayed melody double
                case HARP -> fm(3, 4, true, false);
                case SYNTH -> psg(0, 1, false); // authentic delayed PSG double
                default -> throw new IllegalArgumentException();
            };
            case "marble-garden-1" -> switch (role) {
                // FM2's melody enters after five bars; FM3/4 carry the introduction.
                case SITAR -> List.of(section(0, 20, SceneNoteEvent.Kind.FM, 2, 3, true, true, false),
                        section(20, Integer.MAX_VALUE, SceneNoteEvent.Kind.FM, 1, 4, false, true, false));
                case HARP -> fm(2, 3, true, false);
                default -> throw new IllegalArgumentException("No PSG stream in MGZ1");
            };
            case "flying-battery-1" -> switch (role) {
                case SITAR -> fm(0, 4, false, true); // FM5 is the delayed melodic double
                case HARP -> fm(2, 3, true, false);
                default -> throw new IllegalArgumentException("No PSG stream in S&K FBZ1");
            };
            default -> throw new IllegalArgumentException("No authored chart profile for " + song.id());
        };
    }

    private static List<Section> fm(int primary, int harmony, boolean chords, boolean hopo) {
        return List.of(section(0, Integer.MAX_VALUE, SceneNoteEvent.Kind.FM, primary, harmony, chords, hopo, false));
    }

    private static List<Section> psg(int primary, int harmony, boolean chords) {
        return List.of(section(0, Integer.MAX_VALUE, SceneNoteEvent.Kind.PSG, primary, harmony, chords, true, false));
    }

    /** Source-stream quarter grouping; the sample duration is measured from real DAC onsets. */
    static int unitsPerBeat(SongSpec song) {
        return switch (song.id()) {
            case "green-hill", "emerald-hill" -> 16;
            case "marble", "spring-yard", "labyrinth", "casino-night" -> 12;
            default -> 24;
        };
    }

    /** GHZ 576-unit intro+1536-unit loop; CPZ 768+2688; AIZ 3456-unit whole loop. */
    static int wholeSongBeats(SongSpec song) { return "green-hill".equals(song.id()) ? 132 : 144; }

    /** The first seven inter-attack durations in each ROM DAC stream. */
    static List<Integer> firstDacUnits(SongSpec song) {
        return switch (song.id()) {
            case "green-hill" -> List.of(8, 8, 8, 8, 8, 8, 8);
            case "chemical-plant" -> List.of(24, 12, 24, 12, 12, 12, 24);
            case "angel-island-1" -> List.of(12, 6, 6, 12, 6, 6, 12);
            case "marble" -> List.of(3, 3, 12, 12, 12, 12, 12);
            case "spring-yard" -> List.of(6, 6, 6, 6, 6, 6, 4);
            case "labyrinth" -> List.of(6, 6, 6, 12, 6, 12, 18);
            case "emerald-hill" -> List.of(12, 32, 20, 4, 8, 4, 8);
            case "aquatic-ruin" -> List.of(6, 6, 18, 30, 18, 30, 18);
            case "casino-night" -> List.of(6, 6, 4, 2, 6, 6, 6);
            case "hydrocity-1" -> List.of(20, 4, 32, 16, 20, 24, 4);
            case "marble-garden-1" -> List.of(12, 8, 4, 12, 8, 12, 4);
            case "flying-battery-1" -> List.of(6, 6, 6, 6, 24, 24, 24);
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
        if ("s1".equals(song.game())) {
            return switch (noteId) { case 0x81 -> 4; case 0x82 -> 0; default -> -1; };
        }
        if ("s2".equals(song.game())) {
            // SMPS2 DAC bank: dKick/dSnare/dClap; dHiTom and dMid/Low/FloorTom.
            // Unlike S3K, $86 is a high tom and $8C is a mid tom, not a kick/metal hit.
            return switch (noteId) {
                case 0x81 -> 4;
                case 0x82, 0x83, 0x87, 0x8F, 0x90, 0x91 -> 0;
                case 0x8D, 0x8E -> 1;
                case 0x86, 0x8C -> 2;
                default -> -1;
            };
        }
        return switch (noteId) {
            case 0x86 -> 4; // dKickS3
            case 0x81, 0x87, 0x8F -> 0; // dSnareS3 / muffled snare / dClapS3
            case 0x84, 0x85 -> 1; // low/floor tom
            case 0x82, 0x83 -> 2; // high/mid tom
            case 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E -> 3; // cymbals / metal-hit family
            default -> -1;
        };
    }
}
