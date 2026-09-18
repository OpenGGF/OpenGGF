package com.openggf.game.dataselect;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestPreviewImageFiles {
    @TempDir Path root;

    @Test
    void scalingUsesNearestNeighborAndEqualDimensionsProduceIndependentPixels() {
        var source = new RgbaImage(3, 2, new int[]{1, 2, 3, 4, 5, 6});
        assertArrayEquals(new int[]{1, 2, 1, 2, 4, 5},
                PreviewImageFiles.scaleToPreview(source, 2, 3).pixels());
        var copy = PreviewImageFiles.scaleToPreview(source, 3, 2);
        assertArrayEquals(source.pixels(), copy.pixels());
        copy.setArgb(0, 0, 99);
        assertEquals(1, source.argb(0, 0));
    }

    @Test
    void pngReplacementPreservesPixelsAndRemovesTemporaryFilesAfterFailure() throws Exception {
        Files.writeString(root.resolve("preview.png"), "old");
        PreviewImageFiles.writePng(new RgbaImage(1, 1, new int[]{0xffabcdef}), root, "preview", "preview.png");
        assertEquals(0xffabcdef, ScreenshotCapture.loadPNG(root.resolve("preview.png")).argb(0, 0));
        Files.createDirectory(root.resolve("blocked"));
        Files.writeString(root.resolve("blocked/child"), "keep");
        assertThrows(IOException.class, () -> PreviewImageFiles.writePng(
                new RgbaImage(1, 1, new int[]{0}), root, "preview", "blocked"));
        try (var paths = Files.list(root)) {
            assertEquals(2, paths.count());
        }
        assertEquals("keep", Files.readString(root.resolve("blocked/child")));
    }
}
