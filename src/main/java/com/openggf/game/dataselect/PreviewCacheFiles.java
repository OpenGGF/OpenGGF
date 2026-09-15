package com.openggf.game.dataselect;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.version.AppVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** File validation and decoding shared by the donated host preview caches. */
public final class PreviewCacheFiles {
    private PreviewCacheFiles() { }

    /** Internal view; serialized game-specific manifests remain unchanged. */
    public record Manifest(String engineVersion, int generatorFormatVersion,
                           String romSha256, Map<String, String> zones) { }

    public static boolean valid(Path cacheRoot, Manifest manifest, int formatVersion,
                                Supplier<String> romSha256, Set<String> expectedZones,
                                int width, int height) {
        if (!AppVersion.get().equals(manifest.engineVersion())) {
            return false;
        }
        if (manifest.generatorFormatVersion() != formatVersion) {
            return false;
        }
        if (!Objects.equals(romSha256.get(), manifest.romSha256())) {
            return false;
        }

        if (manifest.zones() == null || !expectedZones.equals(manifest.zones().keySet())) {
            return false;
        }
        for (String relativePath : manifest.zones().values()) {
            if (relativePath == null || relativePath.isBlank()) {
                return false;
            }
            Path imagePath = cacheRoot.resolve(relativePath);
            if (Files.notExists(imagePath)) {
                return false;
            }
            if (!isDecodablePng(imagePath, width, height)) {
                return false;
            }
        }
        return true;
    }

    public static Map<Integer, RgbaImage> load(Path cacheRoot, Map<String, String> zones,
                                              List<Integer> zoneIds, IntFunction<String> zoneKeyForId) {
        Map<Integer, RgbaImage> previews = new LinkedHashMap<>();
        for (int zoneId : zoneIds) {
            String zoneKey = zoneKeyForId.apply(zoneId);
            if (zoneKey == null) {
                return Map.of();
            }
            String relativePath = zones.get(zoneKey);
            if (relativePath == null || relativePath.isBlank()) {
                return Map.of();
            }
            try {
                previews.put(zoneId, ScreenshotCapture.loadPNG(cacheRoot.resolve(relativePath)));
            } catch (IOException e) {
                return Map.of();
            }
        }
        return Map.copyOf(previews);
    }

    private static boolean isDecodablePng(Path imagePath, int width, int height) {
        try {
            RgbaImage image = ScreenshotCapture.loadPNG(imagePath);
            return image.width() == width
                    && image.height() == height;
        } catch (IOException e) {
            return false;
        }
    }
}
