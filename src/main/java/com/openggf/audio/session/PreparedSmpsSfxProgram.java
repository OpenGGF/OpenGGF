package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsDriverSnapshot;

import java.util.Objects;

public record PreparedSmpsSfxProgram(
        SmpsDriverSnapshot.SequencerEntry incomingSfx,
        int continuousSfxId,
        int continuousTrackCount) {
    public PreparedSmpsSfxProgram {
        Objects.requireNonNull(incomingSfx, "incomingSfx");
    }
}
