package com.openggf.game.soniccd.objects.projector;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

/**
 * The Metal Sonic projector's catalogue ({@code object-catalogue-projector.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_projector.py}): per stage, the file offsets of its mappings, its explosion
 * schedule and its hologram animations, located through the instructions that load them; -1 where the stage links no
 * projector.
 */
public final class ProjectorObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("projector");

    private ProjectorObjectCatalogue() { }

    public static int mappings(CdStagePayload payload) { return TABLES.offset(payload, "projectorMappings"); }

    public static int schedule(CdStagePayload payload) { return TABLES.offset(payload, "projectorSchedule"); }

    public static int animations(CdStagePayload payload) { return TABLES.offset(payload, "projectorAnimations"); }

    /**
     * Whether the stage's object index starts the projector: its code is linked and the stage is not an act 3 boss
     * arena, where the zone's boss reuses the projector's ID.
     */
    public static boolean indexedIn(CdStagePayload payload) {
        return mappings(payload) >= 0 && payload.stage().actNumber() != 3;
    }
}
