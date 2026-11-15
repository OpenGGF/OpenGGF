package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Collision Chaos boss's catalogue ({@code object-catalogue-bossccz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bossccz.py}): stage-file offsets of the part mappings and animations, the
 * launcher's frame list, the barrier thresholds and heights and the four explosion patterns, -1 outside R33C/R33D.
 * The tables are read from the stage file here; no value is transcribed.
 */
public final class CczBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bossccz");

    private CczBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }

    /** One word of the table in {@code column}: entry {@code index} ({@code move.w table(pc,d0.w),d0}). */
    static int word(CdStageImage image, CdStagePayload payload, String column, int index) {
        return (short) image.u16(offset(payload, column) + index * 2);
    }

    /** {@code BombLauncher_FiringFrames} (objects/r3/boss.asm:965-967): the frame for firing step {@code index}. */
    static int launcherFrame(CdStageImage image, CdStagePayload payload, int index) {
        return image.u8(offset(payload, "launcherFrames") + index);
    }
}
