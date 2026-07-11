package com.openggf.mods;

import java.util.Objects;
import java.util.OptionalLong;

public record ModAudioTrack(TrackKey key, String assetPath, boolean loop,
                            long loopStartFrame, OptionalLong loopEndFrame, float gain,
                            boolean tempoEffects) {
    public ModAudioTrack {
        Objects.requireNonNull(key, "key");
        assetPath = ModAudioValidation.requireAudioPath(assetPath);
        Objects.requireNonNull(loopEndFrame, "loopEndFrame");
        ModAudioValidation.requireGain(gain);
        if (loopStartFrame < 0) throw new IllegalArgumentException("loopStartFrame must be nonnegative");
        if (!loop && (loopStartFrame != 0 || loopEndFrame.isPresent())) {
            throw new IllegalArgumentException("Non-looping tracks require start 0 and absent end");
        }
        if (loop && loopEndFrame.isPresent() && loopEndFrame.getAsLong() <= loopStartFrame) {
            throw new IllegalArgumentException("loopEndFrame must exceed loopStartFrame");
        }
    }
}
