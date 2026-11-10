package com.openggf.game.internal;

import java.io.IOException;

/** Host-owned powered animation selection; never selects another game's ROM. */
public interface PoweredAnimationProvider {
    PoweredAnimationSource loadPoweredAnimations() throws IOException;
}
