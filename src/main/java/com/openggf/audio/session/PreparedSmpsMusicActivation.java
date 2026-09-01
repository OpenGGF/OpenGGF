package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsDriverSnapshot;

import java.util.Objects;

public record PreparedSmpsMusicActivation(
        SmpsMusicActivation activation,
        SmpsDriverSnapshot.SequencerEntry incomingMusic,
        SmpsLogicalTransitionPolicy logicalPolicy,
        SmpsDacSelection selectedDac) {
    public PreparedSmpsMusicActivation {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(incomingMusic, "incomingMusic");
        Objects.requireNonNull(logicalPolicy, "logicalPolicy");
        Objects.requireNonNull(selectedDac, "selectedDac");
        if (incomingMusic.sfx()) {
            throw new IllegalArgumentException(
                    "music activation cannot contain an SFX sequencer");
        }
        if (!activation.source().equals(incomingMusic.source())
                || !activation.source().equals(selectedDac.source())) {
            throw new IllegalArgumentException(
                    "music activation sources must agree");
        }
        long generation = activation.source().dependencyGeneration();
        if (incomingMusic.source().dependencyGeneration() != generation
                || selectedDac.source().dependencyGeneration()
                        != generation) {
            throw new IllegalArgumentException(
                    "music activation dependency generations must agree");
        }
    }
}
