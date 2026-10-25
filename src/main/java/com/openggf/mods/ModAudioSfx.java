package com.openggf.mods;

import java.util.Objects;

/** One declared SFX; {@code dac} (nullable) derives a retro rendering of the asset at preparation. */
public record ModAudioSfx(SfxKey key, String assetPath, float gain, ModSfxDac dac) {
    public ModAudioSfx {
        Objects.requireNonNull(key, "key");
        assetPath = ModAudioValidation.requireAudioPath(assetPath);
        ModAudioValidation.requireGain(gain);
    }

    /** A plain SFX played as recorded. */
    public ModAudioSfx(SfxKey key, String assetPath, float gain) {
        this(key, assetPath, gain, null);
    }
}
