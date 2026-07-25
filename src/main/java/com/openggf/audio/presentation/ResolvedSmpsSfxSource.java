package com.openggf.audio.presentation;

import java.util.Objects;

public record ResolvedSmpsSfxSource(
        long standaloneVoiceId,
        SmpsAssetKey assetKey,
        int pitchQ16,
        int priority,
        int continuousSfxId,
        int trackCount,
        int maxStereoFrames) {

    public ResolvedSmpsSfxSource {
        Objects.requireNonNull(assetKey, "assetKey");
        if (trackCount < 0) {
            throw new IllegalArgumentException("trackCount must be non-negative");
        }
        if (maxStereoFrames < 0) {
            throw new IllegalArgumentException("maxStereoFrames must be non-negative");
        }
    }
}
