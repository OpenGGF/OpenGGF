package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * Collision Chaos objects' catalogue ({@code object-catalogue-ccz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_ccz.py}). Owned by the Collision Chaos object lane.
 */
public final class CczObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("ccz");

    private CczObjectCatalogue() { }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
