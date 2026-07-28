package com.openggf.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Pure-Java PNG decode/encode.
 *
 * <p>Neither AWT/ImageIO nor a native decoder: AWT is barred so native images
 * stay buildable, and the mod SDK is a CLI a creator runs on a plain JDK, so it
 * must not require platform natives on the classpath either. PNG is zlib plus a
 * small set of row filters, all of which {@link Inflater} and {@link Deflater}
 * already cover.
 *
 * <p>Scope is what creator art actually uses: non-interlaced, 8-bit greyscale,
 * greyscale+alpha, RGB, RGBA, and palette. Anything else is rejected by name
 * rather than silently mis-decoded.
 */
public final class PngCodec {

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngCodec() {
    }

    /** Width and height of an encoded PNG, read from its header only. */
    public record Info(int width, int height) {
    }

    public static Info info(byte[] encoded) throws IOException {
        Header header = readHeader(encoded);
        return new Info(header.width, header.height);
    }

    public static PixelImage decode(Path path) throws IOException {
        return decode(Files.readAllBytes(Objects.requireNonNull(path, "path")));
    }

    public static PixelImage decode(byte[] encoded) throws IOException {
        Header header = readHeader(encoded);
        byte[] palette = null;
        byte[] paletteAlpha = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        int cursor = SIGNATURE.length;
        while (cursor + 8 <= encoded.length) {
            int length = readInt(encoded, cursor);
            if (length < 0 || cursor + 12L + length > encoded.length) {
                throw new IOException("PNG chunk overruns the file");
            }
            String type = new String(encoded, cursor + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int body = cursor + 8;
            switch (type) {
                case "PLTE" -> palette = java.util.Arrays.copyOfRange(encoded, body, body + length);
                case "tRNS" -> paletteAlpha = java.util.Arrays.copyOfRange(encoded, body, body + length);
                case "IDAT" -> data.write(encoded, body, length);
                default -> { }
            }
            cursor = body + length + 4;
            if ("IEND".equals(type)) {
                break;
            }
        }
        if (data.size() == 0) {
            throw new IOException("PNG has no image data");
        }

        byte[] raw = inflate(data.toByteArray(), header);
        return unfilter(raw, header, palette, paletteAlpha);
    }

    public static void write(Path path, PixelImage image) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(image, "image");
        int width = image.getWidth();
        int height = image.getHeight();

        // Filter type 0 on every row: these are small generated assets, so the
        // extra compression a filter search would buy is not worth the surface.
        byte[] rows = new byte[height * (1 + width * 4)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            rows[at++] = 0;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                rows[at++] = (byte) ((argb >> 16) & 0xFF);
                rows[at++] = (byte) ((argb >> 8) & 0xFF);
                rows[at++] = (byte) (argb & 0xFF);
                rows[at++] = (byte) ((argb >>> 24) & 0xFF);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SIGNATURE, 0, SIGNATURE.length);
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(width).putInt(height).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        writeChunk(out, "IHDR", ihdr.array());
        writeChunk(out, "IDAT", deflate(rows));
        writeChunk(out, "IEND", new byte[0]);

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, out.toByteArray());
    }

    private record Header(int width, int height, int bitDepth, int colorType, int channels) {
    }

    private static Header readHeader(byte[] encoded) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length < SIGNATURE.length + 25) {
            throw new IOException("Input is too short to be a PNG");
        }
        for (int index = 0; index < SIGNATURE.length; index++) {
            if (encoded[index] != SIGNATURE[index]) {
                throw new IOException("Input is not a PNG");
            }
        }
        if (readInt(encoded, 8) != 13
                || !"IHDR".equals(new String(encoded, 12, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("PNG does not begin with IHDR");
        }
        int width = readInt(encoded, 16);
        int height = readInt(encoded, 20);
        int bitDepth = encoded[24] & 0xFF;
        int colorType = encoded[25] & 0xFF;
        int interlace = encoded[28] & 0xFF;
        if (width <= 0 || height <= 0) {
            throw new IOException("PNG has non-positive extents");
        }
        if (bitDepth != 8) {
            throw new IOException("Only 8-bit PNGs are supported, got bit depth " + bitDepth);
        }
        if (interlace != 0) {
            throw new IOException("Interlaced PNGs are not supported");
        }
        int channels = switch (colorType) {
            case 0 -> 1;
            case 2 -> 3;
            case 3 -> 1;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw new IOException("Unsupported PNG colour type " + colorType);
        };
        return new Header(width, height, bitDepth, colorType, channels);
    }

    private static byte[] inflate(byte[] compressed, Header header) throws IOException {
        int stride = header.width * header.channels;
        int expected = Math.multiplyExact(header.height, stride + 1);
        byte[] raw = new byte[expected];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            int produced = 0;
            while (produced < expected && !inflater.finished()) {
                int step = inflater.inflate(raw, produced, expected - produced);
                if (step == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                produced += step;
            }
            if (produced != expected) {
                throw new IOException("PNG data does not match its declared extents");
            }
            return raw;
        } catch (DataFormatException malformed) {
            throw new IOException("PNG data is not inflatable", malformed);
        } finally {
            inflater.end();
        }
    }

    private static PixelImage unfilter(byte[] raw, Header header, byte[] palette, byte[] paletteAlpha)
            throws IOException {
        int channels = header.channels;
        int stride = header.width * channels;
        byte[] previous = new byte[stride];
        byte[] current = new byte[stride];
        int[] argb = new int[Math.multiplyExact(header.width, header.height)];

        int at = 0;
        for (int y = 0; y < header.height; y++) {
            int filter = raw[at++] & 0xFF;
            System.arraycopy(raw, at, current, 0, stride);
            at += stride;
            applyFilter(filter, current, previous, channels);
            for (int x = 0; x < header.width; x++) {
                argb[y * header.width + x] =
                        toArgb(current, x * channels, header.colorType, palette, paletteAlpha);
            }
            byte[] swap = previous;
            previous = current;
            current = swap;
        }
        return new PixelImage(header.width, header.height, argb);
    }

    private static void applyFilter(int filter, byte[] row, byte[] previous, int channels)
            throws IOException {
        switch (filter) {
            case 0 -> { }
            case 1 -> {
                for (int index = channels; index < row.length; index++) {
                    row[index] += row[index - channels];
                }
            }
            case 2 -> {
                for (int index = 0; index < row.length; index++) {
                    row[index] += previous[index];
                }
            }
            case 3 -> {
                for (int index = 0; index < row.length; index++) {
                    int left = index >= channels ? row[index - channels] & 0xFF : 0;
                    row[index] += (byte) (((left + (previous[index] & 0xFF)) / 2) & 0xFF);
                }
            }
            case 4 -> {
                for (int index = 0; index < row.length; index++) {
                    int left = index >= channels ? row[index - channels] & 0xFF : 0;
                    int up = previous[index] & 0xFF;
                    int upLeft = index >= channels ? previous[index - channels] & 0xFF : 0;
                    row[index] += (byte) paeth(left, up, upLeft);
                }
            }
            default -> throw new IOException("Unsupported PNG row filter " + filter);
        }
    }

    private static int paeth(int left, int up, int upLeft) {
        int estimate = left + up - upLeft;
        int distLeft = Math.abs(estimate - left);
        int distUp = Math.abs(estimate - up);
        int distUpLeft = Math.abs(estimate - upLeft);
        if (distLeft <= distUp && distLeft <= distUpLeft) {
            return left;
        }
        return distUp <= distUpLeft ? up : upLeft;
    }

    private static int toArgb(byte[] row, int offset, int colorType, byte[] palette, byte[] paletteAlpha)
            throws IOException {
        return switch (colorType) {
            case 0 -> {
                int grey = row[offset] & 0xFF;
                yield 0xFF000000 | (grey << 16) | (grey << 8) | grey;
            }
            case 2 -> 0xFF000000
                    | ((row[offset] & 0xFF) << 16)
                    | ((row[offset + 1] & 0xFF) << 8)
                    | (row[offset + 2] & 0xFF);
            case 3 -> {
                int index = row[offset] & 0xFF;
                if (palette == null || index * 3 + 2 >= palette.length) {
                    throw new IOException("PNG palette index is outside PLTE");
                }
                int alpha = paletteAlpha != null && index < paletteAlpha.length
                        ? paletteAlpha[index] & 0xFF : 0xFF;
                yield (alpha << 24)
                        | ((palette[index * 3] & 0xFF) << 16)
                        | ((palette[index * 3 + 1] & 0xFF) << 8)
                        | (palette[index * 3 + 2] & 0xFF);
            }
            case 4 -> {
                int grey = row[offset] & 0xFF;
                yield ((row[offset + 1] & 0xFF) << 24) | (grey << 16) | (grey << 8) | grey;
            }
            default -> ((row[offset + 3] & 0xFF) << 24)
                    | ((row[offset] & 0xFF) << 16)
                    | ((row[offset + 1] & 0xFF) << 8)
                    | (row[offset + 2] & 0xFF);
        };
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] body) {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(body.length).array(), 0, 4);
        out.write(typeBytes, 0, typeBytes.length);
        out.write(body, 0, body.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(body);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array(), 0, 4);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
