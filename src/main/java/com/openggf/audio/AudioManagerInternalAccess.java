package com.openggf.audio;

import java.util.Objects;

/** Engine-only native request ingress without extending the creator audio API. */
public final class AudioManagerInternalAccess {
    private AudioManagerInternalAccess() { }

    public static void playSecondaryNativeRequest(AudioManager manager, int nativeId) {
        Objects.requireNonNull(manager, "manager").playSecondaryNativeRequest(nativeId);
    }
}
