package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationParityProbe;

/** Test-source bridge for package-private audio diagnostics. */
public final class AudioManagerTestDiagnostics {
    private AudioManagerTestDiagnostics() {
    }

    public static AudioPresentationParityProbe.Snapshot shadowParitySnapshot(
            AudioManager audio) {
        return audio.shadowParitySnapshot();
    }
}
