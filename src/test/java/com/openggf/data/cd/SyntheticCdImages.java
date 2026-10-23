package com.openggf.data.cd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class SyntheticCdImages {
    static final int BLOCK = 2048;

    private SyntheticCdImages() {
    }

    static Path cue(Path directory, String text) throws IOException {
        return Files.writeString(directory.resolve("disc.cue"), text);
    }

    static byte[] raw(byte[] cooked) {
        if (cooked.length % BLOCK != 0) {
            throw new IllegalArgumentException();
        }
        byte[] raw = new byte[cooked.length / BLOCK * 2352];
        for (int sector = 0; sector < cooked.length / BLOCK; sector++) {
            int start = sector * 2352;
            Arrays.fill(raw, start + 1, start + 11, (byte) 0xff);
            raw[start + 15] = 1;
            System.arraycopy(cooked, sector * BLOCK, raw, start + 16, BLOCK);
            Arrays.fill(raw, start + 2064, start + 2352, (byte) 0x5a);
        }
        return raw;
    }

    static Path writeDisc(Path directory, byte[] cooked, boolean raw) throws IOException {
        Files.write(directory.resolve("data.bin"), raw ? raw(cooked) : cooked);
        return cue(directory, "FILE \"data.bin\" BINARY\n  TRACK 01 MODE1/"
                + (raw ? "2352" : "2048") + "\n    INDEX 01 00:00:00\n");
    }

    static byte[] iso() {
        byte[] bytes = new byte[32 * BLOCK];
        int pvd = 16 * BLOCK;
        descriptor(bytes, 16, 1);
        descriptor(bytes, 17, 255);
        byte[] id = "SYNTHETIC_DISC".getBytes(StandardCharsets.US_ASCII);
        Arrays.fill(bytes, pvd + 40, pvd + 72, (byte) ' ');
        System.arraycopy(id, 0, bytes, pvd + 40, id.length);
        both32(bytes, pvd + 80, 32);
        both16(bytes, pvd + 120, 1);
        both16(bytes, pvd + 124, 1);
        both16(bytes, pvd + 128, BLOCK);
        both32(bytes, pvd + 132, 10);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(pvd + 140, 18);
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(pvd + 148, 19);
        bytes[pvd + 881] = 1;
        byte[] root = record(new byte[] {0}, 20, 2 * BLOCK, true);
        System.arraycopy(root, 0, bytes, pvd + 156, root.length);
        directory(bytes, 20, root, record(new byte[] {1}, 20, 2 * BLOCK, true),
                record("CROSS.BIN;1", 24, 3000, false), record("VERS.TXT;1", 26, 3, false),
                record("VERS.TXT;2", 27, 3, false), record("SUB", 22, BLOCK, true),
                record("EMPTY.DAT;1", 32, 0, false));
        directory(bytes, 21, record("LATER.TXT;1", 28, 5, false));
        directory(bytes, 22, record(new byte[] {0}, 22, BLOCK, true),
                record(new byte[] {1}, 20, 2 * BLOCK, true), record("NESTED.BIN;1", 29, 4, false));
        for (int i = 0; i < 3000; i++) {
            bytes[24 * BLOCK + i] = (byte) (i * 7 + 3);
        }
        System.arraycopy(new byte[] {1, 2, 3}, 0, bytes, 26 * BLOCK, 3);
        System.arraycopy(new byte[] {4, 5, 6}, 0, bytes, 27 * BLOCK, 3);
        System.arraycopy("later".getBytes(StandardCharsets.US_ASCII), 0, bytes, 28 * BLOCK, 5);
        System.arraycopy(new byte[] {8, 7, 6, 5}, 0, bytes, 29 * BLOCK, 4);
        return bytes;
    }

    static void descriptor(byte[] bytes, int sector, int type) {
        int start = sector * BLOCK;
        bytes[start] = (byte) type;
        System.arraycopy("CD001".getBytes(StandardCharsets.US_ASCII), 0, bytes, start + 1, 5);
        bytes[start + 6] = 1;
    }

    static byte[] record(String identifier, long extent, long size, boolean directory) {
        return record(identifier.getBytes(StandardCharsets.US_ASCII), extent, size, directory);
    }

    static byte[] record(byte[] identifier, long extent, long size, boolean directory) {
        byte[] record = new byte[33 + identifier.length + (identifier.length % 2 == 0 ? 1 : 0)];
        record[0] = (byte) record.length;
        both32(record, 2, extent);
        both32(record, 10, size);
        record[25] = (byte) (directory ? 2 : 0);
        both16(record, 28, 1);
        record[32] = (byte) identifier.length;
        System.arraycopy(identifier, 0, record, 33, identifier.length);
        return record;
    }

    static void directory(byte[] bytes, int sector, byte[]... records) {
        int offset = sector * BLOCK;
        for (byte[] record : records) {
            System.arraycopy(record, 0, bytes, offset, record.length);
            offset += record.length;
        }
    }

    static void both16(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putShort(offset + 2, (short) value);
    }

    static void both32(byte[] bytes, int offset, long value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, (int) value);
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(offset + 4, (int) value);
    }
}
