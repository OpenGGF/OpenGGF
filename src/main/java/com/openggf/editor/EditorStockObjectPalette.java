package com.openggf.editor;

import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Logical stock-object palette shared by keyboard and gamepad input. */
public final class EditorStockObjectPalette {
    public enum Navigation { NEXT_OBJECT, PREVIOUS_OBJECT, INCREMENT_SUBTYPE, DECREMENT_SUBTYPE }

    private final ObjectRegistry registry;
    private int objectId;
    private int subtype;

    public EditorStockObjectPalette(ObjectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void navigate(Navigation navigation) {
        switch (Objects.requireNonNull(navigation, "navigation")) {
            case NEXT_OBJECT -> nextObject();
            case PREVIOUS_OBJECT -> previousObject();
            case INCREMENT_SUBTYPE -> subtype = (subtype + 1) & 0xFF;
            case DECREMENT_SUBTYPE -> subtype = (subtype - 1) & 0xFF;
        }
    }

    public void nextObject() { objectId = (objectId + 1) & 0xFF; }
    public void previousObject() { objectId = (objectId - 1) & 0xFF; }
    public int selectedObjectId() { return objectId; }
    public void setObjectId(int objectId) {
        if (objectId < 0 || objectId > 0xFF) throw new IllegalArgumentException("objectId must be 0..255");
        this.objectId = objectId;
    }
    public int selectedSubtype() { return subtype; }
    public void setSubtype(int subtype) {
        if (subtype < 0 || subtype > 0xFF) throw new IllegalArgumentException("subtype must be 0..255");
        this.subtype = subtype;
    }
    public String selectedLabel() { return "%02X: %s".formatted(objectId, registry.getPrimaryName(objectId)); }
    public void eyedrop(ObjectSpawn spawn) {
        objectId = spawn.objectId();
        subtype = spawn.subtype();
    }
}
