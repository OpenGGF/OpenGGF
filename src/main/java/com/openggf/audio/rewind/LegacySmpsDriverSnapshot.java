package com.openggf.audio.rewind;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.synth.VirtualSynthesizer;

import java.util.Objects;

/**
 * Transitional pre-session carrier for one logical SMPS memento and its
 * standalone physical synthesizer state.
 */
public record LegacySmpsDriverSnapshot(
        SmpsDriverSnapshot logical,
        VirtualSynthesizer.Snapshot physical,
        DacData liveDacReference) {

    public LegacySmpsDriverSnapshot {
        Objects.requireNonNull(logical, "logical");
        Objects.requireNonNull(physical, "physical");
    }
}
