package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Quartz Quadrant boss's catalogue ({@code object-catalogue-bossqqz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bossqqz.py}): stage-file offsets of the part mappings and animations and of
 * the data tables the routines read, -1 outside R53C/R53D. Every table value is read from the stage file here; none
 * is transcribed.
 */
public final class QqzBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bossqqz");

    private QqzBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }

    /** word_20B78E (objects/r5/boss.asm:446-450): the booth's drop for map frame {@code frame}. */
    static int boothDrop(CdStageImage image, CdStagePayload payload, int frame) {
        return (short) image.u16(offset(payload, "boothDropTable") + frame * 2);
    }

    /** word_20C1B8 (objects/r5/boss.asm:1545-1551): the spark burst's Y offset for the booth's map frame. */
    static int sparkY(CdStageImage image, CdStagePayload payload, int frame) {
        return (short) image.u16(offset(payload, "sparkYOffsets") + frame * 2);
    }

    /** word_20C218 (objects/r5/boss.asm:1584-1592): spark {@code index}'s {dx, dy}, in allocation order. */
    static int[] sparkOffset(CdStageImage image, CdStagePayload payload, int index) {
        int at = offset(payload, "sparkOffsets") + index * 4;
        return new int[] {(short) image.u16(at), (short) image.u16(at + 2)};
    }

    /** word_20C29E (objects/r5/boss.asm:1632-1636): explosion {@code index}'s {dx, dy} (0-3). */
    static int[] explosionOffset(CdStageImage image, CdStagePayload payload, int index) {
        int at = offset(payload, "explosionOffsets") + index * 4;
        return new int[] {(short) image.u16(at), (short) image.u16(at + 2)};
    }

    /**
     * word_20BCA8 + subtype * $14 (objects/r5/boss.asm:957-961, read by SmallBombObject_0_Routine0, :877-900): a small
     * bomb's start, {dx, dy, x speed, y speed}, the speeds 16.16.
     */
    static int[] smallBombStart(CdStageImage image, CdStagePayload payload, int subtype) {
        int at = offset(payload, "smallBombStarts") + subtype * 0x14;
        return new int[] {(short) image.u16(at), (short) image.u16(at + 2), image.u32(at + 4), image.u32(at + 8)};
    }

    /** dword_20BCB4 + subtype * $14 (objects/r5/boss.asm:963-980, read by SmallBombObject_0_Routine4): {x, y} acceleration. */
    static int[] smallBombAccel(CdStageImage image, CdStagePayload payload, int subtype) {
        int at = offset(payload, "smallBombAccel") + subtype * 0x14;
        return new int[] {image.u32(at), image.u32(at + 4)};
    }

    /** word_20C0D4 / word_20C0E6 (objects/r5/boss.asm:1448-1456): the belt colours' source at step {@code step}. */
    static int beltPalette(CdStagePayload payload, boolean goodFuture, int step) {
        return offset(payload, goodFuture ? "beltPaletteGood" : "beltPaletteBad") + step;
    }
}
