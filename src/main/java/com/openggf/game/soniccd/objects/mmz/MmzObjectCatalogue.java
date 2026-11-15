package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * Metallic Madness objects' catalogue ({@code object-catalogue-mmz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_mmz.py}). Owned by the Metallic Madness object lane.
 */
public final class MmzObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("mmz");

    private MmzObjectCatalogue() { }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
