package com.openggf.editor;

import com.openggf.level.objects.ObjectRegistry;
import com.openggf.i18n.UiMessages;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Logical stock-object palette shared by keyboard and gamepad input. */
public final class EditorStockObjectPalette {
    public enum Navigation { NEXT_OBJECT, PREVIOUS_OBJECT, INCREMENT_SUBTYPE, DECREMENT_SUBTYPE }

    private final ObjectRegistry registry;
    private final java.util.List<Entry> entries;
    private int cursor;
    private int subtype;
    private int renderFlags;
    private boolean respawnTracked=true;
    public int renderFlags(){return renderFlags;}
    public boolean respawnTracked(){return respawnTracked;}
    public void toggleRenderFlag(int mask){renderFlags^=mask&3;}
    public void toggleRespawnTracked(){respawnTracked=!respawnTracked;}

    public EditorStockObjectPalette(ObjectRegistry registry) {
        this(registry, registry::getPrimaryName);
    }

    public EditorStockObjectPalette(ObjectRegistry registry, java.util.function.IntFunction<String> names) {
        this.registry = Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(names, "names");
        java.util.ArrayList<Entry> built = new java.util.ArrayList<>(256 + registry.browsableObjectKeys().size());
        for (int id = 0; id <= 0xFF; id++) built.add(Entry.stock(id, names.apply(id),
                registry.editorPreviewArtKey(id).orElse(null)));
        registry.browsableObjectKeys().forEach(key -> built.add(Entry.keyed(key,
                registry.editorPreviewArtKey(key).orElse(null))));
        entries = java.util.List.copyOf(built);
    }

    public void navigate(Navigation navigation) {
        switch (Objects.requireNonNull(navigation, "navigation")) {
            case NEXT_OBJECT -> nextObject();
            case PREVIOUS_OBJECT -> previousObject();
            case INCREMENT_SUBTYPE -> subtype = (subtype + 1) & 0xFF;
            case DECREMENT_SUBTYPE -> subtype = (subtype - 1) & 0xFF;
        }
    }

    public void nextObject() { cursor = Math.floorMod(cursor + 1, entries.size()); }
    public void previousObject() { cursor = Math.floorMod(cursor - 1, entries.size()); }
    public int selectedObjectId() { return entries.get(cursor).stockObjectId() == null ? 0 : entries.get(cursor).stockObjectId(); }
    public String selectedObjectKey() { return entries.get(cursor).objectKey(); }
    public boolean selectedIsKeyed() { return selectedObjectKey() != null; }
    public void setObjectId(int objectId) {
        if (objectId < 0 || objectId > 0xFF) throw new IllegalArgumentException("objectId must be 0..255");
        this.cursor = objectId;
    }
    public void setObjectKey(String objectKey) {
        String key = com.openggf.game.ModKeySyntax.requireDisplayKey(objectKey);
        for (int i = 0; i < entries.size(); i++) if (key.equals(entries.get(i).objectKey())) { cursor=i; return; }
        throw new IllegalArgumentException("Object key is not enabled: " + key);
    }
    public int selectedSubtype() { return subtype; }
    public void setSubtype(int subtype) {
        if (subtype < 0 || subtype > 0xFF) throw new IllegalArgumentException("subtype must be 0..255");
        this.subtype = subtype;
    }
    public String selectedLabel() { return entries.get(cursor).label(); }
    public java.util.List<Entry> entries() { return entries; }
    public void eyedrop(ObjectSpawn spawn) {
        if (spawn.objectKey() == null) setObjectId(spawn.objectId()); else setObjectKey(spawn.objectKey());
        subtype = spawn.subtype();
        renderFlags=spawn.renderFlags();respawnTracked=spawn.respawnTracked();
    }
    /** Query-only: previews never execute registry factories or gameplay initialization. */
    public com.openggf.level.metadata.ObjectPreview preview(ObjectSpawn spawn) {
        if (registry instanceof com.openggf.level.metadata.ObjectPreviewProvider provider) return provider.objectPreview(spawn);
        var key = spawn.objectKey() == null ? registry.editorPreviewArtKey(spawn.objectId()) : registry.editorPreviewArtKey(spawn.objectKey());
        return key.map(value -> com.openggf.level.metadata.ObjectPreview.frame(value, 0))
                .orElseGet(com.openggf.level.metadata.ObjectPreview::empty);
    }
    public static String displayName(String value) {
        if (value.startsWith("openggf:")) value = value.substring(8);
        value = value.replaceAll("^[0-9A-Fa-f]{2}: ", "").replace('_', ' ').replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z0-9])([A-Z][a-z])", "$1 $2");
        return value.isEmpty() ? UiMessages.text("editor.object.unnamed") : value;
    }
    public ObjectSpawn previewSpawn(Entry entry, int subtype, int flags) {
        return new ObjectSpawn(0, 0, entry.stockObjectId() == null ? 0 : entry.stockObjectId(), subtype, flags,
                false, 0, -1, entry.objectKey() == null ? null : entry.objectKey().split(":", 2)[0], entry.objectKey());
    }
    public ObjectSpawn brushPreview(int x, int y) {
        var spawn = previewSpawn(entries.get(cursor), subtype, renderFlags);
        return new ObjectSpawn(x, y, spawn.objectId(), subtype, renderFlags, false, y, -1, spawn.ownerModId(), spawn.objectKey());
    }
    public record Entry(Integer stockObjectId, String objectKey, String label, String previewArtKey) {
        static Entry stock(int id, String name,String preview) { return new Entry(id, null, "%02X: %s".formatted(id, displayName(name)),preview); }
        static Entry keyed(String key,String preview) { return new Entry(null, key, displayName(key),preview); }
    }
}
