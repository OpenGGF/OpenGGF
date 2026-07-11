package com.openggf.mods;

import java.util.Objects;

public record ModAudioSfx(SfxKey key, String assetPath, float gain) {
    public ModAudioSfx {
        Objects.requireNonNull(key, "key");
        assetPath = ModAudioValidation.requireAudioPath(assetPath);
        ModAudioValidation.requireGain(gain);
    }
}
