package com.openggf.level.objects;

/** Builds lossless, game-native object placement records for editor mutations. */
@com.openggf.game.ModApi
public interface ObjectPlacementEncoding {
    ObjectSpawn create(int x, int y, int objectId, int subtype, int renderFlags,
                       boolean respawnTracked, int placementId);

    default ObjectSpawn move(ObjectSpawn spawn, int x, int y) {
        ObjectSpawn moved = create(x, y, spawn.objectId(), spawn.subtype(), spawn.renderFlags(),
                spawn.respawnTracked(), spawn.layoutIndex());
        return new ObjectSpawn(moved.x(), moved.y(), moved.objectId(), moved.subtype(),
                moved.renderFlags(), moved.respawnTracked(), moved.rawYWord(), moved.layoutIndex(),
                spawn.ownerModId(), spawn.objectKey());
    }

    /** Returns an object backing for a newly-authored stage ring, or {@code null}. */
    default ObjectSpawn createRingBackingObject(int x, int y, int placementId) {
        return null;
    }

    default boolean usesObjectBackedRingPlacements() {
        return false;
    }

    default boolean supportsEditorObjectId(int objectId) {
        return objectId >= 0 && objectId <= 0xFF;
    }

    default boolean isReservedForRingEditing(int objectId) {
        return false;
    }
}
