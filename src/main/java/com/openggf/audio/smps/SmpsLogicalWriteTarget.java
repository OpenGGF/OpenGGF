package com.openggf.audio.smps;

import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.synth.Synthesizer;

/**
 * Descriptor-aware SMPS write boundary. The descriptor identifies the DAC
 * source for session-owned physical-device admission while standalone targets
 * may deliberately ignore that provenance.
 */
public interface SmpsLogicalWriteTarget extends Synthesizer {
    void selectDac(SmpsSourceDescriptor source, DacData data);
}
