package com.openggf.route;

import java.util.Objects;

/**
 * Bounds of one repair. Output rows {@code [0, firstEditable)} are the original rows verbatim and
 * cold replay still validates them. {@code maxAuthoredRows} bounds rows the search may author in
 * total; {@code maxWindows} bounds how many separate drift windows it may open. These are
 * authoring bounds, not measured safe defaults.
 */
public record RepairScope(int firstEditable, int maxAuthoredRows, int maxWindows, RepairTrigger trigger) {
    public RepairScope {
        Objects.requireNonNull(trigger, "trigger");
        if (firstEditable < 0 || maxAuthoredRows < 1 || maxWindows < 1) {
            throw new IllegalArgumentException("invalid repair scope " + firstEditable + "/" + maxAuthoredRows + "/" + maxWindows);
        }
    }

    public RepairScope(int firstEditable, int maxAuthoredRows, int maxWindows) {
        this(firstEditable, maxAuthoredRows, maxWindows, new RepairTrigger.TrackingDrift());
    }
}
