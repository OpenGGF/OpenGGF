package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Generated metadata and byte patterns only. No commercial payloads. */
public final class SyntheticProgramDisc {
    private static final int BLOCK = 2048;
    private SyntheticProgramDisc() { }

    public static Path write(Path directory, CdReleaseVariant variant) throws IOException {
        Files.createDirectories(directory);
        byte[] image = new byte[512 * BLOCK];
        putText(image, 0, "SEGADISCSYSTEM  ");
        putText(image, 16, variant.discHeaderVolumeId());
        putText(image, 0x50, variant.sourceBuildDate());
        putText(image, 0x180, variant.productId());
        putText(image, 0x1f0, variant.headerMarket());
        int pvd = 16 * BLOCK;
        descriptor(image, 16, 1);
        descriptor(image, 17, 255);
        Arrays.fill(image, pvd + 40, pvd + 72, (byte) ' ');
        putText(image, pvd + 40, "SYNTHETIC_PROGRAMS");
        both32(image, pvd + 80, 512);
        both16(image, pvd + 120, 1);
        both16(image, pvd + 124, 1);
        both16(image, pvd + 128, BLOCK);
        both32(image, pvd + 132, 10);
        ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN).putInt(pvd + 140, 18);
        ByteBuffer.wrap(image).putInt(pvd + 148, 19);
        image[pvd + 881] = 1;
        byte[] root = record(new byte[] {0}, 20, 4 * BLOCK, true);
        System.arraycopy(root, 0, image, pvd + 156, root.length);
        int position = 20 * BLOCK;
        for (byte[] record : new byte[][] {root, record(new byte[] {1}, 20, 4 * BLOCK, true)}) {
            System.arraycopy(record, 0, image, position, record.length);
            position += record.length;
        }
        int block = 24;
        for (CdNativeFile file : CdNativeFile.values()) {
            int size = file.kind() == CdNativeFile.Kind.VIDEO_STREAM ? 5000 : 3000;
            byte[] record = record(file.isoName().getBytes(StandardCharsets.US_ASCII), block, size, false);
            if (position % BLOCK + record.length > BLOCK) { position = (position / BLOCK + 1) * BLOCK; }
            System.arraycopy(record, 0, image, position, record.length);
            position += record.length;
            for (int i = 0; i < size; i++) { image[block * BLOCK + i] = (byte) (file.id() * 17 + i * 7); }
            if (file.isoName().contains(".MMD;")) {
                byte[] header = new byte[256];
                ByteBuffer.wrap(header).putInt(8, 0x200100);
                System.arraycopy(header, 0, image, block * BLOCK, header.length);
            }
            block += (size + BLOCK - 1) / BLOCK;
        }
        Files.write(directory.resolve("data.bin"), image);
        StringBuilder cue = new StringBuilder("FILE \"data.bin\" BINARY\n TRACK 01 MODE1/2048\n INDEX 01 00:00:00\n");
        for (int number = 2; number <= 35; number++) {
            String name = "audio" + number + ".bin";
            byte[] audio = new byte[2352 * 3];
            Arrays.fill(audio, (byte) number);
            Files.write(directory.resolve(name), audio);
            cue.append("FILE \"").append(name).append("\" BINARY\n TRACK ")
                    .append(String.format(java.util.Locale.ROOT, "%02d", number))
                    .append(" AUDIO\n INDEX 00 00:00:00\n INDEX 01 00:00:01\n");
        }
        return Files.writeString(directory.resolve("disc.cue"), cue);
    }

    private static void descriptor(byte[] image, int block, int type) {
        int offset = block * BLOCK;
        image[offset] = (byte) type;
        putText(image, offset + 1, "CD001");
        image[offset + 6] = 1;
    }
    private static void putText(byte[] bytes, int offset, String text) {
        byte[] encoded = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, bytes, offset, encoded.length);
    }
    private static byte[] record(byte[] name, int block, int size, boolean directory) {
        byte[] bytes = new byte[33 + name.length + (name.length % 2 == 0 ? 1 : 0)];
        bytes[0] = (byte) bytes.length;
        both32(bytes, 2, block);
        both32(bytes, 10, size);
        bytes[25] = (byte) (directory ? 2 : 0);
        both16(bytes, 28, 1);
        bytes[32] = (byte) name.length;
        System.arraycopy(name, 0, bytes, 33, name.length);
        return bytes;
    }
    private static void both32(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
        ByteBuffer.wrap(bytes).putInt(offset + 4, value);
    }
    private static void both16(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
        ByteBuffer.wrap(bytes).putShort(offset + 2, (short) value);
    }
}
