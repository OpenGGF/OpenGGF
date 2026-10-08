package sitarhero.chart;

import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.ScenePreparedMusic;
import sitarhero.model.SongCatalog;
import sitarhero.model.SongSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Musical quarters from native form/tempo boundaries; chart attacks remain exact ROM samples. */
final class MusicalClock {
    private record Anchor(double beat, double sample) { }
    private final List<Anchor> anchors;
    private final long length;
    private MusicalClock(List<Anchor> anchors, long length) { this.anchors = anchors; this.length = length; }

    static MusicalClock of(SongSpec song, ScenePreparedMusic prepared) {
        var form = SongCatalog.arrangement(song.id()).orElseThrow();
        var musical = SongCatalog.musicalForm(song.id());
        boolean shortened = prepared.lengthSamples() != (long) song.durationFrames() * prepared.sampleRate() / 60;
        if (shortened && form.firstDacUnits().size() == 7) {
            double witnessed = witnessedBeat(song, prepared, 0);
            if (witnessed > 0) return constant(witnessed, prepared.lengthSamples());
        }
        var nativeAnchors = SongCatalog.tempoAnchors(song.id());
        if (!nativeAnchors.isEmpty()) {
            return new MusicalClock(nativeAnchors.stream().map(anchor -> new Anchor(anchor.beat(),
                    serviceSample(anchor.serviceFrame(), prepared.sampleRate()))).toList(), prepared.lengthSamples());
        }
        var anchors = new ArrayList<Anchor>();
        anchors.add(new Anchor(0, 0));
        if (form.loopFrames() == 0) {
            // endFrames includes the service packet that performs the native final stop.
            anchors.add(new Anchor(musical.endBeats(), serviceSample(Math.max(1, form.endFrames() - 1), prepared.sampleRate())));
        } else {
            if (musical.introBeats() > 0)
                anchors.add(new Anchor(musical.introBeats(), serviceSample(form.introFrames(), prepared.sampleRate())));
            int repeats = (form.durationFrames() - form.introFrames() + form.loopFrames() - 1) / form.loopFrames() + 1;
            for (int repeat = 1; repeat <= repeats; repeat++)
                anchors.add(new Anchor(musical.introBeats() + repeat * musical.loopBeats(),
                        serviceSample(form.introFrames() + (long) repeat * form.loopFrames(), prepared.sampleRate())));
        }
        return new MusicalClock(List.copyOf(anchors), prepared.lengthSamples());
    }
    private static long serviceSample(long frame, int rate) {
        // The host's packet cursor floors frame*rate/60. Rounding would move a
        // handoff past its real attack at rates such as 8 kHz; anchor each repeat
        // separately so a fractional packet remainder cannot accumulate drift.
        return frame * rate / 60;
    }
    private static MusicalClock constant(double beat, long length) {
        if (!Double.isFinite(beat) || beat <= 0) throw new IllegalArgumentException("Invalid ROM quarter clock");
        return new MusicalClock(List.of(new Anchor(0, 0), new Anchor(1, beat)), length);
    }
    private static double witnessedBeat(SongSpec song, ScenePreparedMusic prepared, double fallback) {
        List<SceneNoteEvent> dac = prepared.notes().stream().filter(event -> event.kind() == SceneNoteEvent.Kind.DAC)
                .sorted(Comparator.comparingLong(SceneNoteEvent::onsetSamples)).limit(8).toList();
        var units = CurationProfile.firstDacUnits(song);
        if (dac.size() < 8 || units.size() != 7) return fallback;
        long duration = dac.get(7).onsetSamples() - dac.getFirst().onsetSamples();
        long total = units.stream().mapToLong(Integer::longValue).sum();
        return duration * (double) CurationProfile.unitsPerBeat(song) / total;
    }
    double beatAt(long sample) {
        int index = sampleSegment(sample);
        Anchor first = anchors.get(index), last = anchors.get(index + 1);
        return first.beat() + (sample - first.sample()) * (last.beat() - first.beat()) / (last.sample() - first.sample());
    }
    long sampleAt(double beat) {
        int index = 0;
        while (index + 2 < anchors.size() && anchors.get(index + 1).beat() <= beat) index++;
        Anchor first = anchors.get(index), last = anchors.get(index + 1);
        return Math.round(first.sample() + (beat - first.beat()) * (last.sample() - first.sample()) / (last.beat() - first.beat()));
    }
    long beatLengthAt(long sample) {
        int index = sampleSegment(sample);
        Anchor first = anchors.get(index), last = anchors.get(index + 1);
        return Math.max(1, Math.round((last.sample() - first.sample()) / (last.beat() - first.beat())));
    }
    long averageBeat() { return Math.max(1, Math.round(length / beatAt(length))); }
    private int sampleSegment(long sample) {
        int index = 0;
        while (index + 2 < anchors.size() && anchors.get(index + 1).sample() <= sample) index++;
        return index;
    }
}
