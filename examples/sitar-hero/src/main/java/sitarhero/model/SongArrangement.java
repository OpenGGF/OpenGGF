package sitarhero.model;

import java.util.List;

/** Authored song form and channel ownership, derived from shipped-ROM sequencer evidence. */
public record SongArrangement(String id, String label, String game, int musicId, int zone, int act,
                              int introFrames, int loopFrames, int endFrames,
                              int introBeats, int loopBeats, int unitsPerBeat, List<Integer> firstDacUnits,
                              List<Section> lead, List<Section> rhythm, List<Section> synth, boolean drums) {
    /** Kind is FM or PSG; boundaries are musical quarter beats, never render frames. */
    public record Section(int firstBeat, int lastBeat, String kind, int channel, int harmony,
                          boolean chords, boolean hopo, boolean noise) {
        public Section {
            if (firstBeat < 0 || lastBeat <= firstBeat || !List.of("FM", "PSG").contains(kind)
                    || channel < 0 || channel > (kind.equals("FM") ? 5 : 2)
                    || harmony < -1 || harmony > (kind.equals("FM") ? 5 : 2)) throw new IllegalArgumentException("Invalid section");
        }
    }
    public SongArrangement {
        firstDacUnits = List.copyOf(firstDacUnits); lead = List.copyOf(lead); rhythm = List.copyOf(rhythm); synth = List.copyOf(synth);
        if (introFrames < 0 || loopFrames < 0 || endFrames < 0 || introBeats < 0 || loopBeats <= 0 || unitsPerBeat <= 0
                || loopFrames == 0 && endFrames == 0 || !firstDacUnits.isEmpty() && (firstDacUnits.size() != 7 || firstDacUnits.stream().anyMatch(n -> n <= 0)))
            throw new IllegalArgumentException("Invalid ROM song form");
        int duration = loopFrames == 0 ? endFrames : Math.max(7_200, Math.addExact(introFrames, Math.multiplyExact(loopFrames, 2)));
        if (duration > 36_000) throw new IllegalArgumentException("Song exceeds bounded ten-minute preparation");
    }
    public int durationFrames() { return loopFrames == 0 ? endFrames : Math.max(7_200, Math.addExact(introFrames, Math.multiplyExact(loopFrames, 2))); }
    public SongSpec song() { return new SongSpec(id, label, game, musicId, durationFrames(), zone, act); }
    public boolean supports(Role role) { return switch (role) { case SITAR -> !lead.isEmpty(); case HARP -> !rhythm.isEmpty(); case SYNTH -> !synth.isEmpty(); case BONGOS -> drums; }; }
    public List<Section> sections(Role role) { return switch (role) { case SITAR -> lead; case HARP -> rhythm; case SYNTH -> synth; case BONGOS -> List.of(); }; }
}
