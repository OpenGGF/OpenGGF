package com.openggf.audio.smps;

import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.synth.Synthesizer;

/**
 * Descriptor-aware SMPS write boundary. The descriptor identifies the DAC
 * source for session-owned physical-device admission while standalone targets
 * may deliberately ignore that provenance.
 */
public interface SmpsLogicalWriteTarget extends Synthesizer {
    /**
     * Writes one source-driver PSG frequency transaction. Physical targets
     * receive both bytes verbatim; an owning driver may arbitrate the pair at
     * the source track's single ROM gate rather than between bus bytes.
     */
    default void writePsgFrequencyPair(
            Object source, int latchByte, int followingByte) {
        writePsg(source, latchByte);
        writePsg(source, followingByte);
    }

    void selectDac(SmpsSourceDescriptor source, DacData data);
}
