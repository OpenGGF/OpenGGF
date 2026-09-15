package com.openggf.game.dataselect;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Exact scaling and file replacement operations for generated preview images. */
public final class PreviewImageFiles {
    private PreviewImageFiles() { }

    public static void writePng(RgbaImage preview, Path cacheRoot, String fileStem,
                                String fileName) throws IOException {
        Path tempPng = Files.createTempFile(cacheRoot, fileStem + "-", ".tmp");
        Path finalPng = cacheRoot.resolve(fileName);
        try {
            ScreenshotCapture.savePNG(preview, tempPng);
            moveAtomically(tempPng, finalPng);
        } finally {
            Files.deleteIfExists(tempPng);
        }
    }

    public static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static RgbaImage scaleToPreview(RgbaImage source, int width, int height) {
        if (source.width() == width && source.height() == height) {
            return source.copy();
        }
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(source.height() - 1, y * source.height() / height);
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(source.width() - 1, x * source.width() / width);
                pixels[y * width + x] = source.argb(sourceX, sourceY);
            }
        }
        return new RgbaImage(width, height, pixels);
    }

}
