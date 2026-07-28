package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Inspects hostile PNG dimensions and format before allocating decoded pixels. */
final class TmxPngReader {
    PixelImage read(Path path, int expectedWidth, int expectedHeight, ModInputLimits limits)
            throws IOException {
        byte[] encoded = Files.readAllBytes(path);
        // Geometry is checked from the header so a hostile declaration never
        // reaches a pixel allocation.
        PngCodec.Info info = PngCodec.info(encoded);
        if (info.width() != expectedWidth || info.height() != expectedHeight
                || info.width() > limits.maxImageWidth() || info.height() > limits.maxImageHeight()
                || (long) info.width() * info.height() > limits.maxImagePixels()) {
            throw new IOException("Tileset image geometry or limits do not match its declaration");
        }
        return PngCodec.decode(encoded);
    }
}
