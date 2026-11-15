package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * Tidal Tempest objects' catalogue ({@code object-catalogue-ttz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_ttz.py}). Owned by the Tidal Tempest object lane.
 */
public final class TtzObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("ttz");

    private TtzObjectCatalogue() { }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
