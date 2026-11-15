package com.openggf.game.soniccd.zones.ppz;

import com.openggf.game.soniccd.level.CdStageImage;

/** Disc-backed camera-section ownership for PPZ's independently drawn background rows. */
public final class PpzBackgroundRows {
    private final byte[] sections;

    private PpzBackgroundRows(byte[] sections) {
        this.sections = sections;
    }

    /**
     * Resolve the table and row mask from InitLevelDrawBG's addressing sequence,
     * rather than copying BGCameraSectIDs out of the research sources.
     * InitLevelDrawBG (Level/Palmtree Panic/Scroll (Act 1 Present).asm:1094-1104),
     * InitLevelDrawBG (Level/Palmtree Panic/Scroll (Act 2 Past).asm:1143-1154).
     */
    public static PpzBackgroundRows read(CdStageImage image) {
        for (int at = 0; at + 22 <= image.size(); at += 2) {
            // lea table,a0; adda.w #1,a0; move.w cameraBgY.w,d0;
            // add.w d4,d0; andi.w #rowMask,d0
            if (image.u16(at) != 0x41F9 || image.u16(at + 6) != 0xD0FC
                    || image.u16(at + 8) != 1 || image.u16(at + 10) != 0x3038
                    || image.u16(at + 14) != 0xD044 || image.u16(at + 16) != 0x0240) continue;
            int mask = image.u16(at + 18);
            if (mask != 0x7F0 && mask != 0x1F0) continue;
            int table = image.offsetOf(image.u32(at + 2));
            byte[] sections = image.slice(table + 1, (mask + 16) / 16);
            for (byte section : sections) {
                if (section != 0 && section != 2 && section != 4 && section != 6) {
                    throw new IllegalStateException("Invalid PPZ background camera section");
                }
            }
            return new PpzBackgroundRows(sections);
        }
        throw new IllegalStateException("PPZ InitLevelDrawBG camera-section table not found");
    }

    /**
     * DrawLevelBG1's StaticRow draws X=0..511; moving rows stream their own layout X.
     * DrawLevelBG1 (Level/Palmtree Panic/Scroll (Act 1 Present).asm:914-938),
     * DrawLevelBG1 (Level/Palmtree Panic/Scroll (Act 2 Past).asm:960-982).
     */
    public int layoutX(int sourceX, int sourceY) {
        int row = Math.floorMod(Math.floorDiv(sourceY, 16), sections.length);
        return sections[row] == 0 ? Math.floorMod(sourceX, 512) : sourceX;
    }
}
