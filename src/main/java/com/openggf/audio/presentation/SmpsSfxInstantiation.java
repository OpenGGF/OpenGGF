package com.openggf.audio.presentation;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.SmpsSequencer;

public interface SmpsSfxInstantiation {
    SmpsSequencer instantiateCached(ResolvedSmpsSfxSource source,
                                    SmpsDriver currentOwner);

    SmpsCompositeVoice instantiateStandaloneCached(ResolvedSmpsSfxSource source);
}
