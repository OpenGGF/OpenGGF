package com.openggf.tools.fbzvisual;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Normalized output paths for one checkpoint/mode capture. */
public record FbzVisualCapturePaths(Path fullPng, Path nativeCropPng, Path receipt) {

    public FbzVisualCapturePaths {
        fullPng = normalized(fullPng, "fullPng");
        nativeCropPng = normalized(nativeCropPng, "nativeCropPng");
        receipt = normalized(receipt, "receipt");
        if (receipt.equals(fullPng) || receipt.equals(nativeCropPng)) {
            throw new IllegalArgumentException("FBZ receipt path aliases an image path: " + receipt);
        }
    }

    public Set<Path> allFiles() {
        Set<Path> files = new LinkedHashSet<>();
        files.add(fullPng);
        files.add(nativeCropPng);
        files.add(receipt);
        return Set.copyOf(files);
    }

    public static void requireNoAliases(Collection<FbzVisualCapturePaths> captures) {
        Objects.requireNonNull(captures, "captures");
        Map<Path, Integer> owners = new LinkedHashMap<>();
        int owner = 0;
        for (FbzVisualCapturePaths capture : captures) {
            Objects.requireNonNull(capture, "capture");
            for (Path path : capture.allFiles()) {
                Integer previous = owners.putIfAbsent(path, owner);
                if (previous != null && previous != owner) {
                    throw new IllegalArgumentException("FBZ capture output alias: " + path);
                }
            }
            owner++;
        }
    }

    private static Path normalized(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
