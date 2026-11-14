package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * Quartz Quadrant objects' catalogue ({@code object-catalogue-qqz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_qqz.py}). Owned by the Quartz Quadrant object lane.
 */
public final class QqzObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("qqz");

    private QqzObjectCatalogue() { }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
