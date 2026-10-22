package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsSourceDescriptor;

import java.util.Objects;

public record SmpsDriverSessionSnapshot(
        boolean initialized,
        SmpsPendingGlobalCommand pendingGlobalCommand,
        SmpsSessionProfileFingerprint profile,
        SmpsSourceDescriptor selectedDacSource,
        boolean speedShoesEnabled,
        int speedMultiplier,
        int musicTempoPercent,
        boolean ringLeft,
        int musicFmDacTrackCount,
        SmpsSegaPcmTransportSnapshot segaPcmTransport,
        SmpsPhysicalDevice.Snapshot physical,
        SmpsNativePauseState nativePause,
        int nativeHistoryCursor) {
    public SmpsDriverSessionSnapshot(
            boolean initialized, SmpsPendingGlobalCommand pendingGlobalCommand,
            SmpsSessionProfileFingerprint profile, SmpsSourceDescriptor selectedDacSource,
            boolean speedShoesEnabled, int speedMultiplier, int musicTempoPercent,
            boolean ringLeft, int musicFmDacTrackCount,
            SmpsSegaPcmTransportSnapshot segaPcmTransport,
            SmpsPhysicalDevice.Snapshot physical) {
        this(initialized, pendingGlobalCommand, profile, selectedDacSource,
                speedShoesEnabled, speedMultiplier, musicTempoPercent, ringLeft,
                musicFmDacTrackCount, segaPcmTransport, physical, SmpsNativePauseState.RUNNING, 0);
    }

    public SmpsDriverSessionSnapshot {
        Objects.requireNonNull(nativePause, "nativePause");
        if (nativeHistoryCursor < 0) throw new IllegalArgumentException("negative native history cursor");
        com.openggf.audio.MusicTempoPercent.requireValid(musicTempoPercent);
        Objects.requireNonNull(pendingGlobalCommand,
                "pendingGlobalCommand");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(physical, "physical");
        if (musicFmDacTrackCount < 0) {
            throw new IllegalArgumentException("musicFmDacTrackCount must be non-negative");
        }
        if (speedMultiplier < 1) {
            throw new IllegalArgumentException(
                    "speedMultiplier must be positive");
        }
    }
}
