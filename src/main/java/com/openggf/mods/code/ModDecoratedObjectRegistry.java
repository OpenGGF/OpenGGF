package com.openggf.mods.code;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectWindowingStrategy;

import java.util.List;
import java.util.Objects;

/** Routes tagged mod spawns by namespaced key while leaving stock registry behavior untouched. */
public final class ModDecoratedObjectRegistry implements ObjectRegistry {
    private final ObjectRegistry base;
    private final ModObjectKeyRegistry modKeys;
    private final java.util.Map<String,String> previewArtKeys;

    public ModDecoratedObjectRegistry(ObjectRegistry base, ModObjectKeyRegistry modKeys) {
        this(base, modKeys, java.util.Map.of());
    }

    public ModDecoratedObjectRegistry(ObjectRegistry base, ModObjectKeyRegistry modKeys,
                                      java.util.Map<String,String> previewArtKeys) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(modKeys, "modKeys");
        if (base instanceof ModDecoratedObjectRegistry decorated) {
            this.base = decorated.base;
            this.modKeys = decorated.modKeys.mergedWith(modKeys);
            java.util.LinkedHashMap<String,String> merged=new java.util.LinkedHashMap<>(decorated.previewArtKeys);
            merged.putAll(previewArtKeys);this.previewArtKeys=java.util.Map.copyOf(merged);
        } else {
            this.base = base;
            this.modKeys = modKeys;
            this.previewArtKeys=java.util.Map.copyOf(previewArtKeys);
        }
    }

    @Override
    public ObjectInstance create(ObjectSpawn spawn) {
        Objects.requireNonNull(spawn, "spawn");
        if (spawn.objectKey() == null) {
            return base.create(spawn);
        }
        return modKeys.requireFactory(spawn.ownerModId(), spawn.objectKey()).create(spawn, this);
    }

    @Override public void reportCoverage(List<ObjectSpawn> spawns) { base.reportCoverage(spawns); }
    @Override public String getPrimaryName(int objectId) { return base.getPrimaryName(objectId); }
    @Override public ObjectSlotLayout objectSlotLayout() { return base.objectSlotLayout(); }
    @Override public ObjectWindowingStrategy objectWindowingStrategy() { return base.objectWindowingStrategy(); }
    @Override public List<String> getAliases(int objectId) { return base.getAliases(objectId); }
    @Override public boolean hasObjectKey(String objectKey) { return modKeys.contains(objectKey); }
    @Override public List<String> browsableObjectKeys() { return modKeys.keys(); }
    @Override public java.util.Optional<String> editorPreviewArtKey(int stockObjectId) {
        return base.editorPreviewArtKey(stockObjectId);
    }
    @Override public java.util.Optional<String> editorPreviewArtKey(String objectKey) {
        return java.util.Optional.ofNullable(previewArtKeys.get(objectKey));
    }
}
