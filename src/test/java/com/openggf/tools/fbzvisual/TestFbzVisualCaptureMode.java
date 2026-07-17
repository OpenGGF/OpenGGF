package com.openggf.tools.fbzvisual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzVisualCaptureMode {

    @TempDir
    Path tempDir;

    @Test
    void resolvesEveryReviewedFramebufferAndNativeCrop() {
        assertGeometry("native-320", 320, 0);
        assertGeometry("widescreen-352", 352, 16);
        assertGeometry("widescreen-400", 400, 40);
        assertGeometry("widescreen-528", 528, 104);
        assertGeometry("widescreen-800", 800, 240);
    }

    @Test
    void rejectsGeometryThatDoesNotMatchModeTable() {
        assertThrows(IllegalArgumentException.class,
                () -> FbzVisualCaptureMode.resolve("widescreen-400", 400, 224, 39));
        assertThrows(IllegalArgumentException.class,
                () -> FbzVisualCaptureMode.resolve("widescreen-400", 400, 225, 40));
        assertThrows(IllegalArgumentException.class,
                () -> FbzVisualCaptureMode.resolve("widescreen-400", 528, 224, 104));
    }

    @Test
    void donationAndMultiSidekickModesRetainTheirDeclaredBaseGeometry() {
        assertGeometry("s1-donation-native", 320, 0);
        assertGeometry("s2-donation-native", 320, 0);
        assertGeometry("multi-sidekick-native", 320, 0);
        assertGeometry("multi-sidekick-duplicate-native", 320, 0);
        assertGeometry("widescreen-800+s1-donation+multi-sidekick-duplicate", 800, 240);
    }

    @Test
    void nativeAndCompatibilityOutputsCannotAlias() {
        Path nativeOutput = tempDir.resolve("target/fbz-validation/fbz1-start-outdoor.png");
        FbzVisualCapturePaths nativePaths = FbzVisualCaptureMode
                .resolve("native-320", 320, 224, 0)
                .paths(tempDir.resolve("target/fbz-validation"),
                        "fbz1-start-outdoor", nativeOutput);
        FbzVisualCapturePaths compatPaths = FbzVisualCaptureMode
                .resolve("widescreen-400", 400, 224, 40)
                .paths(tempDir.resolve("target/fbz-validation"),
                        "fbz1-start-outdoor", nativeOutput);

        assertEquals(nativeOutput.toAbsolutePath().normalize(), nativePaths.fullPng());
        assertEquals(nativePaths.fullPng(), nativePaths.nativeCropPng());
        assertEquals(tempDir.resolve("target/fbz-validation/compat/widescreen-400/"
                        + "fbz1-start-outdoor/full-400x224.png").toAbsolutePath().normalize(),
                compatPaths.fullPng());
        assertEquals(tempDir.resolve("target/fbz-validation/compat/widescreen-400/"
                        + "fbz1-start-outdoor/crop-320x224.png").toAbsolutePath().normalize(),
                compatPaths.nativeCropPng());
        assertEquals(tempDir.resolve("target/fbz-validation/compat/widescreen-400/"
                        + "fbz1-start-outdoor/receipt.json").toAbsolutePath().normalize(),
                compatPaths.receipt());

        assertFalse(nativePaths.allFiles().stream().anyMatch(compatPaths.allFiles()::contains));
        FbzVisualCapturePaths.requireNoAliases(List.of(nativePaths, compatPaths));
    }

    @Test
    void duplicateModeCheckpointOutputsAreRejectedBeforeRendering() {
        Path nativeOutput = tempDir.resolve("target/fbz-validation/fbz1-start-outdoor.png");
        FbzVisualCaptureMode mode = FbzVisualCaptureMode.resolve("widescreen-352", 352, 224, 16);
        FbzVisualCapturePaths first = mode.paths(tempDir, "fbz1-start-outdoor", nativeOutput);
        FbzVisualCapturePaths duplicate = mode.paths(tempDir, "fbz1-start-outdoor", nativeOutput);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> FbzVisualCapturePaths.requireNoAliases(List.of(first, duplicate)));
        assertTrue(failure.getMessage().contains("alias"));
    }

    private static void assertGeometry(String key, int width, int cropX) {
        FbzVisualCaptureMode mode = FbzVisualCaptureMode.resolve(key, width, 224, cropX);
        assertEquals(key, mode.key());
        assertEquals(width, mode.framebufferWidth());
        assertEquals(224, mode.framebufferHeight());
        assertEquals(cropX, mode.nativeCropX());
        assertEquals(0, mode.nativeCropY());
        assertEquals(320, mode.nativeCropWidth());
        assertEquals(224, mode.nativeCropHeight());
    }
}
