package com.openggf.mods.code;

import com.openggf.game.mutators.MonitorContent;
import com.openggf.level.objects.MutatorPlacementClassifier;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectFactory;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectWindowingStrategy;

import java.util.List;
import java.util.Objects;

/** Routes tagged mod spawns by namespaced key while leaving stock registry behavior untouched. */
public final class ModDecoratedObjectRegistry implements ObjectRegistry, MutatorPlacementClassifier,
        java.util.function.Supplier<Object>, com.openggf.level.objects.HostObjectKeyRegistry, com.openggf.editor.properties.ObjectPropertyProvider, com.openggf.editor.palette.EditorPalettePolicyProvider {
    private final ObjectRegistry base;
    private final ModObjectKeyRegistry modKeys;
    private final java.util.Map<String,String> previewArtKeys;
    private final ModFaultBoundary boundary;
    private final ObjectCallbackOwnerLookup ownerLookup = new ObjectCallbackOwnerLookup();

    public ModDecoratedObjectRegistry(ObjectRegistry base, ModObjectKeyRegistry modKeys) {
        this(base, modKeys, java.util.Map.of(), null);
    }

    public ModDecoratedObjectRegistry(ObjectRegistry base, ModObjectKeyRegistry modKeys,
                                      java.util.Map<String,String> previewArtKeys) {
        this(base, modKeys, previewArtKeys, null);
    }

    ModDecoratedObjectRegistry(ObjectRegistry base, ModObjectKeyRegistry modKeys,
                               java.util.Map<String,String> previewArtKeys,
                               ModFaultBoundary boundary) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(modKeys, "modKeys");
        this.boundary = boundary;
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
        if(hasHostObjectKey(spawn.objectKey())) return base.create(spawn);
        String owner = spawn.ownerModId();
        ObjectFactory factory = modKeys.requireFactory(owner, spawn.objectKey());
        if (boundary == null) return factory.create(spawn, this);
        return boundary.call(owner, () -> ownerLookup.remember(owner, factory.create(spawn, this)));
    }

    @Override public boolean isPaletteCellEditable(int line, int color) {
        return base instanceof com.openggf.editor.palette.EditorPalettePolicyProvider provider
                && provider.isPaletteCellEditable(line, color);
    }

    @Override public com.openggf.editor.properties.ObjectPropertySchema objectProperties(int stockObjectId) {
        return base instanceof com.openggf.editor.properties.ObjectPropertyProvider provider
                ? provider.objectProperties(stockObjectId) : com.openggf.editor.properties.ObjectPropertySchema.FALLBACK;
    }

    @Override public MonitorContent monitorContent(ObjectSpawn spawn) {
        return spawn.ownerModId() == null && base instanceof MutatorPlacementClassifier classifier
                ? classifier.monitorContent(spawn) : null;
    }
    @Override public boolean isRingPlacement(ObjectSpawn spawn) {
        return spawn.ownerModId() == null && base instanceof MutatorPlacementClassifier classifier
                && classifier.isRingPlacement(spawn);
    }

    @Override public void reportCoverage(List<ObjectSpawn> spawns) { base.reportCoverage(spawns); }
    @Override public String getPrimaryName(int objectId) { return base.getPrimaryName(objectId); }
    @Override public ObjectSlotLayout objectSlotLayout() { return base.objectSlotLayout(); }
    @Override public ObjectWindowingStrategy objectWindowingStrategy() { return base.objectWindowingStrategy(); }
    @Override public void installFixedSstObjects(int romZoneId, int act,
                                               com.openggf.level.objects.FixedSstSlotSink slots) {
        base.installFixedSstObjects(romZoneId, act, slots);
    }
    @Override public List<String> getAliases(int objectId) { return base.getAliases(objectId); }
    @Override public boolean hasObjectKey(String objectKey) { return hasHostObjectKey(objectKey) || modKeys.contains(objectKey); }
    @Override public boolean hasHostObjectKey(String key) {
        var authority=com.openggf.level.objects.HostObjectKeyRegistry.trusted(base);
        return authority!=null && authority.hasHostObjectKey(key);
    }
    @Override public boolean ownsHostObject(ObjectSpawn spawn,ObjectInstance instance) {
        var authority=com.openggf.level.objects.HostObjectKeyRegistry.trusted(base);
        return authority!=null && authority.ownsHostObject(spawn,instance);
    }
    @Override public List<String> browsableObjectKeys() {
        var keys=new java.util.LinkedHashSet<String>(base.browsableObjectKeys()); keys.addAll(modKeys.keys());
        return List.copyOf(keys);
    }
    @Override public java.util.Optional<String> editorPreviewArtKey(int stockObjectId) {
        return base.editorPreviewArtKey(stockObjectId);
    }
    @Override public java.util.Optional<String> editorPreviewArtKey(String objectKey) {
        return hasHostObjectKey(objectKey)?base.editorPreviewArtKey(objectKey):java.util.Optional.ofNullable(previewArtKeys.get(objectKey));
    }

    @Override public Object get() {
        return boundary == null ? null : java.util.Map.entry(boundary, ownerLookup);
    }
}
