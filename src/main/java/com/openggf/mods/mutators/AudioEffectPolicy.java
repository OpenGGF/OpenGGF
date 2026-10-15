package com.openggf.mods.mutators;

import com.openggf.game.ModApi;
import java.util.Objects;

/** Native ROM cues, admitted by the audio owner with warning/music precedence. */
@ModApi
public record AudioEffectPolicy(Mode mode, int cueId, int minimumIntervalTicks) implements MutatorPolicy {
    public AudioEffectPolicy {
        Objects.requireNonNull(mode, "mode");
        if (cueId < 0 || cueId > 255 || minimumIntervalTicks < 15 || minimumIntervalTicks > 600)
            throw new IllegalArgumentException("Audio cue or interval exceeds native bounds");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.AUDIO_EFFECT; }
    @ModApi public enum Mode { MUSIC, LANDING_CUE }
}
