package com.openggf.level.render;

import com.openggf.game.internal.BackgroundColumnRemap.Columns;
import com.openggf.util.IntIndexedView;
import com.openggf.util.ShortIndexedView;

/** Internal presentation bridge, outside the published creator API. */
public final class BackgroundRendererInternalAccess {
    private BackgroundRendererInternalAccess() { }

    public static void setColumns(BackgroundRenderer renderer, Columns columns) {
        renderer.setColumns(columns);
    }

    public static void renderWithPriorityMetadata(BackgroundRenderer renderer,
            IntIndexedView hScroll, ShortIndexedView lineVScroll, ShortIndexedView columnVScroll,
            int midpoint, int extraBuffer, int vScroll, boolean noHScroll) {
        renderer.renderWithPriorityMetadata(hScroll, lineVScroll, columnVScroll,
                midpoint, extraBuffer, vScroll, noHScroll);
    }

    public static void renderPriorityMask(BackgroundRenderer renderer) {
        renderer.renderPriorityMask();
    }
}
