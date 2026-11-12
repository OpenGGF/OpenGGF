package com.openggf.game.internal;

import java.io.IOException;

/** Host-owned powered animation selection; never selects another game's ROM. */
public interface PoweredAnimationProvider {
    PoweredAnimationSource loadPoweredAnimations() throws IOException;

    /** Select this host's native vocabulary for the creator's movement archetype. */
    default PoweredAnimationSource loadPoweredAnimations(com.openggf.game.PlayerCharacter archetype) throws IOException {
        return loadPoweredAnimations();
    }
}
