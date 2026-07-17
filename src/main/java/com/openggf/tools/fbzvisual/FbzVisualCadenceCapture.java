package com.openggf.tools.fbzvisual;

import com.openggf.graphics.RgbaImage;
import com.openggf.level.Pattern;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact engine-side capture contract for one FBZ AniPLC destination. */
final class FbzVisualCadenceCapture {

    private FbzVisualCadenceCapture() {
    }

    static Spec spec(String checkpoint) {
        return switch (checkpoint) {
            case "fbz1-aniplc-200" -> new Spec(checkpoint, "aniplc-cadence-200-v2", 3, 0x200, 8, 7);
            case "fbz1-aniplc-208" -> new Spec(checkpoint, "aniplc-cadence-208-v2", 4, 0x208, 8, 7);
            case "fbz1-aniplc-210" -> new Spec(checkpoint, "aniplc-cadence-210-v2", 0, 0x210, 0x20, 63);
            case "fbz1-aniplc-230" -> new Spec(checkpoint, "aniplc-cadence-230-v2", 1, 0x230, 8, 7);
            case "fbz1-aniplc-238" -> new Spec(checkpoint, "aniplc-cadence-238-v2", 2, 0x238, 0x10, 1);
            default -> throw new IllegalArgumentException("Not an FBZ AniPLC checkpoint: " + checkpoint);
        };
    }

    static String sha256Patterns(List<Pattern> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        byte[] packed = new byte[patterns.size() * Pattern.PATTERN_SIZE_IN_ROM];
        int cursor = 0;
        for (Pattern pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x += 2) {
                    int high = pattern.getPixel(x, y) & 0x0F;
                    int low = pattern.getPixel(x + 1, y) & 0x0F;
                    packed[cursor++] = (byte) ((high << 4) | low);
                }
            }
        }
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(packed));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static String hashDestinationPatterns(Spec spec, com.openggf.level.Level level) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(level, "level");
        List<Pattern> patterns = new ArrayList<>(spec.tileCount());
        for (int index = 0; index < spec.tileCount(); index++) {
            int patternIndex = spec.destinationTile() + index;
            if (patternIndex >= level.getPatternCount()) {
                throw new IllegalStateException("FBZ cadence destination exceeds pattern memory: "
                        + Integer.toHexString(patternIndex));
            }
            patterns.add(level.getPattern(patternIndex));
        }
        return sha256Patterns(patterns);
    }

    static boolean reviewedRegionChanged(RgbaImage before, RgbaImage after,
                                         FbzVisualEvidenceAmendment.VisibleRegion region) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(region, "region");
        if (before.width() != after.width() || before.height() != after.height()) {
            throw new IllegalArgumentException("FBZ cadence crops have different dimensions");
        }
        region.requireInside(before.width(), before.height());
        for (int y = region.y(); y < region.y() + region.height(); y++) {
            for (int x = region.x(); x < region.x() + region.width(); x++) {
                if (before.argb(x, y) != after.argb(x, y)) return true;
            }
        }
        return false;
    }

    static FramePaths paths(Path outputRoot, String series, int index, String control) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        requireSafe(series, "series");
        requireSafe(control, "control");
        if (index < 0) throw new IllegalArgumentException("Negative FBZ cadence index");
        Path normalized = outputRoot.toAbsolutePath().normalize();
        String leaf = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
        Path modeRoot = leaf.equals("native-pre-compat") || leaf.equals("native-post-compat")
                ? normalized : normalized.resolve("native-pre-compat");
        Path engineRoot = modeRoot.resolve("time-series/engine");
        String stem = "%s-%s-%02d".formatted(series, control, index);
        return new FramePaths(engineRoot.resolve(stem + ".png"),
                engineRoot.resolve("provenance").resolve(stem + ".json"));
    }

    private static void requireSafe(String value, String name) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("Unsafe FBZ cadence " + name + ": " + value);
        }
    }

    record Spec(String checkpoint, String series, int channel, int destinationTile,
                int tileCount, int resetTimer) {
    }

    record FramePaths(Path png, Path receipt) {
    }

    record FramePublication(Path png, Path receipt, byte[] pngBytes, Map<String, Object> provenance) {
        FramePublication {
            Objects.requireNonNull(png, "png");
            Objects.requireNonNull(receipt, "receipt");
            pngBytes = Objects.requireNonNull(pngBytes, "pngBytes").clone();
            provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
        }
        public byte[] pngBytes() { return pngBytes.clone(); }
    }
}
