package com.openggf.audio.presentation;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.SmpsSequencer;

public interface SmpsSfxInstantiation {
    SmpsSequencer instantiateCached(ResolvedSmpsSfxSource source,
                                    SmpsDriver currentOwner);

    /**
     * Creates an empty standalone composite. The registry applies current
     * channel controls before constructing and attaching the first sequencer.
     */
    SmpsCompositeVoice instantiateStandaloneCached(ResolvedSmpsSfxSource source);
}
