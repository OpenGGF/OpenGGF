package com.openggf.game.soniccd;

import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The X, Y/flags, ID and subtype of Sonic CD's 8-byte object entry (SpawnObject,
 * Level/Object Spawner.asm:285-314), which share Sonic 1's encoding; the time-zone mask
 * and second subtype stay on {@link com.openggf.game.soniccd.level.CdLevel.CdPlacement}.
 */
final class CdObjectPlacementEncoding implements ObjectPlacementEncoding {
    @Override public java.util.List<ObjectSpawn> mergeAdditions(java.util.List<ObjectSpawn> source,
            java.util.List<ObjectSpawn> additions) {
        // The native spawner stops at the first future X. Preserve native order, inserting owned
        // additions before that boundary; layoutIndex still identifies the original entry.
        var pending = new java.util.ArrayList<>(additions);
        pending.sort(java.util.Comparator.comparingInt(ObjectSpawn::x));
        var merged = new java.util.ArrayList<ObjectSpawn>();
        int next = 0;
        for (var nativeSpawn : source) {
            while (next < pending.size() && pending.get(next).x() <= nativeSpawn.x()) merged.add(pending.get(next++));
            merged.add(nativeSpawn);
        }
        merged.addAll(pending.subList(next, pending.size()));
        return java.util.List.copyOf(merged);
    }

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
