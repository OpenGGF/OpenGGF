package com.openggf.tools.fbzvisual;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Reviewed framebuffer geometry and output routing for an FBZ visual mode. */
public record FbzVisualCaptureMode(
        String key,
        int framebufferWidth,
        int framebufferHeight,
        int nativeCropX,
        int nativeCropY,
        int nativeCropWidth,
        int nativeCropHeight) {

    private static final int HEIGHT = 224;
    private static final int NATIVE_WIDTH = 320;
    private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9][a-z0-9+_-]*");
    private static final Map<String, Geometry> BASE_GEOMETRIES = Map.of(
            "native-320", new Geometry(320, 0),
            "widescreen-352", new Geometry(352, 16),
            "widescreen-400", new Geometry(400, 40),
            "widescreen-528", new Geometry(528, 104),
            "widescreen-800", new Geometry(800, 240),
            "multi-sidekick-native", new Geometry(320, 0),
            "multi-sidekick-duplicate-native", new Geometry(320, 0),
            "s1-donation-native", new Geometry(320, 0),
            "s2-donation-native", new Geometry(320, 0));

    public static FbzVisualCaptureMode resolve(String key, int width, int height, int cropX) {
        Objects.requireNonNull(key, "key");
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Unsafe FBZ visual mode key: " + key);
        }
        Geometry geometry = geometryFor(key);
        if (width != geometry.width || height != HEIGHT || cropX != geometry.cropX) {
            throw new IllegalArgumentException("FBZ visual mode geometry mismatch for " + key
                    + ": expected " + geometry.width + "x" + HEIGHT + " cropX=" + geometry.cropX
                    + ", got " + width + "x" + height + " cropX=" + cropX);
        }
        return new FbzVisualCaptureMode(key, width, height, cropX, 0, NATIVE_WIDTH, HEIGHT);
    }

    public boolean nativeMode() {
        return "native-320".equals(key);
    }

    public FbzVisualCapturePaths paths(Path outputRoot, String checkpointId, Path manifestNativeOutput) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(checkpointId, "checkpointId");
        Objects.requireNonNull(manifestNativeOutput, "manifestNativeOutput");
        if (!SAFE_KEY.matcher(checkpointId).matches()) {
            throw new IllegalArgumentException("Unsafe FBZ checkpoint id: " + checkpointId);
        }
        if (nativeMode()) {
            Path image = manifestNativeOutput.toAbsolutePath().normalize();
            Path receipt = outputRoot.resolve("provenance/native-320")
                    .resolve(checkpointId + ".json");
            return new FbzVisualCapturePaths(image, image, receipt);
        }
        Path directory = outputRoot.resolve("compat").resolve(key).resolve(checkpointId);
        return new FbzVisualCapturePaths(
                directory.resolve("full-" + framebufferWidth + "x" + framebufferHeight + ".png"),
                directory.resolve("crop-320x224.png"),
                directory.resolve("receipt.json"));
    }

    private static Geometry geometryFor(String key) {
        Geometry exact = BASE_GEOMETRIES.get(key);
        if (exact != null) {
            return exact;
        }
        int suffix = key.indexOf('+');
        if (suffix > 0) {
            Geometry base = BASE_GEOMETRIES.get(key.substring(0, suffix));
            if (base != null) {
                return base;
            }
        }
        throw new IllegalArgumentException("Unknown FBZ visual mode key: " + key);
    }

    private record Geometry(int width, int cropX) {
    }
}
