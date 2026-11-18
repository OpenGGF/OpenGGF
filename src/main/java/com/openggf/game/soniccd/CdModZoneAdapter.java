package com.openggf.game.soniccd;

import com.openggf.game.modzone.*;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.Level;
import com.openggf.level.ModLevel;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/** Authored terrain, owned objects, and common rings decoded from the supplied disc. */
final class CdModZoneAdapter implements ModZoneAdapter {
    private final Supplier<CdDiscDataSource> disc;

    CdModZoneAdapter(Supplier<CdDiscDataSource> disc) {
        this.disc = disc;
    }

    @Override public void validate(String owner, ModZoneLevelData data) {
        if (data.formatVersion() != 1 || data.blockGridSide() != 16)
            throw new ModZoneRegistrationException(owner, "CD authored terrain requires v1 and 16x16 blocks");
        if (!Arrays.equals(data.primaryCollisionIndices(), data.secondaryCollisionIndices()))
            throw new ModZoneRegistrationException(owner, "CD has one collision path");
        if (data.objects().stream().anyMatch(s -> s.objectKey() == null))
            throw new ModZoneRegistrationException(owner,
                    "CD authored zones require owned object keys; native objects need a disc stage's program/PLC context");
        for (RingSpawn ring : data.rings()) {
            if (ring.x() < 0 || ring.x() >= 0xFFFF || ring.y() < 0 || ring.y() > 0x0FFF) {
                throw new ModZoneRegistrationException(owner, "CD rings require coordinates x=0..65534, y=0..4095");
            }
        }
        var codec = new CdObjectPlacementEncoding();
        for (var spawn : data.objects()) codec.move(spawn, spawn.x(), spawn.y());
    }

    @Override public Level load(String owner, ModZoneLevelData data) throws IOException {
        validate(owner, data);
        var objects = new ArrayList<>(data.objects());
        var backings = new LinkedHashMap<ObjectSpawn, List<RingSpawn>>();
        var codec = new CdObjectPlacementEncoding();
        long next = objects.stream().mapToLong(ObjectSpawn::layoutIndex).max().orElse(-1) + 1;
        for (RingSpawn ring : data.rings()) {
            if (next > Integer.MAX_VALUE) throw new IOException("CD ring backing placement identity capacity exhausted");
            var backing = codec.create(ring.x(), ring.y(), CdLevel.RING_OBJECT_ID, 0, 0, true, (int) next++);
            backings.put(backing, List.of(ring));
            objects.add(backing);
        }
        // The CD spawner stops evaluating entries at the first one beyond its X window.
        if (!backings.isEmpty()) objects.sort(Comparator.comparingInt(ObjectSpawn::x));
        return ModLevel.builder(data.zoneIndex(), 16, data.patternBytes(), data.chunkBytes(), data.blockBytes())
                .layout(data.width(), data.height(), data.foregroundMap(), data.backgroundMap().orElse(null))
                .paletteLines(data.paletteLines())
                .solidProfiles(data.solidHeights(), data.solidWidths(), data.solidAngles())
                .collisionIndices(data.primaryCollisionIndices(), data.secondaryCollisionIndices())
                .boundaries(data.minX(), data.maxX(), data.minY(), data.maxY())
                .spawns(objects, data.rings(), ringSheet(backings.isEmpty())).buildWithRingBackings(backings);
    }

    private RingSpriteSheet ringSheet(boolean empty) throws IOException {
        if (empty) return new RingSpriteSheet(new Pattern[0], List.of(), 0, 1, 0, 0);
        CdDiscDataSource source = disc.get();
        if (source == null) throw new IOException("CD authored rings require a verified Sonic CD disc");
        var payload = CdStagePayload.R11A;
        var image = new CdStageLoader(source).readStage(payload);
        return CdObjectArtProvider.ringSheet(image, payload, CdObjectArtProvider.loadVram(image, payload));
    }

    @Override public ModZoneRuntimeProfile runtimeProfile(String owner, ModZoneLevelData data) {
        validate(owner, data);
        return ModZoneRuntimeProfile.flatEmpty();
    }
}
