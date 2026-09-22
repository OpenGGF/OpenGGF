package com.openggf.game.sonic3k;

import com.openggf.graphics.GraphicsManager;

import java.util.List;

/** S3K {@code Map_SpriteMask} SAT-control helpers. */
public final class S3kSpriteMaskSupport {
    private S3kSpriteMaskSupport() { }

    /** Obj_SpriteMask uses subtype's high nibble as its ROM mapping frame. */
    public static void submitFrame(GraphicsManager graphics, com.openggf.data.Rom rom,
                                   int frame, int originX, int originY) throws java.io.IOException {
        if (graphics == null || !graphics.isSpriteSatCollectionActive()) return;
        graphics.requestSpriteMask();
        // Map_SpriteMask ($18595E), six-byte S3K mapping pieces, art_tile = 0.
        int address = 0x18595E + rom.read16BitAddr(0x18595E + frame * 2);
        int count = rom.read16BitAddr(address);
        byte[] pieces = rom.readBytes(address + 2, count * 6);
        for (int i = 0; i < pieces.length; i += 6) {
            int size = pieces[i + 1] & 15;
            int x = (short) (((pieces[i + 4] & 255) << 8) | (pieces[i + 5] & 255));
            int tile = ((pieces[i + 2] & 255) << 8) | (pieces[i + 3] & 255);
            graphics.submitSpriteSatControlEntry(originX + x, originY + pieces[i],
                    (size >> 2) + 1, (size & 3) + 1, tile & 0x7FF);
        }
    }

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
