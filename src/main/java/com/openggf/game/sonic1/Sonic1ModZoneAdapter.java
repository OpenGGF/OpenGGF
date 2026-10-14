package com.openggf.game.sonic1;

import com.openggf.game.modzone.*;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.Level;
import com.openggf.level.ModLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import java.io.IOException;
import java.util.*;

/** Bounded flat Sonic 1 authoring host. Stock zone scripts and loop flags are not inherited. */
public final class Sonic1ModZoneAdapter implements ModZoneAdapter {
    private final Sonic1GameModule module;
    public Sonic1ModZoneAdapter(Sonic1GameModule module) {
        this.module = Objects.requireNonNull(module);
    }

    /** These factories use independent ROM art and do not require a stock zone identity. */
    public static boolean supportsStockObject(int id) {
        return switch (id) {
            case Sonic1ObjectIds.SIGNPOST, Sonic1ObjectIds.MONITOR, Sonic1ObjectIds.SPRING, Sonic1ObjectIds.SPIKES,
                    Sonic1ObjectIds.CRABMEAT, Sonic1ObjectIds.BUZZ_BOMBER,
                    Sonic1ObjectIds.CHOPPER, Sonic1ObjectIds.MOTOBUG, Sonic1ObjectIds.NEWTRON,
                    Sonic1ObjectIds.CATERKILLER, Sonic1ObjectIds.BATBRAIN,
                    Sonic1ObjectIds.YADRIN, Sonic1ObjectIds.ROLLER,
                    Sonic1ObjectIds.INVISIBLE_BARRIER, Sonic1ObjectIds.HIDDEN_BONUS -> true;
            default -> false;
        };
    }

    @Override public void validate(String owner, ModZoneLevelData data) {
        Objects.requireNonNull(owner); Objects.requireNonNull(data);
        if (data.formatVersion()!=1 || data.blockGridSide()!=16)
            throw new ModZoneRegistrationException(owner,"Sonic 1 authored levels require v1 and 16x16 blocks");
        if (!Arrays.equals(data.primaryCollisionIndices(),data.secondaryCollisionIndices()))
            throw new ModZoneRegistrationException(owner,"Sonic 1 has one collision path; primary and secondary profile assignments must agree");
        var codec = new Sonic1ObjectPlacementEncoding();
        for (ObjectSpawn spawn:data.objects()) {
            if (spawn.objectKey()==null && !supportsStockObject(spawn.objectId()))
                throw new ModZoneRegistrationException(owner,"Stock Sonic 1 object 0x%02X requires unsupported native zone behavior; remove it or use an owned object key".formatted(spawn.objectId()));
            codec.move(spawn,spawn.x(),spawn.y());
        }
        for (RingSpawn ring:data.rings()) if(ring.x()<0 || ring.x()>=0xFFFF || ring.y()<0 || ring.y()>0xFFF)
            throw new ModZoneRegistrationException(owner,"Sonic 1 rings require native coordinates x=0..65534, y=0..4095");
    }

    @Override public Level load(String owner, ModZoneLevelData data) throws IOException {
        validate(owner,data);
        var codec = new Sonic1ObjectPlacementEncoding();
        List<ObjectSpawn> objects = new ArrayList<>();
        for(ObjectSpawn spawn:data.objects()) objects.add(codec.move(spawn,spawn.x(),spawn.y()));
        long next = objects.stream().mapToLong(ObjectSpawn::layoutIndex).max().orElse(-1)+1;
        Map<ObjectSpawn,List<RingSpawn>> mapping = new LinkedHashMap<>();
        for(RingSpawn ring:data.rings()) {
            if(next>Integer.MAX_VALUE) throw new IOException("Sonic 1 ring backing placement identity capacity exhausted");
            ObjectSpawn backing=codec.createRingBackingObject(ring.x(),ring.y(),(int)next++);
            objects.add(backing); mapping.put(backing,List.of(ring));
        }
        // Native ObjPosLoad preserves within-column ordering, not full-X sorting.
        objects.sort(Comparator.comparingInt(spawn -> spawn.x() & 0xFF80));
        ModLevel decoded=ModLevel.builder(data.zoneIndex(),16,data.patternBytes(),data.chunkBytes(),data.blockBytes())
                .layout(data.width(),data.height(),data.foregroundMap(),data.backgroundMap().orElse(null))
                .paletteLines(data.paletteLines())
                .solidProfiles(data.solidHeights(),data.solidWidths(),data.solidAngles())
                .collisionIndices(data.primaryCollisionIndices(),data.secondaryCollisionIndices())
                .boundaries(data.minX(),data.maxX(),data.minY(),data.maxY())
                .spawns(objects,data.rings(),module.getAdditiveLevelRingSpriteSheet()).build();
        decoded.setPalette(0,module.getAdditiveLevelCharacterPalette());
        return Sonic1Level.fromAuthoredLevel(decoded,mapping);
    }

    @Override public ModZoneRuntimeProfile runtimeProfile(String owner, ModZoneLevelData data) {
        validate(owner,data); return ModZoneRuntimeProfile.flatEmpty();
    }
}
