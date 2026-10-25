package com.openggf.audio;

import com.openggf.data.Rom;

import java.util.Objects;

/** Engine-only native request ingress without extending the creator audio API. */
public final class AudioManagerInternalAccess {
    private AudioManagerInternalAccess() { }

    /** Installs one selected world source without decoding either transient cross-pair. */
    public static void installMatchedSource(
            AudioManager manager, Rom rom, GameAudioProfile profile) {
        Objects.requireNonNull(manager, "manager").installMatchedSource(rom, profile);
    }

    public static void playSecondaryNativeRequest(AudioManager manager, int nativeId) {
        Objects.requireNonNull(manager, "manager").playSecondaryNativeRequest(nativeId);
    }
}
