package com.openggf.tools.modsdk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.TreeMap;

/** Deterministically writes the original one-screen level's Base64 parser assets. */
public final class SampleRomArtRemixAssetGenerator {
    private static final Path OUTPUT = Path.of(
            "src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod/level-source");
    private static final int WIDTH = 3;
    private static final int HEIGHT = 2;
    private static final int GRID_SIDE = 8;

    private SampleRomArtRemixAssetGenerator() { }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(OUTPUT);
        TreeMap<String, byte[]> assets = new TreeMap<>();
        assets.put("patterns.bin", patterns());
        assets.put("chunks.bin", chunks());
        assets.put("blocks.bin", blocks());
        assets.put("fg-map.bin", foregroundMap());
        assets.put("solid-heights.bin", solidHeights());
        assets.put("solid-widths.bin", solidWidths());
        assets.put("solid-angles.bin", solidAngles());
        assets.put("collision-primary.bin", collisions(false));
        assets.put("collision-secondary.bin", collisions(true));
        assets.put("palettes.bin", palettes());

        StringBuilder properties = new StringBuilder();
        for (var entry : assets.entrySet()) {
            properties.append(entry.getKey()).append('=')
                    .append(Base64.getEncoder().encodeToString(entry.getValue())).append('\n');
        }
        Files.writeString(OUTPUT.resolve("binary-assets.properties"), properties.toString(),
                StandardCharsets.US_ASCII);
    }

    private static byte[] patterns() throws IOException {
        return binary(out -> {
            out.writeBytes("GPTN");
            out.writeShort(1);
            out.writeShort(32);
            out.writeInt(2);
            writeFlatPattern(out, 0);
            writeFlatPattern(out, 2);
        });
    }

    private static void writeFlatPattern(DataOutputStream out, int pixel) throws IOException {
        int packed = ((pixel & 0xF) << 4) | (pixel & 0xF);
        for (int i = 0; i < 32; i++) out.writeByte(packed);
    }

    private static byte[] chunks() throws IOException {
        return binary(out -> {
            out.writeBytes("GCHK");
            out.writeShort(1);
            out.writeShort(8);
            out.writeInt(1);
            int word = (1 << 13) | 1;
            for (int i = 0; i < 4; i++) out.writeShort(word);
        });
    }

    private static byte[] blocks() throws IOException {
        return binary(out -> {
            out.writeBytes("GBLK");
            out.writeShort(1);
            out.writeByte(GRID_SIDE);
            out.writeByte(0);
            out.writeInt(2);
            for (int block = 0; block < 2; block++) {
                for (int i = 0; i < GRID_SIDE * GRID_SIDE; i++) out.writeShort(0);
            }
        });
    }

    private static byte[] foregroundMap() throws IOException {
        return binary(out -> {
            out.writeBytes("GMAP");
            out.writeShort(1);
            out.writeShort(WIDTH);
            out.writeShort(HEIGHT);
            out.writeShort(1);
            out.writeInt(WIDTH * HEIGHT);
            for (int i = 0; i < WIDTH * HEIGHT; i++) out.writeByte(1);
        });
    }

    private static byte[] solidHeights() throws IOException {
        return binary(out -> {
            out.writeBytes("GSHG");
            out.writeShort(1);
            out.writeShort(16);
            out.writeInt(1);
            for (int i = 0; i < 16; i++) out.writeByte(0);
        });
    }

    private static byte[] solidWidths() throws IOException {
        return binary(out -> {
            out.writeBytes("GSWD");
            out.writeShort(1);
            out.writeShort(16);
            out.writeInt(1);
            for (int i = 0; i < 16; i++) out.writeByte(0);
        });
    }

    private static byte[] solidAngles() throws IOException {
        return binary(out -> {
            out.writeBytes("GSAN");
            out.writeShort(1);
            out.writeShort(1);
            out.writeInt(1);
            out.writeByte(0);
        });
    }

    private static byte[] collisions(boolean secondary) throws IOException {
        return binary(out -> {
            out.writeBytes("GCOL");
            out.writeShort(1);
            out.writeByte(secondary ? 1 : 0);
            out.writeByte(2);
            out.writeInt(1);
            out.writeShort(0);
        });
    }

    private static byte[] palettes() throws IOException {
        return binary(out -> {
            out.writeBytes("GPAL");
            out.writeShort(1);
            out.writeShort(4);
            out.writeShort(16);
            out.writeShort(0);
            for (int line = 0; line < 4; line++) {
                for (int color = 0; color < 16; color++) {
                    int value = line == 1 && color == 2 ? (7 << 9) | (5 << 5) : 0;
                    out.writeShort(value);
                }
            }
        });
    }

    private static byte[] binary(IoWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
