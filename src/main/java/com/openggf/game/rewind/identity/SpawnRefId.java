package com.openggf.game.rewind.identity;

import com.openggf.game.ModKeySyntax;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

@com.openggf.game.ModApi
public record SpawnRefId(int layoutIndex, String ownerModId, String objectKey) {
    public SpawnRefId(int layoutIndex) {
        this(layoutIndex, null, null);
    }

    public SpawnRefId {
        if ((ownerModId == null) != (objectKey == null)) {
            throw new IllegalArgumentException("Spawn reference owner and key must both be present or absent");
        }
        if (objectKey != null) {
            ownerModId = ModKeySyntax.requireManifestId(ownerModId);
            objectKey = ModKeySyntax.requireDisplayKey(objectKey);
            String keyOwner = objectKey.substring(0, objectKey.indexOf(':'));
            if (!keyOwner.equals(ownerModId)) {
                throw new IllegalArgumentException("Spawn reference key owner mismatch");
            }
        }
    }

    public static SpawnRefId fromSpawn(ObjectSpawn spawn) {
        Objects.requireNonNull(spawn, "spawn");
        return new SpawnRefId(spawn.layoutIndex(), spawn.ownerModId(), spawn.objectKey());
    }
}
