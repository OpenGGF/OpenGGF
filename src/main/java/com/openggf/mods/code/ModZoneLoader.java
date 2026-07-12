package com.openggf.mods.code;

import com.openggf.game.sonic2.Sonic2Level;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds a playable Sonic 2 level from one fully prepared export. */
public final class ModZoneLoader {
    private ModZoneLoader() {}

    public static Level load(PreparedModZone contribution, RingSpriteSheet ringSheet) throws IOException {
        Objects.requireNonNull(contribution, "contribution");
        ModLevelDefinition definition = Objects.requireNonNull(contribution.definition(), "prepared definition");
        if (definition.blockGridSide() != 8) {
            throw new IOException("Sonic 2 runtime requires blockGridSide 8");
        }
        List<ObjectSpawn> objects = new ArrayList<>(definition.objects().size());
        for (ModLevelDefinition.ObjectEntry entry : definition.objects()) {
            if (entry instanceof ModLevelDefinition.StockObjectSpawn stock) {
                objects.add(new ObjectSpawn(stock.x(), stock.y(), stock.stockObjectId(), stock.subtype(),
                        stock.renderFlags(), stock.respawnTracked(), stock.rawYWord(), stock.placementId()));
            } else {
                ModLevelDefinition.KeyedObjectSpawn keyed = (ModLevelDefinition.KeyedObjectSpawn) entry;
                String display = com.openggf.game.ModKeySyntax.requireDisplayKey(keyed.objectKey());
                String owner = display.substring(0, display.indexOf(':'));
                objects.add(new ObjectSpawn(keyed.x(), keyed.y(), 0, keyed.subtype(), keyed.renderFlags(),
                        keyed.respawnTracked(), keyed.rawYWord(), keyed.placementId(), owner, display));
            }
        }
        List<RingSpawn> rings = definition.rings().stream()
                .map(ring -> new RingSpawn(ring.x(), ring.y(), ring.placementId())).toList();
        return Sonic2Level.inMemoryBuilder(definition.zoneIndex(), definition.patternBytes(),
                        definition.chunkBytes(), definition.blockBytes())
                .layout(definition.width(), definition.height(), definition.foregroundMap(),
                        definition.backgroundMap().orElse(null))
                .paletteLines(definition.paletteLines())
                .solidProfiles(definition.solidHeights(), definition.solidWidths(), definition.solidAngles())
                .collisionIndices(definition.primaryCollisionIndices(), definition.secondaryCollisionIndices())
                .boundaries(definition.bounds().minX(), definition.bounds().maxX(),
                        definition.bounds().minY(), definition.bounds().maxY())
                .spawns(objects, rings, Objects.requireNonNull(ringSheet, "ringSheet"))
                .build();
    }
}
