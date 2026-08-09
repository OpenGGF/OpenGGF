package com.openggf.tools.audio.parity;

import java.util.List;
import java.util.Objects;

/** One invocation-ordinal's gating state and ordered decoded chip transactions. */
public record AudioParityTick(
        int ordinal,
        GlobalState global,
        List<AudioParityTrackState> tracks,
        List<AudioParityChipWrite> events) {

    public AudioParityTick {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        Objects.requireNonNull(global, "global");
        tracks = List.copyOf(tracks);
        events = List.copyOf(events);
        if (tracks.size() != AudioParitySchema.ROLES.size()) {
            throw new IllegalArgumentException("tick must contain all ten fixed roles");
        }
        for (int index = 0; index < tracks.size(); index++) {
            if (!AudioParitySchema.ROLES.get(index).equals(tracks.get(index).role())) {
                throw new IllegalArgumentException("fixed role is absent or out of order at index " + index);
            }
        }
    }

    public AudioParityTick withOrdinal(int newOrdinal) {
        return new AudioParityTick(newOrdinal, global, tracks, events);
    }

    /** Global state common to the ROM driver and OpenGGF sequencer. */
    public record GlobalState(
            boolean fadeActive,
            String fadeDirection,
            Integer fadeDelay,
            Integer fadeSteps,
            boolean speedUp,
            int tempoReload,
            int tempoTimeout) {
        public GlobalState {
            Objects.requireNonNull(fadeDirection, "fadeDirection");
            AudioParityChipWrite.byteRange(tempoReload, "tempoReload");
            AudioParityChipWrite.byteRange(tempoTimeout, "tempoTimeout");
            if (fadeActive) {
                if (!fadeDirection.equals("in") && !fadeDirection.equals("out")) {
                    throw new IllegalArgumentException("active fadeDirection must be in or out");
                }
                if (fadeDelay == null || fadeSteps == null) {
                    throw new IllegalArgumentException("fadeDelay and fadeSteps are required for an active fade");
                }
                AudioParityChipWrite.byteRange(fadeDelay, "fadeDelay");
                AudioParityChipWrite.byteRange(fadeSteps, "fadeSteps");
            } else {
                if (!fadeDirection.equals("none")) {
                    throw new IllegalArgumentException("inactive fadeDirection must be none");
                }
                if (fadeDelay != null || fadeSteps != null) {
                    throw new IllegalArgumentException("inactive fade cannot contain fadeDelay or fadeSteps");
                }
            }
        }
    }
}
