package com.openggf.audio;

import com.openggf.audio.synth.NativeCompatiblePcmOutput;

/**
 * Capture/diagnostic access to the opt-in native-compatible PCM stage, kept
 * outside the creator-facing {@link AudioManager} API (see
 * {@code GameplayCaptureTool --native-pcm}).
 */
public final class AudioNativePcmCaptureAccess {
    private AudioNativePcmCaptureAccess() { }

    /** See {@link AudioManager#setNativeCompatiblePcmCapture}; set before the presentation exists. */
    public static void enable(AudioManager manager, NativeCompatiblePcmOutput.Config config) {
        manager.setNativeCompatiblePcmCapture(config);
    }

    /** Moves completed native-compatible stereo frames; zero without the stage or a session. */
    public static int drain(AudioManager manager, short[] target, int offsetSamples, int maxFrames) {
        return manager.drainNativeCompatiblePcm(target, offsetSamples, maxFrames);
    }

    /** Presentation sessions built with the stage; more than one means its clock origin restarted. */
    public static int sessions(AudioManager manager) {
        return manager.nativeCompatiblePcmSessions();
    }
}
