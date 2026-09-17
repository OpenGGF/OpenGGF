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
        // Map_SpriteMask frame 4: dc.b $F0,3,7,$C0,0,8 / dc.b $F0,3,0,0,0,0.
        // Size byte 3 = width ((3>>2)+1) 1 tile, height ((3&3)+1) 4 tiles: a
        // 32-line mask.
        return List.of(
                new ControlEntry(originX + 8, originY - 16, 1, 4, 0x7C0),
                new ControlEntry(originX, originY - 16, 1, 4, 0));
    }

    record ControlEntry(int x, int y, int widthTiles, int heightTiles, int rawTileWordLow11) { }
}
