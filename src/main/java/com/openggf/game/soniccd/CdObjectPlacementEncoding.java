package com.openggf.game.soniccd;

import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The X, Y/flags, ID and subtype of Sonic CD's 8-byte object entry (SpawnObject,
 * Level/Object Spawner.asm:285-314), which share Sonic 1's encoding; the time-zone mask
 * and second subtype stay on {@link com.openggf.game.soniccd.level.CdLevel.CdPlacement}.
 */
final class CdObjectPlacementEncoding implements ObjectPlacementEncoding {
    @Override
    public ObjectSpawn create(int x, int y, int objectId, int subtype, int renderFlags,
                              boolean respawnTracked, int placementId) {
        if (x < 0 || x >= 0xFFFF || y < 0 || y > 0x0FFF || objectId < 0 || objectId > 0x7F
                || subtype < 0 || subtype > 0xFF || renderFlags < 0 || renderFlags > 3 || placementId < 0) {
            throw new IllegalArgumentException("Sonic CD placement out of range");
        }
        return new ObjectSpawn(x, y, objectId, subtype, renderFlags, respawnTracked,
                y | (renderFlags << 14), placementId);
    }

    @Override
    public boolean supportsEditorObjectId(int objectId) {
        return objectId >= 0 && objectId <= 0x7F;
    }
}
