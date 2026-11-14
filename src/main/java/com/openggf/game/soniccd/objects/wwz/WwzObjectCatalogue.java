package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * Wacky Workbench objects' catalogue ({@code object-catalogue-wwz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_wwz.py}). Owned by the Wacky Workbench object lane.
 */
public final class WwzObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("wwz");

    private WwzObjectCatalogue() { }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
