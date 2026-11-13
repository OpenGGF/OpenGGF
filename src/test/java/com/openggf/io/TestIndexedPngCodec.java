package com.openggf.io;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class TestIndexedPngCodec {
    @Test void preservesPackedIndicesEvenWithDuplicatePaletteColours() throws Exception {
        var image = PngCodec.decodeIndexed(png(3, 1, 4, new byte[]{0, 0x12, 0}), 256);
        assertArrayEquals(new byte[]{1, 2, 0}, image.indices());
        assertEquals(image.rgb()[1], image.rgb()[2]);
        assertEquals(3, PngCodec.decode(png(3, 1, 4, new byte[]{0, 0x12, 0})).getWidth());
    }
    @Test void rejectsOversizedHeadersBeforeInflatingAndRejectsOutOfBudgetIndices() throws Exception {
        assertThrows(IOException.class, () -> PngCodec.decodeIndexed(png(257, 1, 8, new byte[]{0}), 256));
        assertThrows(IOException.class, () -> PngCodec.decodeIndexed(png(1, 1, 8, new byte[]{0, 16}), 256));
        assertThrows(IOException.class, () -> PngCodec.decodeIndexed(png(1, 1, 4, new byte[]{0, 0x30}), 256));
    }
    private static byte[] png(int width, int height, int depth, byte[] rows) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeLong(0x89504e470d0a1a0aL);
        var headerBytes = new ByteArrayOutputStream();
        var header = new DataOutputStream(headerBytes);
        header.writeInt(width); header.writeInt(height);
        header.write(new byte[]{(byte)depth, 3, 0, 0, 0});
        chunk(out, "IHDR", headerBytes.toByteArray());
        chunk(out, "PLTE", new byte[]{0,0,0, 12,34,56, 12,34,56});
        var data = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(data)) { deflater.write(rows); }
        chunk(out, "IDAT", data.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return bytes.toByteArray();
    }
    private static void chunk(DataOutputStream out, String name, byte[] data) throws IOException {
        byte[] type = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeInt(data.length); out.write(type); out.write(data);
        var crc = new java.util.zip.CRC32(); crc.update(type); crc.update(data);
        out.writeInt((int)crc.getValue());
    }
}
