package com.openggf.mods;

import java.util.Objects;

/** Launch-prepared one-shot PCM retained by the session audio lease. */
public record PreparedSfx(SfxKey key, PcmData pcm, float gain, String sourceSha256) {
    public PreparedSfx {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(pcm, "pcm");
        ModAudioValidation.requireGain(gain);
        if (sourceSha256 == null || sourceSha256.isBlank()) {
            throw new IllegalArgumentException("Source digest is required");
        }
    }
}
