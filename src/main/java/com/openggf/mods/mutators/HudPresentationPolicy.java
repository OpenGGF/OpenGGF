package com.openggf.mods.mutators;

import com.openggf.game.ModApi;
import java.util.Objects;

/** Native readout layout only; each label remains paired with its current value. */
@ModApi
public record HudPresentationPolicy(Mode mode, int periodPresentations, int seed) implements MutatorPolicy {
    public HudPresentationPolicy {
        Objects.requireNonNull(mode, "mode");
        if (periodPresentations < 30 || periodPresentations > 1800)
            throw new IllegalArgumentException("HUD period must be 30..1800");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.HUD_PRESENTATION; }
    @ModApi public enum Mode { BOUNCE, ORBIT, EVADE, SCALE, SHUFFLE, HIDE }
}
