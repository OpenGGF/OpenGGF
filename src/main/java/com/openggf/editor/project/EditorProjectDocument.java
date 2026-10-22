package com.openggf.editor.project;

import com.openggf.editor.EditorCommand;
import com.openggf.editor.assets.EditorArtImport;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.Palette;
import com.openggf.level.MutableLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.Objects;

/** Durable authored data and saved revision, retained across transient playtest sessions. */
public final class EditorProjectDocument {
    private EditorProjectMetadata metadata;
    private MutableLevel level;
    private Path path;
    private EditorProjectMetadata savedMetadata;
    private EditorProjectLevel savedLevel;
    private EditorProjectAssets assets = EditorProjectAssets.EMPTY;
    private EditorProjectAssets savedAssets;

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
    public BitSet importedPatternSlots() { return assets.slots(); }
    EditorProjectAssets assets() { return assets; }
    void restoreAssets(EditorProjectAssets value) {
        Objects.requireNonNull(value, "assets").validateAgainst(level.getPatternCount());
        assets = value;
    }

    /** Duplicate authored assets, including unused transparent imported tiles, without a savepoint. */
    public EditorProjectDocument duplicate(EditorProjectMetadata value) {
        var candidate = create(value, level);
        candidate.assets = assets;
        return candidate;
    }

    public record ArtImport(EditorCommand command, List<Integer> patternIndices, int frameCount) {
        public ArtImport { patternIndices = List.copyOf(patternIndices); }
    }

    /** Decode first; the returned history step claims its tiles only on a successful apply. */
    public ArtImport prepareArtImport(Path folder, Path png, Path sheet, boolean importPalette,
            BiPredicate<Integer, Integer> editablePaletteCell, IntConsumer refreshPalette,
            Consumer<List<Integer>> refreshArt) throws IOException {
        Objects.requireNonNull(refreshArt, "refreshArt");
        MutableLevel target = level;
        EditorProjectAssets previous = assets;
        var prepared = EditorArtImport.prepare(folder, png, sheet, target, previous.slots(), importPalette,
                editablePaletteCell, refreshPalette);
        EditorProjectAssets replacement = previous.plus(prepared.patternIndices());
        EditorCommand command = new EditorCommand() {
            @Override public void apply() { change(previous, replacement, true); }
            @Override public void undo() { change(replacement, previous, false); }

            private void change(EditorProjectAssets expected, EditorProjectAssets next, boolean applying) {
                if (level != target || !assets.equals(expected))
                    throw new IllegalStateException("The project or imported tile reservations changed");
                // Keep a small byte snapshot so a graphics refresh failure cannot leave
                // imported data or ownership half-applied, even if a recovery upload fails.
                Pattern[] patterns = new Pattern[prepared.patternIndices().size()];
                for (int i = 0; i < patterns.length; i++) {
                    patterns[i] = new Pattern();
                    patterns[i].copyFrom(target.getPattern(prepared.patternIndices().get(i)));
                }
                int line = prepared.importsPalette() ? prepared.paletteLine() : -1;
                Palette palette = line < 0 ? null : target.getPalette(line).deepCopy();
                boolean changed = false;
                try {
                    if (applying) prepared.command().apply(); else prepared.command().undo();
                    changed = true;
                    assets = next;
                    refreshArt.accept(prepared.patternIndices());
                } catch (RuntimeException failure) {
                    if (!changed) throw failure;
                    for (int i = 0; i < patterns.length; i++) target.setPattern(prepared.patternIndices().get(i), patterns[i]);
                    if (palette != null) target.setPalette(line, palette);
                    assets = expected;
                    try { if (palette != null) refreshPalette.accept(line); }
                    catch (RuntimeException restoreFailure) { if (restoreFailure != failure) failure.addSuppressed(restoreFailure); }
                    try { refreshArt.accept(prepared.patternIndices()); }
                    catch (RuntimeException restoreFailure) { if (restoreFailure != failure) failure.addSuppressed(restoreFailure); }
                    throw failure;
                }
            }
        };
        return new ArtImport(command, prepared.patternIndices(), prepared.frameCount());
    }

    /** Prepare a structural replacement without publishing it or losing its savepoint. */
    EditorProjectDocument copyForReplacement() {
        var candidate = create(metadata, level);
        candidate.path = path;
        candidate.savedMetadata = savedMetadata;
        candidate.savedLevel = savedLevel;
        candidate.assets = assets;
        candidate.savedAssets = savedAssets;
        return candidate;
    }

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
        assets.validateAgainst(replacement.getPatternCount());
        MutableLevel mutable = MutableLevel.snapshot(replacement);
        metadata = value; level = mutable;
    }

    public void resize(int width, int height, boolean allowCrop) {
        int size = level.getBlockPixelSize();
        int rightMargin = retainedCameraInset(level.getMap().getWidth() * size, metadata.maxX(), 320);
        int bottomMargin = retainedCameraInset(level.getMap().getHeight() * size, metadata.maxY(), 224);
        EditorProjectLevel resized = EditorProjectLevel.resized(level, width, height, allowCrop);
        int maxX = Math.max(0, width * size - 1 - rightMargin);
        int maxY = Math.max(0, height * size - 1 - bottomMargin);
        replace(metadata.withBounds(Math.min(metadata.minX(), maxX), maxX,
                Math.min(metadata.minY(), maxY), maxY), resized);
    }

    private static int retainedCameraInset(int canvasPixels, int maximum, int nativeViewportPixels) {
        // A native camera cannot have a negative origin. On a canvas smaller than
        // its viewport, zero is the clipped camera limit, not evidence that the
        // viewport shrank. Recover the canonical inset when growing it again.
        // Full-canvas bounds have a positive maximum and keep their zero inset.
        return maximum == 0 && canvasPixels < nativeViewportPixels
                ? nativeViewportPixels - 1 : canvasPixels - 1 - maximum;
    }

    public EditorProjectLevel snapshot() {
        metadata.validateAgainst(level);
        return EditorProjectLevel.copyOf(level);
    }

    public boolean isModified() {
        return savedLevel == null || !metadata.equals(savedMetadata) || !assets.equals(savedAssets) || !savedLevel.contentEquals(level);
    }

    void markSaved(Path file, EditorProjectMetadata persistedMetadata, EditorProjectLevel persistedLevel) {
        savedAssets = assets;
        savedMetadata = persistedMetadata;
        savedLevel = EditorProjectLevel.copyOf(persistedLevel);
        path = file.toAbsolutePath().normalize();
        if (!isModified()) level.markSaved();
    }
}
