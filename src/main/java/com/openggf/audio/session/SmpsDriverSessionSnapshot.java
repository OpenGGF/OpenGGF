package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsSourceDescriptor;

import java.util.Objects;

public record SmpsDriverSessionSnapshot(
        boolean initialized,
        SmpsPendingGlobalCommand pendingGlobalCommand,
        SmpsSessionProfileFingerprint profile,
        SmpsSourceDescriptor selectedDacSource,
        SmpsPhysicalDevice.Snapshot physical) {
    public SmpsDriverSessionSnapshot {
        Objects.requireNonNull(pendingGlobalCommand,
                "pendingGlobalCommand");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(physical, "physical");
    }
}
