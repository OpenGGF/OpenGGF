package com.openggf.audio.session;

import com.openggf.audio.smps.SmpsSequencer;

import java.util.Objects;

/** Immutable host configuration for one persistent SMPS session. */
public record SmpsDriverSessionConfiguration(
        SmpsStatefulCommandPolicy statefulCommandPolicy,
        SmpsSequencer.Region region) {
    public static final SmpsDriverSessionConfiguration DEFAULT =
            new SmpsDriverSessionConfiguration(SmpsStatefulCommandPolicy.NONE);

    public SmpsDriverSessionConfiguration(SmpsStatefulCommandPolicy statefulCommandPolicy) {
        this(statefulCommandPolicy, SmpsSequencer.Region.NTSC);
    }

    public SmpsDriverSessionConfiguration {
        Objects.requireNonNull(statefulCommandPolicy,
                "statefulCommandPolicy");
        Objects.requireNonNull(region, "region");
    }
}
