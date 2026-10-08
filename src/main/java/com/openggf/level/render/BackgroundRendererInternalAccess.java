package com.openggf.level.render;

import com.openggf.game.internal.BackgroundColumnRemap.Columns;

/** Internal presentation bridge, outside the published creator API. */
public final class BackgroundRendererInternalAccess {
    private BackgroundRendererInternalAccess() { }

    public static void setColumns(BackgroundRenderer renderer, Columns columns) {
        renderer.setColumns(columns);
    }
}
