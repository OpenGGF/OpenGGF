package sitarhero.chart;

import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.ScenePreparedMusic;
import com.openggf.mods.scene.SceneMusicPart;
import sitarhero.model.Chart;
import sitarhero.model.ChartNote;
import sitarhero.model.Role;
import sitarhero.model.SongSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ROM timing plus authored musical selections, never one gem per chip write. It selects
     * each song's lead/harmony/PSG/DAC arrangement, thins dense ornaments to eighth-note
     * spacing (sixteenths for direct-hit DAC), maps four-bar pitch contours to five frets,
 * admits only genuine simultaneous harmony chords, and authors HOPO/SP eligibility.
 */
public final class ChartCurator {
    private ChartCurator() { }

    private record Gem(long onset, long end, int lanes, boolean allowHopo, int phrase) { }

    /** The same authored source ownership that charts use, on the prepared audible clock. */
    public static List<SceneMusicPart> audioParts(SongSpec song, Role role, ScenePreparedMusic prepared) {
        if (!"green-hill".equals(song.id()) || role != Role.SITAR && role != Role.HARP)
            return List.of(new SceneMusicPart(0, role.fmMask(song), role.psgMask(song), role.dacMuted(song)));
        long beat = samplesPerBeat(song, prepared);
        List<SceneMusicPart> parts = new ArrayList<>();
        int previousMask = -1;
        for (CurationProfile.Section section : CurationProfile.sections(song, role)) {
            int mask = 1 << section.primary().channel();
            if (section.harmony() != null) mask |= 1 << section.harmony().channel();
            long onset = snapBoundary(prepared, section.firstBeat() * beat);
            if (onset < prepared.lengthSamples() && mask != previousMask) {
                parts.add(new SceneMusicPart(onset, mask, 0, false));
                previousMask = mask;
            }
        }
        return List.copyOf(parts);
    }

    private static long snapBoundary(ScenePreparedMusic prepared, long predicted) {
        if (predicted == 0) return 0;
        long result = predicted;
        long closest = prepared.sampleRate() / 60L + 1;
        for (SceneNoteEvent event : prepared.notes()) {
            long distance = Math.abs(event.onsetSamples() - predicted);
            if (distance < closest) { closest = distance; result = event.onsetSamples(); }
        }
        return result;
    }

    public static Chart curate(SongSpec song, Role role, ScenePreparedMusic prepared) {
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
        long previous = -beat;
        for (Map.Entry<Long, List<SceneNoteEvent>> group : byOnset.entrySet()) {
            long onset = group.getKey();
            long musicalBeat = onset / beat;
            CurationProfile.Section section = sections.stream().filter(value -> value.contains(musicalBeat))
                    .findFirst().orElseThrow();
            SceneNoteEvent primary = group.getValue().stream().filter(section.primary()::matches).findFirst().orElse(null);
            if (primary == null || onset - previous < beat / section.densityDivision()) continue;
            long duration = primary.durationSamples();
            int lanes;
            if (role.drums()) {
                int lane = CurationProfile.drumLane(song, primary.pitch());
                if (lane < 0) continue;
                lanes = 1 << lane;
            } else if (section.noise()) {
                // PSG3 has no pitched melody. Keep its sound and use authored alternating
                // accent/response positions, never pretend the note ID is a scale.
                lanes = 1 << ((musicalBeat & 1) == 0 ? 1 : 3);
            } else {
                List<Integer> pitches = phrasePitches(prepared.notes(), section, onset, beat);
                lanes = 1 << lane(primary.pitch(), pitches);
                if (section.chords() && section.harmony() != null) {
                    SceneNoteEvent harmony = group.getValue().stream().filter(section.harmony()::matches)
                            .findFirst().orElse(null);
                    if (harmony != null) {
                        lanes |= 1 << lane(harmony.pitch(), pitches);
                        duration = Math.min(duration, harmony.durationSamples());
                    }
                }
            }
            long end = role.drums() || section.noise() ? onset
                    : Math.min(prepared.lengthSamples(), onset + duration);
            gems.add(new Gem(onset, end, lanes, section.hopo(), CurationProfile.starPhrase(musicalBeat)));
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
        if (prepared.lengthSamples() == fullLength) {
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

    private static int lane(int pitch, List<Integer> pitches) {
        int index = java.util.Collections.binarySearch(pitches, pitch);
        if (index < 0) index = Math.max(0, -index - 1);
        // Phrase-local pitch ranks preserve the contour and avoid a wide-register bass
        // voice forcing all nearby melody pitches onto one fret. Five frets are a chart
        // abstraction; they do not alter the synthesized pitch.
        return pitches.size() <= 1 ? 2 : Math.min(4, (int) Math.round(index * 4.0 / (pitches.size() - 1)));
    }
}
