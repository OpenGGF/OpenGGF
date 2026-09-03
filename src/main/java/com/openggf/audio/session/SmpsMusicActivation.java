package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsSourceDescriptor;

import java.util.Objects;

public record SmpsMusicActivation(
        SmpsSourceDescriptor source,
        int fmDacTrackCount) {
    public SmpsMusicActivation {
        Objects.requireNonNull(source, "source");
        if (fmDacTrackCount < 0) {
            throw new IllegalArgumentException(
                    "fmDacTrackCount must be non-negative");
        }
    }
}
