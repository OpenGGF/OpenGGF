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

    private record Gem(long onset, long end, int lanes, boolean allowHopo, int phrase) { }
    private record PitchWindow(CurationProfile.Section section, long first) { }

    /** The same authored source ownership that charts use, on the prepared audible clock. */
    public static List<SceneMusicPart> audioParts(SongSpec song, Role role, ScenePreparedMusic prepared) {
        long beat = samplesPerBeat(song, prepared);
        List<SceneMusicPart> parts = new ArrayList<>();
        for (CurationProfile.Section section : CurationProfile.sections(song, role)) {
            long onset = snapBoundary(prepared, section.firstBeat() * beat);
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
        long beat = samplesPerBeat(song, prepared);
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
        long spacing = Math.max(Math.max(1, beat / difficulty.densityDivision(role.drums())),
                Math.max(1, prepared.sampleRate() / (role.drums() ? 12L : 10L)));
        long previous = -beat;
        for (Map.Entry<Long, List<SceneNoteEvent>> group : byOnset.entrySet()) {
            long onset = group.getKey();
            long musicalBeat = onset / beat;
            CurationProfile.Section section = sections.stream().filter(value -> value.contains(musicalBeat))
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
                PitchWindow window = new PitchWindow(section, onset / (beat * 16) * beat * 16);
                List<Integer> pitches = pitchWindows.computeIfAbsent(window,
                        ignored -> phrasePitches(prepared.notes(), section, onset, beat));
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
            gems.add(new Gem(onset, end, lanes, difficulty != Difficulty.EASY && section.hopo(), CurationProfile.starPhrase(musicalBeat)));
            previous = onset;
        }
        if (gems.isEmpty()) throw new IllegalArgumentException("ROM has no events for " + song.id() + "/" + role);
        List<ChartNote> notes = new ArrayList<>();
        for (int index = 0; index < gems.size(); index++) {
            Gem gem = gems.get(index);
            long end = gem.end();
            if (index + 1 < gems.size()) end = Math.min(end, gems.get(index + 1).onset() - Math.max(1, beat / 12));
            if (end - gem.onset() < beat / 2) end = gem.onset();
            boolean hopo = false;
            if (index > 0 && !role.drums() && gem.allowHopo() && Integer.bitCount(gem.lanes()) == 1) {
                Gem last = gems.get(index - 1);
                hopo = Integer.bitCount(last.lanes()) == 1 && last.lanes() != gem.lanes()
                        && gem.onset() - last.onset() <= beat * 3 / 4;
            }
            int ticks = end > gem.onset() ? (int) ((end - gem.onset()) * 25 / beat) : 0;
            notes.add(new ChartNote(gem.onset(), Math.max(gem.onset(), end), gem.lanes(), hopo, gem.phrase(), ticks));
        }
        return new Chart(notes, prepared.lengthSamples(), beat);
    }

    /** Quarter duration from actual ROM DAC progression, including that driver's tempo gates. */
    private static long samplesPerBeat(SongSpec song, ScenePreparedMusic prepared) {
        List<SceneNoteEvent> dac = prepared.notes().stream().filter(event -> event.kind() == SceneNoteEvent.Kind.DAC)
                .sorted(Comparator.comparingLong(SceneNoteEvent::onsetSamples)).limit(8).toList();
        List<Integer> units = CurationProfile.firstDacUnits(song);
        if (dac.size() < 8) throw new IllegalArgumentException("Song preparation is too short to establish ROM tempo");
        long fullLength = (long) song.durationFrames() * prepared.sampleRate() / 60;
        if (!song.excerpt() && prepared.lengthSamples() == fullLength) {
            // Complete ROM stream cycle avoids bias from the driver's integer-frame
            // tempo gate over a short eight-attack sample. This is the cycle's measured
            // sample span divided by reference-derived SMPS quarter units, not a BPM.
            return Math.max(1, Math.round(prepared.lengthSamples() / (double) CurationProfile.wholeSongBeats(song)));
        }
        long duration = dac.get(7).onsetSamples() - dac.getFirst().onsetSamples();
        long totalUnits = units.stream().mapToLong(Integer::longValue).sum();
        return Math.max(1, Math.round(duration * (double) CurationProfile.unitsPerBeat(song) / totalUnits));
    }

    private static List<Integer> phrasePitches(List<SceneNoteEvent> events, CurationProfile.Section section,
                                              long onset, long beat) {
        long first = onset / (beat * 16) * beat * 16;
        long last = first + beat * 16;
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
