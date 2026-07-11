package com.openggf.game.sonic1;

import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;

/** Sonic 1 six-byte object encoding (respawn bit lives in the object-id byte). */
public final class Sonic1ObjectPlacementEncoding implements ObjectPlacementEncoding {
    private static final int RING_OBJECT_ID = 0x25;
    @Override
    public ObjectSpawn create(int x, int y, int objectId, int subtype, int renderFlags,
                              boolean respawnTracked, int placementId) {
        validate(x, y, objectId, subtype, renderFlags, placementId);
        int rawYWord = y | (renderFlags << 14);
        return new ObjectSpawn(x, y, objectId, subtype, renderFlags,
                respawnTracked, rawYWord, placementId);
    }

    private static void validate(int x, int y, int objectId, int subtype,
                                 int renderFlags, int placementId) {
        if (x < 0 || x >= 0xFFFF) throw new IllegalArgumentException("x must be 0..65534");
        if (y < 0 || y > 0x0FFF) throw new IllegalArgumentException("y must be 0..4095");
        if (objectId < 0 || objectId > 0x7F) throw new IllegalArgumentException("objectId must be 0..127");
        if (subtype < 0 || subtype > 0xFF) throw new IllegalArgumentException("subtype must be 0..255");
        if (renderFlags < 0 || renderFlags > 3) throw new IllegalArgumentException("renderFlags must be 0..3");
        if (placementId < 0) throw new IllegalArgumentException("placementId must be nonnegative");
    }

    @Override
    public ObjectSpawn createRingBackingObject(int x, int y, int placementId) {
        return create(x, y, RING_OBJECT_ID, 0, 0, false, placementId);
    }

    @Override
    public boolean usesObjectBackedRingPlacements() {
        return true;
    }

    @Override
    public boolean supportsEditorObjectId(int objectId) {
        return objectId >= 0 && objectId <= 0x7F;
    }

    @Override
    public boolean isReservedForRingEditing(int objectId) {
        return objectId == RING_OBJECT_ID;
    }
}
