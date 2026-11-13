package com.openggf.level;

import com.openggf.game.mutation.MutationEffects;

import java.util.function.Consumer;

/**
 * Engine-only mutation effect sink for one known layout cell; not part of the
 * compiled-mod API.
 *
 * <p>A single {@code setBlockInMap} on layer 0 reports a whole-foreground redraw.
 * When the writer knows the cell it wrote, the redraw only has to cover that
 * cell's tiles, which is what the ROM's screen redraw after such a write shows
 * of the layout. Any other effect, or any effect beyond the foreground redraw,
 * keeps the ordinary {@link LevelManager#applyMutationEffects} handling.
 */
public final class LevelLayoutCellRedrawAccess {
    private LevelLayoutCellRedrawAccess() { }

    /** Effect sink for a write to layout cell ({@code cellX}, {@code cellY}) of {@code layer}. */
    public static Consumer<MutationEffects> effectSinkForCell(LevelManager manager, int layer,
                                                               int cellX, int cellY) {
        return effects -> {
            if (layer == 0 && isForegroundRedrawOnly(effects)) {
                manager.invalidateForegroundMapCell(cellX, cellY);
            } else {
                manager.applyMutationEffects(effects);
            }
        };
    }

    static boolean isForegroundRedrawOnly(MutationEffects effects) {
        return effects != null
                && effects.foregroundRedrawRequired()
                && !effects.allTilemapsRedrawRequired()
                && !effects.dirtyRegionProcessingRequired()
                && !effects.hasDirtyPatterns()
                && !effects.patternLookupRefreshRequired()
                && !effects.objectResyncRequired()
                && !effects.ringResyncRequired();
    }
}
