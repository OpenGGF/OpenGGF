package com.openggf.level.objects;

import java.util.Objects;

/**
 * Immutable mapping from existing label art and counters to one screen-space HUD row.
 * Numeric metrics use a width from 1 through 9 and saturate into that width.
 * The existing timer renderer has one fixed four-character display width.
 */
@com.openggf.game.ModApi
public record HudRow(boolean visible, HudLabel label, HudMetric metric,
                     int labelX, int labelY, int valueRightX, int valueY,
                     int maxDigits, HudWarningPolicy warning) {
    public HudRow {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(warning, "warning");
        if (maxDigits < 1 || maxDigits > 9) {
            throw new IllegalArgumentException("maxDigits must be between 1 and 9");
        }
        if (metric == HudMetric.TIME && maxDigits != 4) {
            throw new IllegalArgumentException("TIME rows require maxDigits=4");
        }
    }
}
