package com.openggf.game.sonic3k;

import com.openggf.graphics.GraphicsManager;

import java.util.List;

/** S3K {@code Map_SpriteMask} SAT-control helpers. */
public final class S3kSpriteMaskSupport {
    private S3kSpriteMaskSupport() { }

    /** Submits exact mapping frame $04: marker tile $7C0 followed by its companion. */
    public static void submitFrame4(GraphicsManager graphics, int originX, int originY) {
        if (graphics == null || !graphics.isSpriteSatCollectionActive()) return;
        graphics.requestSpriteMask();
        for (ControlEntry entry : frame4Entries(originX, originY)) {
            graphics.submitSpriteSatControlEntry(
                    entry.x(), entry.y(), entry.widthTiles(), entry.heightTiles(), entry.rawTileWordLow11());
        }
    }

    /** Package-visible native mapping model so exact control words can be tested without renderer reflection. */
    static List<ControlEntry> frame4Entries(int originX, int originY) {
        return List.of(
                new ControlEntry(originX + 8, originY - 16, 4, 1, 0x7C0),
                new ControlEntry(originX, originY - 16, 4, 1, 0));
    }

    record ControlEntry(int x, int y, int widthTiles, int heightTiles, int rawTileWordLow11) { }
}
