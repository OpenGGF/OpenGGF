package com.openggf.level.objects;

import com.openggf.game.ModKeySyntax;
import com.openggf.level.spawn.SpawnPoint;

/**
 * Immutable placement record decoded from the Sonic 2 object layout lists.
 * <p>
 * {@code layoutIndex} distinguishes spawns that share identical game data
 * (same position, objectId, subtype, etc.) but occupy different slots in
 * the ROM layout list.  Without it the record's value-based
 * {@code equals}/{@code hashCode} would collapse them into one entry
 * inside {@code LinkedHashSet}, losing the duplicate that the ROM sees
 * as a separate object slot.  Dynamically spawned objects use index
 * {@code -1} (via the 7-parameter convenience constructor).
 */
@com.openggf.game.ModApi
public record ObjectSpawn(
        int x,
        int y,
        int objectId,
        int subtype,
        int renderFlags,
        boolean respawnTracked,
        int rawYWord,
        int layoutIndex,
        String ownerModId,
        String objectKey) implements SpawnPoint {

    /** Canonical constructor – normalises fields to unsigned widths. */
    public ObjectSpawn {
        x = x & 0xFFFF;
        y = y & 0xFFFF;
        objectId = objectId & 0xFF;
        subtype = subtype & 0xFF;
        renderFlags = renderFlags & 0x3;
        rawYWord = rawYWord & 0xFFFF;
        if ((ownerModId == null) != (objectKey == null)) {
            throw new IllegalArgumentException("Object owner and key must either both be present or both be absent");
        }
        if (objectKey != null) {
            ownerModId = ModKeySyntax.requireManifestId(ownerModId);
            objectKey = ModKeySyntax.requireDisplayKey(objectKey);
            if (!objectKey.startsWith(ownerModId + ":")) {
                throw new IllegalArgumentException("Object key owner does not match spawn owner");
            }
        }
    }

    /** Backward-compatible stock placement constructor. */
    public ObjectSpawn(int x, int y, int objectId, int subtype, int renderFlags,
                       boolean respawnTracked, int rawYWord, int layoutIndex) {
        this(x, y, objectId, subtype, renderFlags, respawnTracked, rawYWord, layoutIndex, null, null);
    }

    /**
     * Convenience constructor for dynamically spawned objects (boss children,
     * projectiles, effects, etc.) that don't come from the ROM layout list.
     * Sets {@code layoutIndex} to {@code -1}.
     */
    public ObjectSpawn(int x, int y, int objectId, int subtype,
                       int renderFlags, boolean respawnTracked, int rawYWord) {
        this(x, y, objectId, subtype, renderFlags, respawnTracked, rawYWord, -1, null, null);
    }

    public int rawFlags() {
        return rawYWord & 0xF000;
    }
}
