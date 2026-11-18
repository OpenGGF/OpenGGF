package com.openggf.game.soniccd;

import com.openggf.game.modzone.*;
import com.openggf.level.Level;
import com.openggf.level.ModLevel;
import java.util.Arrays;

/** Authored terrain, owned objects and fixed springs sourced from the common disc art. */
final class CdModZoneAdapter implements ModZoneAdapter {
    @Override public void validate(String owner, ModZoneLevelData data) {
        if (data.formatVersion() != 1 || data.blockGridSide() != 16)
            throw new ModZoneRegistrationException(owner, "CD authored terrain requires v1 and 16x16 blocks");
        if (!Arrays.equals(data.primaryCollisionIndices(), data.secondaryCollisionIndices()))
            throw new ModZoneRegistrationException(owner, "CD has one collision path");
        if (!data.rings().isEmpty() || data.objects().stream().anyMatch(spawn -> spawn.objectKey() == null
                && (spawn.objectId() != 0x0A || (spawn.subtype() & 8) != 0)))
            throw new ModZoneRegistrationException(owner,
                    "CD authored zones admit owned objects and non-diagonal fixed springs; other stock placements need a disc stage");
        var codec = new CdObjectPlacementEncoding();
        for (var spawn : data.objects()) codec.move(spawn, spawn.x(), spawn.y());
    }

    @Override public Level load(String owner, ModZoneLevelData data) throws java.io.IOException {
        validate(owner, data);
        return ModLevel.builder(data.zoneIndex(), 16, data.patternBytes(), data.chunkBytes(), data.blockBytes())
                .layout(data.width(), data.height(), data.foregroundMap(), data.backgroundMap().orElse(null))
                .paletteLines(data.paletteLines())
                .solidProfiles(data.solidHeights(), data.solidWidths(), data.solidAngles())
                .collisionIndices(data.primaryCollisionIndices(), data.secondaryCollisionIndices())
                .boundaries(data.minX(), data.maxX(), data.minY(), data.maxY())
                .spawns(data.objects(), data.rings(), new com.openggf.level.rings.RingSpriteSheet(
                        new com.openggf.level.Pattern[0], java.util.List.of(), 0, 1, 0, 0)).build();
    }

    @Override public ModZoneRuntimeProfile runtimeProfile(String owner, ModZoneLevelData data) {
        validate(owner, data);
        return ModZoneRuntimeProfile.flatEmpty();
    }
}
