package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationProducer;

/** Test-source bridge for package-private audio diagnostics. */
public final class AudioManagerTestDiagnostics {
    private AudioManagerTestDiagnostics() {
    }

    public static AudioPresentationParityProbe.Snapshot shadowParitySnapshot(
            AudioManager audio) {
        return audio.shadowParitySnapshot();
    }

    public static LiveCaptureAudioHandle attachPresentationCapture(
            AudioManager audio, int frameRate) {
        return audio.attachShadowCaptureForTesting(frameRate);
    }

    public static AudioPresentationProducer.TransactionFingerprint
            producerFingerprint(AudioManager audio) {
        return audio.releaseStateForTesting().producer();
    }
}
