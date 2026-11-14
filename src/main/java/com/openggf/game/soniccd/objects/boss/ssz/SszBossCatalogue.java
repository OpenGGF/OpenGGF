package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Stardust Speedway boss's catalogue ({@code object-catalogue-bossssz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bossssz.py}): stage-file offsets of the race's mappings, animation scripts,
 * script tables and data tables, -1 outside R73C/R73D. The tables are read from the stage file here; no value is
 * transcribed.
 */
public final class SszBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bossssz");

    private SszBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }

    /**
     * A {@code sub_20CEE0} script (objects/r7/boss.asm:1846-1868): entry {@code index} is a frame-count word and a
     * routine offset word; the frame count of entry {@code index}.
     */
    static int scriptFrame(CdStageImage image, CdStagePayload payload, String column, int index) {
        return image.u16(offset(payload, column) + index * 4);
    }

    /** word_20CABA (objects/r7/boss.asm:1434-1449): entry {@code index} as {X, dX, dY}, or null at the end marker. */
    static int[] liftEntry(CdStageImage image, CdStagePayload payload, int index) {
        int at = offset(payload, "metalLiftTable") + index * 6;
        int x = (short) image.u16(at);
        if (x < 0) return null;                                  // move.w (a2)+,d2; bmi.w locret_20CAB8
        return new int[] {x, (short) image.u16(at + 2), (short) image.u16(at + 4)};
    }

    /** word_20C930 (objects/r7/boss.asm:1237-1241): the {speed cap, acceleration} pair at byte offset {@code at}. */
    static int[] eggmanSpeed(CdStageImage image, CdStagePayload payload, int at) {
        int table = offset(payload, "eggmanSpeeds") + at;
        return new int[] {(short) image.u16(table), (short) image.u16(table + 2)};
    }

    /** byte_20EBB4 (objects/r7/boss_block.asm:39-45): {width, height} of block frame {@code frame}. */
    static int[] blockSize(CdStageImage image, CdStagePayload payload, int frame) {
        int at = offset(payload, "blockSizes") + frame * 2;
        return new int[] {image.u8(at), image.u8(at + 1)};
    }

    /** word_20EEA2 (objects/r7/spikes.asm:146-150): piece {@code index}'s {X speed, Y speed, X accel, gravity}. */
    static int[] spikePiece(CdStageImage image, CdStagePayload payload, int index) {
        int at = offset(payload, "spikesDebrisSpeeds") + index * 8;
        return new int[] {(short) image.u16(at), (short) image.u16(at + 2), (short) image.u16(at + 4),
                (short) image.u16(at + 6)};
    }
}
