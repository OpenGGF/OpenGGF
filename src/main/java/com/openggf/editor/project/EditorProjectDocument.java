package com.openggf.editor.project;

import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import java.nio.file.Path;
import java.util.Objects;

/** Durable authored data and saved revision, retained across transient playtest sessions. */
public final class EditorProjectDocument {
    private EditorProjectMetadata metadata;
    private MutableLevel level;
    private Path path;
    private EditorProjectMetadata savedMetadata;
    private EditorProjectLevel savedLevel;

    private EditorProjectDocument(EditorProjectMetadata metadata, Level source) {
        replace(metadata, source);
    }

    public static EditorProjectDocument create(EditorProjectMetadata metadata, Level source) {
        return new EditorProjectDocument(metadata, source);
    }

    public static EditorProjectDocument cloneLevel(EditorProjectMetadata metadata, Level source) {
        return create(metadata, EditorProjectLevel.withSpareSlots(EditorProjectLevel.copyOf(source)));
    }

    public static EditorProjectDocument blank(EditorProjectMetadata metadata, Level library, int width, int height) {
        EditorProjectLevel blank = EditorProjectLevel.blank(library, width, height);
        return create(metadata.withBounds(blank.getMinX(), blank.getMaxX(), blank.getMinY(), blank.getMaxY()), blank);
    }

    public MutableLevel level() { return level; }
    public EditorProjectMetadata metadata() { return metadata; }
    public Path path() { return path; }

    public void setMetadata(EditorProjectMetadata value) {
        Objects.requireNonNull(value, "metadata").validateAgainst(level);
        boolean boundsChanged = value.minX() != level.getMinX() || value.maxX() != level.getMaxX()
                || value.minY() != level.getMinY() || value.maxY() != level.getMaxY();
        if (boundsChanged) replace(value, level);
        else metadata = value;
    }

    /** Atomic document replacement suitable for host-owned undoable property/resize commands. */
    public void replace(EditorProjectMetadata value, Level source) {
        Objects.requireNonNull(value, "metadata").validateAgainst(source);
        EditorProjectLevel replacement = EditorProjectLevel.withBounds(source, value.minX(), value.maxX(), value.minY(), value.maxY());
        MutableLevel mutable = MutableLevel.snapshot(replacement);
        metadata = value; level = mutable;
    }

    public void resize(int width, int height, boolean allowCrop) {
        EditorProjectLevel resized = EditorProjectLevel.resized(level, width, height, allowCrop);
        replace(metadata.withBounds(resized.getMinX(), resized.getMaxX(), resized.getMinY(), resized.getMaxY()), resized);
    }

    public EditorProjectLevel snapshot() {
        metadata.validateAgainst(level);
        return EditorProjectLevel.copyOf(level);
    }

    public boolean isModified() {
        return savedLevel == null || !metadata.equals(savedMetadata) || !savedLevel.contentEquals(level);
    }

    void markSaved(Path file, EditorProjectMetadata persistedMetadata, EditorProjectLevel persistedLevel) {
        savedMetadata = persistedMetadata;
        savedLevel = EditorProjectLevel.copyOf(persistedLevel);
        path = file.toAbsolutePath().normalize();
        if (!isModified()) level.markSaved();
    }
}
