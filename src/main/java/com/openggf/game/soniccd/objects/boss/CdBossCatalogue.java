package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The boss pattern's catalogue ({@code object-catalogue-boss.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_boss.py}): per act 3 stage, the flower capsule's mappings,
 * animations and palette. A zone's own boss tables live in its {@code boss<zone>} owner. Values are
 * stage-file offsets, -1 where the stage has none.
 */
public final class CdBossCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("boss");

    private CdBossCatalogue() { }

    /** The stage's value for {@code column}, or -1. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
