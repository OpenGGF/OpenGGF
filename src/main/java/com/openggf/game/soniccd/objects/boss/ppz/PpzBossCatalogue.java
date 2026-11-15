package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Palmtree Panic boss's catalogue ({@code object-catalogue-bossppz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bossppz.py}): stage-file offsets of the part mappings, Eggman's
 * animations, the walk table and the explosion offsets, -1 outside R13C/R13D. The two tables are read
 * from the stage file here; no value is transcribed.
 */
public final class PpzBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bossppz");

    private PpzBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }

    /**
     * byte_20C04E (objects/r1/boss.asm:575-585): the body routine for the walk-table entry at byte offset
     * {@code at} ({@code routine_2}), or -1 at the negative end marker.
     */
    static int walkStep(CdStageImage image, CdStagePayload payload, int at) {
        int table = offset(payload, "walkTable");
        int value = (byte) image.u8(table + at);
        return value < 0 ? -1 : value;
    }

    /** The entry's second byte, the body's {@code var_2d} step count. */
    static int walkCount(CdStageImage image, CdStagePayload payload, int at) {
        return image.u8(offset(payload, "walkTable") + at + 1);
    }

    /** word_20D786 (objects/r1/boss.asm:2722-2732): explosion offset {@code index} (0-9), {dx, dy}. */
    static int[] explosionOffset(CdStageImage image, CdStagePayload payload, int index) {
        int table = offset(payload, "explosionOffsets") + index * 4;
        return new int[] {(short) image.u16(table), (short) image.u16(table + 2)};
    }
}
