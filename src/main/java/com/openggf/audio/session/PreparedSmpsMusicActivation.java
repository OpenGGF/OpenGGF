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
    }
}
