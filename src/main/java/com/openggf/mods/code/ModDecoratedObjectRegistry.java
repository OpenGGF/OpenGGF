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

    public ModDecoratedObjectRegistry(ObjectRegistry base, ModObjectKeyRegistry modKeys) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(modKeys, "modKeys");
        if (base instanceof ModDecoratedObjectRegistry decorated) {
            this.base = decorated.base;
            this.modKeys = decorated.modKeys.mergedWith(modKeys);
        } else {
            this.base = base;
            this.modKeys = modKeys;
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
}
