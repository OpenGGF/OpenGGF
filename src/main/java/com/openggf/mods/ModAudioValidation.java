package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

final class ModAudioValidation {
    private ModAudioValidation() {}

    static String requireAudioPath(String path) {
        return requireAudioPath(path, ModInputLimits.DEFAULT_MAX_ENTRY_NAME_BYTES);
    }

    static String requireAudioPath(String path, int maxBytes) {
        Objects.requireNonNull(path, "assetPath");
        ModAssetRoot.requireNormalizedEntry(path);
        if (!path.startsWith("audio/") || !path.equals(path.toLowerCase(Locale.ROOT))
                || !(path.endsWith(".wav") || path.endsWith(".ogg"))) {
            throw new IllegalArgumentException("Audio assets must be lower-case audio/... WAV or OGG paths");
        }
        if (path.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("Audio asset path exceeds entry-name byte limit");
        }
        return path;
    }

    static void requireGain(float gain) {
        if (!Float.isFinite(gain) || gain < 0 || gain > 4) {
            throw new IllegalArgumentException("Audio gain must be finite and in 0..4");
        }
    }
}
