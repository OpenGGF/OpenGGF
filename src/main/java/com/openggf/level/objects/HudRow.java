package com.openggf.level.objects;

import java.util.Objects;

/** Immutable mapping from existing label art and counters to one screen-space HUD row. */
@com.openggf.game.ModApi
public record HudRow(boolean visible, HudLabel label, HudMetric metric,
                     int labelX, int labelY, int valueRightX, int valueY,
                     int maxDigits, HudWarningPolicy warning) {
    public HudRow {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(warning, "warning");
        if (maxDigits <= 0) throw new IllegalArgumentException("maxDigits must be positive");
    }
}
