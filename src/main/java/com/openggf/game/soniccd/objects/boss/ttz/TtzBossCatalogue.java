package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Tidal Tempest boss's catalogue ({@code object-catalogue-bossttz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bossttz.py}): stage-file offsets of the door, chase and boss mappings and
 * animations and of the boss's data tables, -1 outside R43C/R43D. The tables are read from the stage file here;
 * no value is transcribed.
 */
public final class TtzBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bossttz");

    private TtzBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }

    /**
     * One chase waypoint (byte_20C062, byte_20C0D4, byte_20C0E6; objects/r4/boss_chase.asm:559-667): six bytes,
     * the move kind, the arrival flag, the target X and Y words.
     */
    record Waypoint(int kind, int flag, int x, int y) { }

    /** Entry {@code index} of the chase path in catalogue column {@code column}. */
    static Waypoint waypoint(CdStageImage image, CdStagePayload payload, String column, int index) {
        int at = offset(payload, column) + index * 6;
        return new Waypoint(image.u8(at), image.u8(at + 1), image.u16(at + 2), image.u16(at + 4));
    }

    /** word_20C984 (objects/r4/boss.asm:584-594): explosion offset {@code index} (0-9), {dx, dy}. */
    static int[] explosionOffset(CdStageImage image, CdStagePayload payload, int index) {
        int at = offset(payload, "explosionOffsets") + index * 4;
        return new int[] {(short) image.u16(at), (short) image.u16(at + 2)};
    }

    /** byte_20C6AC (objects/r4/boss.asm:283-291): the bubble count at which attack {@code index} starts (signed). */
    static int bubbleThreshold(CdStageImage image, CdStagePayload payload, int index) {
        return (byte) image.u8(offset(payload, "bubbleThresholds") + index);
    }

    /** word_20CDBE (objects/r4/boss.asm:996-1013): the ring's angle step for {@code count} bubbles. */
    static int ringSpacing(CdStageImage image, CdStagePayload payload, int count) {
        return image.u16(offset(payload, "ringSpacing") + count * 2);
    }

    /**
     * byte_20CF5A (objects/r4/boss.asm:1180-1196): missile {@code index} (0-3): X and Y offset bytes (signed), X and
     * Y speed longs.
     */
    static int[] missileLaunch(CdStageImage image, CdStagePayload payload, int index) {
        int at = offset(payload, "missileLaunch") + index * 10;
        return new int[] {(byte) image.u8(at), (byte) image.u8(at + 1), (int) image.u32(at + 2), (int) image.u32(at + 6)};
    }

    /** word_202BA4+2 (objects/r4/boss.asm:371-375): the stage's right boundary, the second word read. */
    static int stageRightBound(CdStageImage image, CdStagePayload payload) {
        return (short) image.u16(offset(payload, "camBounds") + 2);
    }
}
