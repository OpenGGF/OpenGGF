package com.openggf.level.objects;

import java.util.List;
import java.util.Objects;

/** Immutable row-only HUD presentation policy. */
@com.openggf.game.ModApi
public record HudProfile(List<HudRow> rows) {
    private static final HudProfile STOCK = new HudProfile(List.of(
            new HudRow(true, HudLabel.SCORE, HudMetric.SCORE,
                    16, 8, 64, 8, 6, HudWarningPolicy.NONE),
            new HudRow(true, HudLabel.TIME, HudMetric.TIME,
                    16, 24, 56, 24, 4, HudWarningPolicy.TIMER_FLASH),
            new HudRow(true, HudLabel.RINGS, HudMetric.RINGS,
                    16, 40, 64, 40, 3, HudWarningPolicy.ZERO_FLASH),
            new HudRow(true, HudLabel.LIVES, HudMetric.LIVES,
                    16, 200, 56, 208, 2, HudWarningPolicy.NONE)));

    public HudProfile {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }

    public static HudProfile stock() {
        return STOCK;
    }
}
