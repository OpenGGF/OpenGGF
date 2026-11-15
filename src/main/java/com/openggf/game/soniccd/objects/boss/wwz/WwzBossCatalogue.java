package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Wacky Workbench boss's catalogue ({@code object-catalogue-bosswwz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bosswwz.py}): stage-file offsets of the part mappings and scripts and of
 * the boss's data tables, -1 outside R63C/R63D. Table values are read from the stage file here; none is transcribed.
 */
public final class WwzBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bosswwz");

    private WwzBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }

    /** Word {@code index} of the table in {@code column}. */
    static int word(CdStageImage image, CdStagePayload payload, String column, int index) {
        return (short) image.u16(offset(payload, column) + index * 2);
    }
}
