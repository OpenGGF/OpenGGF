package com.openggf.stageplay.runtime;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.stageplay.StageplayDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared scene-relative asset boundary for authoring, capture and prepared mod content. */
public final class StageplayAssets {
    public static final long MAX_ENCODED_BYTES = 32L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public static final int MAX_ASSETS = 256;

    @FunctionalInterface public interface Reader { byte[] read(String relativePath) throws IOException; }

    private StageplayAssets() { }

    /** Bounded real-path-confined read; symlinks cannot escape the saved scene directory. */
    public static byte[] read(Path projectRoot, String relativePath) throws IOException {
        ModAssetRoot.requireNormalizedEntry(relativePath);
        if (projectRoot == null) throw new IOException("Save the scene before loading relative assets");
        Path root = projectRoot.toRealPath();
        Path file = root.resolve(relativePath).toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Asset escapes scene directory: " + relativePath);
        long maximum = Math.min(ModInputLimits.production().maxAssetBytes(), MAX_ENCODED_BYTES);
        if (Files.size(file) > maximum) throw new IOException("Scene asset exceeds encoded size limit: " + relativePath);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maximum + 1));
            if (bytes.length > maximum) throw new IOException("Scene asset exceeds encoded size limit: " + relativePath);
            return bytes;
        }
    }

    /** All declared dialogue image dependencies, once each, in stable document order. */
    public static List<String> imagePaths(StageplayDocument document) {
        var paths = new LinkedHashSet<String>();
        for (var skin : document.dialogueSkins()) if (!skin.decorationAsset().isEmpty()) paths.add(skin.decorationAsset());
        for (var cue : document.dialogue()) if (!cue.portraitAsset().isEmpty()) paths.add(cue.portraitAsset());
        return List.copyOf(paths);
    }
}
