package sitarhero.chart;

import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.ScenePreparedMusic;
import com.openggf.mods.scene.SceneMusicPart;
import sitarhero.model.Chart;
import sitarhero.model.ChartNote;
import sitarhero.model.Difficulty;
import sitarhero.model.Role;
import sitarhero.model.SongSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ROM timing plus authored musical selections, never one gem per chip write. It selects
 * each song's lead/harmony/PSG/DAC arrangement, thins dense ornaments by difficulty,
 * maps four-bar pitch contours to the difficulty's frets,
 * admits only genuine simultaneous harmony chords, and authors HOPO/SP eligibility.
 */
public final class ChartCurator {
    private ChartCurator() { }

    private record Gem(long onset, long end, int lanes, boolean allowHopo, int phrase, long beat) { }
    private record PitchWindow(CurationProfile.Section section, long first) { }

    /** The same authored source ownership that charts use, on the prepared audible clock. */
    public static List<SceneMusicPart> audioParts(SongSpec song, Role role, ScenePreparedMusic prepared) {
        MusicalClock clock = MusicalClock.of(song, prepared);
        List<SceneMusicPart> parts = new ArrayList<>();
        for (CurationProfile.Section section : CurationProfile.sections(song, role)) {
            long onset = snapBoundary(prepared, clock.sampleAt(section.firstBeat()));
            int fm = sectionMask(section, SceneNoteEvent.Kind.FM);
            int psg = sectionMask(section, SceneNoteEvent.Kind.PSG);
            boolean dac = section.primary().kind() == SceneNoteEvent.Kind.DAC;
            if (onset < prepared.lengthSamples()) {
                SceneMusicPart previous = parts.isEmpty() ? null : parts.getLast();
                if (previous == null || previous.fmMask() != fm || previous.psgMask() != psg || previous.dacMuted() != dac) {
                    // Several short sections may collapse onto one real attack.
                    if (previous != null && previous.onsetSamples() == onset) parts.removeLast();
                    parts.add(new SceneMusicPart(onset, fm, psg, dac));
                }
            }
        }
        return List.copyOf(parts);
    }

    private static int sectionMask(CurationProfile.Section section, SceneNoteEvent.Kind kind) {
        int mask = section.primary().kind() == kind ? 1 << section.primary().channel() : 0;
        if (section.harmony() != null && section.harmony().kind() == kind) mask |= 1 << section.harmony().channel();
        return mask;
    }

    private static long snapBoundary(ScenePreparedMusic prepared, long predicted) {
        if (predicted == 0) return 0;
        // Apply ownership at the first real attack on/after the section boundary.
        // A nearest-event snap could move it earlier and mute the preceding section's gem.
        long result = prepared.lengthSamples();
        for (SceneNoteEvent event : prepared.notes()) {
            if (event.onsetSamples() >= predicted) result = Math.min(result, event.onsetSamples());
        }
        return result;
    }

    public static Chart curate(SongSpec song, Role role, ScenePreparedMusic prepared) {
        return curate(song, role, prepared, Difficulty.MEDIUM);
    }

    public static Chart curate(SongSpec song, Role role, ScenePreparedMusic prepared, Difficulty difficulty) {
        java.util.Objects.requireNonNull(difficulty, "difficulty");
        if (prepared.sampleRate() <= 0 || prepared.lengthSamples() <= 0)
            throw new IllegalArgumentException("Invalid prepared song clock");
        MusicalClock clock = MusicalClock.of(song, prepared);
        long beat = clock.averageBeat();
        List<CurationProfile.Section> sections = CurationProfile.sections(song, role);
        Map<Long, List<SceneNoteEvent>> byOnset = new TreeMap<>();
        for (SceneNoteEvent event : prepared.notes()) {
            if (event.onsetSamples() < prepared.lengthSamples())
                byOnset.computeIfAbsent(event.onsetSamples(), ignored -> new ArrayList<>()).add(event);
        }
        List<Gem> gems = new ArrayList<>();
        Map<PitchWindow, List<Integer>> pitchWindows = new HashMap<>();
        // Fixed ergonomic ceiling prevents fast source ornaments from becoming
        // unsafe alternating hits: at most ten melodic or twelve DAC hits/second.
        long previous = -beat;
        for (Map.Entry<Long, List<SceneNoteEvent>> group : byOnset.entrySet()) {
            long onset = group.getKey();
            long localBeat = clock.beatLengthAt(onset);
            long spacing = Math.max(Math.max(1, localBeat / difficulty.densityDivision(role.drums())),
                    Math.max(1, prepared.sampleRate() / (role.drums() ? 12L : 10L)));
            double exactBeat = clock.beatAt(onset) + 1e-8;
            long musicalBeat = (long) Math.floor(exactBeat);
            CurationProfile.Section section = sections.stream().filter(value -> value.contains(exactBeat))
                    .findFirst().orElseThrow();
            SceneNoteEvent primary = group.getValue().stream().filter(section.primary()::matches).findFirst().orElse(null);
            if (primary == null || onset - previous < spacing) continue;
            long duration = primary.durationSamples();
            int lanes;
            if (role.drums()) {
                int lane = CurationProfile.drumLane(song, primary.pitch());
                if (lane < 0) continue;
                // DAC uses physical pads 0..3 plus kick 4 at every difficulty.
                // Preserve native sample families and existing direct-hit bindings.
                // EASY combines tom/cymbal fills on pad 2: snare, fill, kick only.
                lanes = 1 << (difficulty == Difficulty.EASY && lane >= 1 && lane <= 3 ? 2 : lane);
            } else if (section.noise()) {
                // PSG3 has no pitched melody. Keep its sound and use authored alternating
                // accent/response positions, never pretend the note ID is a scale.
                lanes = 1 << ((musicalBeat & 1) == 0 ? 1 : difficulty.lanes() - 1);
            } else {
                long first = clock.sampleAt(musicalBeat / 16 * 16);
                long last = clock.sampleAt(musicalBeat / 16 * 16 + 16);
                PitchWindow window = new PitchWindow(section, first);
                List<Integer> pitches = pitchWindows.computeIfAbsent(window,
                        ignored -> phrasePitches(prepared.notes(), section, first, last));
                lanes = 1 << lane(primary.pitch(), pitches, difficulty.lanes());
                if (difficulty != Difficulty.EASY && section.chords() && section.harmony() != null) {
                    SceneNoteEvent harmony = group.getValue().stream().filter(section.harmony()::matches)
                            .findFirst().orElse(null);
                    if (harmony != null) {
                        lanes |= 1 << lane(harmony.pitch(), pitches, difficulty.lanes());
                        duration = Math.min(duration, harmony.durationSamples());
                    }
                }
            }
            long end = role.drums() || section.noise() ? onset
                    : Math.min(prepared.lengthSamples(), onset + duration);
            gems.add(new Gem(onset, end, lanes, difficulty != Difficulty.EASY && section.hopo(), CurationProfile.starPhrase(musicalBeat), localBeat));
            previous = onset;
        }
        if (gems.isEmpty()) throw new IllegalArgumentException("ROM has no events for " + song.id() + "/" + role);
        List<ChartNote> notes = new ArrayList<>();
        for (int index = 0; index < gems.size(); index++) {
            Gem gem = gems.get(index);
            long end = gem.end();
            if (index + 1 < gems.size()) end = Math.min(end, gems.get(index + 1).onset() - Math.max(1, gem.beat() / 12));
            if (end - gem.onset() < gem.beat() / 2) end = gem.onset();
            boolean hopo = false;
            if (index > 0 && !role.drums() && gem.allowHopo() && Integer.bitCount(gem.lanes()) == 1) {
                Gem last = gems.get(index - 1);
                hopo = Integer.bitCount(last.lanes()) == 1 && last.lanes() != gem.lanes()
                        && gem.onset() - last.onset() <= gem.beat() * 3 / 4;
            }
            int ticks = end > gem.onset() ? (int) ((clock.beatAt(end) - clock.beatAt(gem.onset())) * 25) : 0;
            notes.add(new ChartNote(gem.onset(), Math.max(gem.onset(), end), gem.lanes(), hopo, gem.phrase(), ticks));
        }
        return new Chart(notes, prepared.lengthSamples(), beat);
    }

    private static List<Integer> phrasePitches(List<SceneNoteEvent> events, CurationProfile.Section section,
                                              long first, long last) {
        return events.stream().filter(event -> event.onsetSamples() >= first && event.onsetSamples() < last)
                .filter(event -> section.primary().matches(event)
                        || section.harmony() != null && section.harmony().matches(event))
                .map(SceneNoteEvent::pitch).distinct().sorted().toList();
    }

    private static int lane(int pitch, List<Integer> pitches, int lanes) {
        int index = java.util.Collections.binarySearch(pitches, pitch);
        if (index < 0) index = Math.max(0, -index - 1);
        // Phrase-local pitch ranks preserve the contour and avoid a wide-register bass
        // voice forcing all nearby melody pitches onto one fret. Frets are a chart
        // abstraction; they do not alter the synthesized pitch.
        return pitches.size() <= 1 ? lanes / 2 : Math.min(lanes - 1, (int) Math.round(index * (lanes - 1.0) / (pitches.size() - 1)));
    }
}
