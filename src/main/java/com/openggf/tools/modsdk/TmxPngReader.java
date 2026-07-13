package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Inspects hostile PNG dimensions and format before allocating decoded pixels. */
final class TmxPngReader {
    BufferedImage read(Path path, int expectedWidth, int expectedHeight, ModInputLimits limits) throws IOException {
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Tileset image is not readable");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Tileset image is not a PNG");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!"png".equalsIgnoreCase(reader.getFormatName()) || width != expectedWidth || height != expectedHeight
                        || width > limits.maxImageWidth() || height > limits.maxImageHeight()
                        || (long) width * height > limits.maxImagePixels()) {
                    throw new IOException("Tileset image geometry or limits do not match its declaration");
                }
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
