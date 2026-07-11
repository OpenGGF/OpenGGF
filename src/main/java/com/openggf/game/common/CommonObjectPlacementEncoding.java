package com.openggf.game.common;

import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;

/** Native six-byte object encoding shared by Sonic 2 and Sonic 3&amp;K. */
public final class CommonObjectPlacementEncoding implements ObjectPlacementEncoding {
    @Override
    public ObjectSpawn create(int x, int y, int objectId, int subtype, int renderFlags,
                              boolean respawnTracked, int placementId) {
        validate(x, y, objectId, subtype, renderFlags, placementId, 0xFF);
        int rawYWord = y | (renderFlags << 13) | (respawnTracked ? 0x8000 : 0);
        return new ObjectSpawn(x, y, objectId, subtype, renderFlags,
                respawnTracked, rawYWord, placementId);
    }

    static void validate(int x, int y, int objectId, int subtype, int renderFlags,
                         int placementId, int maxObjectId) {
        if (x < 0 || x >= 0xFFFF) throw new IllegalArgumentException("x must be 0..65534");
        if (y < 0 || y > 0x0FFF) throw new IllegalArgumentException("y must be 0..4095");
        if (objectId < 0 || objectId > maxObjectId) throw new IllegalArgumentException("objectId out of range");
        if (subtype < 0 || subtype > 0xFF) throw new IllegalArgumentException("subtype must be 0..255");
        if (renderFlags < 0 || renderFlags > 3) throw new IllegalArgumentException("renderFlags must be 0..3");
        if (placementId < 0) throw new IllegalArgumentException("placementId must be nonnegative");
    }
}
