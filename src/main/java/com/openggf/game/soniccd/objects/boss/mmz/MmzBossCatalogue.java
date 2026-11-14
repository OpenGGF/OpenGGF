package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Metallic Madness boss's catalogue ({@code object-catalogue-bossmmz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_bossmmz.py}): stage-file offsets of the boss, Amy, boss door, Hotaru and
 * door mappings, animations and data tables, -1 outside R83C/R83D. The tables are read from the stage file by the
 * objects; no value is transcribed.
 */
public final class MmzBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("bossmmz");

    private MmzBossCatalogue() { }

    public static int offset(CdStagePayload payload, String column) {
        return payload == null ? -1 : TABLES.offset(payload, column);
    }
}
