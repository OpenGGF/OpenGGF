package com.openggf.configuration;

import java.util.Locale;

/** Live window cadence preference; neither choice changes tick-indexed gameplay. */
public enum DisplayPacing {
    /** Lock services to a qualified display refresh near a region-rate multiple. */
    AUTO,
    /** Keep region services on host time at nominal speed on every display. */
    EXACT;

    public static DisplayPacing parse(String value) {
        if (value == null) {
            return AUTO;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DisplayPacing pacing : values()) {
            if (pacing.name().equals(normalized)) {
                return pacing;
            }
        }
        return AUTO;
    }
}
