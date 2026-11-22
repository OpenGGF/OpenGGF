package com.openggf.mods;

import java.util.Objects;
import java.util.OptionalLong;

public record ModAudioTrack(TrackKey key, String assetPath, boolean loop,
                            long loopStartFrame, OptionalLong loopEndFrame, float gain,
                            boolean tempoEffects, String smpsPackage) {
    public ModAudioTrack(TrackKey key, String assetPath, boolean loop, long loopStartFrame,
                         OptionalLong loopEndFrame, float gain, boolean tempoEffects) {
        this(key, assetPath, loop, loopStartFrame, loopEndFrame, gain, tempoEffects, null);
    }

    public boolean isSmpsPackage() {
        return smpsPackage != null;
    }

    public ModAudioTrack {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loopEndFrame, "loopEndFrame");
        if (smpsPackage == null) {
            ModAudioValidation.requireAudioPath(assetPath);
        } else {
            ModAudioValidation.requirePackagePath(smpsPackage,
                    com.openggf.io.ModInputLimits.DEFAULT_MAX_ENTRY_NAME_BYTES);
            if (assetPath != null || loop || loopStartFrame != 0 || loopEndFrame.isPresent()) {
                throw new IllegalArgumentException("SMPS packages cannot declare streamed fields");
            }
        }
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
