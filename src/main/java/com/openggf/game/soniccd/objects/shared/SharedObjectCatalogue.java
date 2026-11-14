package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The shared objects' catalogue ({@code object-catalogue-shared.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_shared.py}): per stage, the object ID each shared routine
 * has in that stage's ObjectIndex ({@code <routine>Id}) and its live copy's tables and
 * immediates. Owned by the shared-object lane.
 */
public final class SharedObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("shared");

    private SharedObjectCatalogue() { }

    /** The stage's value for {@code column}, or -1 where the stage does not index the routine. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
