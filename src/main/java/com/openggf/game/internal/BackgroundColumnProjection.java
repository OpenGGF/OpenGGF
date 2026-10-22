package com.openggf.game.internal;

import com.openggf.level.scroll.ScrollView;

/** Immutable ROM-derived column remapping phase; sampling never reads a live level. */
@FunctionalInterface
public interface BackgroundColumnProjection {
    BackgroundColumnRemap.Columns sample(ScrollView view);
}
