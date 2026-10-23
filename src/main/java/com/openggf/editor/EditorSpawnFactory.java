package com.openggf.editor;

import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.Objects;

/** Creates editor placements with stable identity and native object encoding. */
public final class EditorSpawnFactory {
    private final ObjectPlacementEncoding encoding;
    private final EditorPlacementIdAllocator ids;

    public EditorSpawnFactory(ObjectPlacementEncoding encoding, EditorPlacementIdAllocator ids) {
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public ObjectSpawn createObjectSpawn(int x, int y, int objectId, int subtype,
                                         int renderFlags, boolean respawnTracked) {
        return encoding.create(x, y, objectId, subtype, renderFlags, respawnTracked, ids.nextObjectId());
    }

    public ObjectSpawn createKeyedObjectSpawn(int x, int y, String objectKey, int subtype,
                                              int renderFlags, boolean respawnTracked) {
        return encoding.createKeyed(x, y, objectKey, subtype, renderFlags, respawnTracked,
                ids.nextObjectId());
    }

    public boolean canCreateObject(int objectId) {
        return encoding.supportsEditorObjectId(objectId)
                && !encoding.isReservedForRingEditing(objectId);
    }

    public ObjectSpawn moveObjectSpawn(ObjectSpawn spawn, int x, int y) {
        return encoding.move(spawn, x, y);
    }

    public ObjectSpawn withProperties(ObjectSpawn spawn,int subtype,int flags,boolean respawn) {
        ObjectSpawn changed = spawn.objectKey()==null
                ? encoding.create(spawn.x(),spawn.y(),spawn.objectId(),subtype,flags,respawn,spawn.layoutIndex())
                : encoding.createKeyed(spawn.x(),spawn.y(),spawn.objectKey(),subtype,flags,respawn,spawn.layoutIndex());
        ObjectSpawn nativeBefore = encoding.create(spawn.x(), spawn.y(), spawn.objectId(), spawn.subtype(),
                spawn.renderFlags(), spawn.respawnTracked(), spawn.layoutIndex());
        // Apply only the native flag changes. Preserve uninterpreted envelope bits
        // (e.g. S2 Y-word bit 12) without assuming another game's flag layout.
        int raw = spawn.rawYWord() ^ nativeBefore.rawYWord() ^ changed.rawYWord();
        return new ObjectSpawn(changed.x(), changed.y(), changed.objectId(), changed.subtype(), changed.renderFlags(),
                changed.respawnTracked(), raw, changed.layoutIndex(), changed.ownerModId(), changed.objectKey(), spawn.authoredProperties());
    }

    public RingSpawn createRingSpawn(int x, int y) {
        if (x < 0 || x >= 0xFFFF || y < 0 || y > (encoding.usesObjectBackedRingPlacements() ? 0x0FFF : 0xFFFF)) {
            throw new IllegalArgumentException("ring coordinates exceed stock placement encoding");
        }
        return new RingSpawn(x, y, ids.nextRingId());
    }

    public ObjectSpawn createRingBackingObject(RingSpawn ring) {
        return encoding.usesObjectBackedRingPlacements()
                ? encoding.createRingBackingObject(ring.x(), ring.y(), ids.nextObjectId())
                : null;
    }

    public ObjectSpawn moveRingBackingObject(ObjectSpawn backingObject, int x, int y) {
        return backingObject == null ? null : encoding.move(backingObject, x, y);
    }

    public RingSpawn moveRingSpawn(RingSpawn spawn, int x, int y) {
        if (x < 0 || x >= 0xFFFF || y < 0 || y > (encoding.usesObjectBackedRingPlacements() ? 0x0FFF : 0xFFFF)) {
            throw new IllegalArgumentException("ring coordinates exceed stock placement encoding");
        }
        return new RingSpawn(x, y, spawn.placementId());
    }
}
