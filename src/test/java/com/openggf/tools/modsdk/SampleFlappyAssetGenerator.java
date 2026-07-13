package com.openggf.tools.modsdk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.TreeMap;

/**
 * Deterministic offline generator for the {@code sample-flappy} gallery mod's zone-agnostic
 * level source: the binary asset inventory (base64-embedded in {@code binary-assets.properties},
 * matching the {@code sample-mod-src} template) plus the {@code objects[]} JSON fragment pasted
 * into the hand-written {@code level.json}.
 *
 * <p>This is a plain {@code main()} utility, run manually and checked in as data (not executed by
 * the build). It contains no timestamps, randomness, or environment-dependent input, so re-running
 * it always reproduces byte-identical output.
 *
 * <p>Mirrors {@code FullLevelExporter}'s exact binary writers (pattern/chunk/block/map/solid/
 * collision/palette layouts) so the generated directory round-trips through
 * {@code ModLevelDefinitionParser} / {@code ggfmod convert level --from-export} exactly like the
 * {@code sample-mod-src} template.
 */
public final class SampleFlappyAssetGenerator {
    private static final Path OUTPUT_DIR = Path.of(
            "src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source");

    /** Map is 80x2 blocks of {@code blockGridSide=8} chunks of 16px each: 10240x256 level pixels. */
    private static final int MAP_WIDTH_BLOCKS = 80;
    private static final int MAP_HEIGHT_BLOCKS = 2;
    private static final int BLOCK_GRID_SIDE = 8;

    /** Palette line index carrying the flat sky-fill and dimmer-band colors used by the chunks. */
    private static final int SKY_PALETTE_LINE = 1;

    /** Pattern indices: 0 = blank (unused), 1 = flat sky fill, 2 = dimmer sky band. */
    private static final int PATTERN_BLANK = 0;
    private static final int PATTERN_SKY = 1;
    private static final int PATTERN_SKY_BAND = 2;

    private SampleFlappyAssetGenerator() {}

    public static void main(String[] args) throws IOException {
        Files.createDirectories(OUTPUT_DIR);

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

        // Written by hand (not java.util.Properties#store) so the output has no timestamp comment
        // and is byte-for-byte reproducible across runs/JDKs. Keys are sorted (TreeMap); base64
        // values never contain '=' except trailing padding, which Properties#load handles fine
        // after the first unescaped separator.
        StringBuilder out = new StringBuilder();
        for (var entry : assets.entrySet()) {
            out.append(entry.getKey()).append('=')
                    .append(Base64.getEncoder().encodeToString(entry.getValue())).append('\n');
        }
        Files.writeString(OUTPUT_DIR.resolve("binary-assets.properties"), out.toString(),
                StandardCharsets.US_ASCII);

        System.out.println(objectsJsonFragment());
    }

    // ---- Binary asset writers (mirror FullLevelExporter's exact layouts) ----

    private static byte[] patterns() throws IOException {
        return binary(out -> {
            out.writeBytes("GPTN");
            out.writeShort(1);
            out.writeShort(32);
            out.writeInt(3);
            writeFlatPattern(out, PATTERN_BLANK); // index 0: blank
            writeFlatPattern(out, 2); // index 1: sky fill (color 2 in palette line 1)
            writeFlatPattern(out, 4); // index 2: dimmer sky band (color 4 in palette line 1)
        });
    }

    private static void writeFlatPattern(DataOutputStream out, int pixel) throws IOException {
        int nibble = pixel & 0xF;
        byte packed = (byte) ((nibble << 4) | nibble);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x += 2) {
                out.writeByte(packed);
            }
        }
    }

    /** Chunk 0: flat sky (all four quadrants = pattern 1). Chunk 1: dimmer band (pattern 2). */
    private static byte[] chunks() throws IOException {
        return binary(out -> {
            out.writeBytes("GCHK");
            out.writeShort(1);
            out.writeShort(8);
            out.writeInt(2);
            writeFlatChunk(out, PATTERN_SKY);
            writeFlatChunk(out, PATTERN_SKY_BAND);
        });
    }

    private static void writeFlatChunk(DataOutputStream out, int patternIndex) throws IOException {
        int word = (SKY_PALETTE_LINE << 13) | (patternIndex & 0x7FF);
        for (int i = 0; i < 4; i++) out.writeShort(word);
    }

    /**
     * Two blocks. Index 0 is reserved-empty filler: {@code ModLevel.decodeBlocks} force-replaces
     * block 0 with an empty {@code Block} at gameplay load, so visible content must never live
     * there (matches the sample-mod-src / sample-standalone-src precedent, whose fg-maps keep
     * real content at index 1). Index 1 holds the sky layout: an 8x8 chunk grid, all flat-sky
     * chunk 0 except grid row 3 (48-64px band), which uses the dimmer chunk 1 -- a simple
     * horizontal band across the block.
     */
    private static byte[] blocks() throws IOException {
        return binary(out -> {
            out.writeBytes("GBLK");
            out.writeShort(1);
            out.writeByte(BLOCK_GRID_SIDE);
            out.writeByte(0);
            out.writeInt(2);
            // Block 0: reserved-empty (contents ignored; force-emptied at gameplay load).
            for (int i = 0; i < BLOCK_GRID_SIDE * BLOCK_GRID_SIDE; i++) out.writeShort(0);
            // Block 1: sky with a dimmer horizontal band on grid row 3.
            for (int row = 0; row < BLOCK_GRID_SIDE; row++) {
                int chunkIndex = row == 3 ? 1 : 0;
                for (int col = 0; col < BLOCK_GRID_SIDE; col++) out.writeShort(chunkIndex);
            }
        });
    }

    /** 80x2 map cells, all referencing content block 1 (block 0 is reserved-empty at load). */
    private static byte[] foregroundMap() throws IOException {
        return binary(out -> {
            out.writeBytes("GMAP");
            out.writeShort(1);
            out.writeShort(MAP_WIDTH_BLOCKS);
            out.writeShort(MAP_HEIGHT_BLOCKS);
            out.writeShort(1);
            out.writeInt(MAP_WIDTH_BLOCKS * MAP_HEIGHT_BLOCKS);
            for (int i = 0; i < MAP_WIDTH_BLOCKS * MAP_HEIGHT_BLOCKS; i++) out.writeByte(1);
        });
    }

    /** Single no-collision solid profile: the level is terrain-free (the controller owns contact). */
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

    /** One collision path (0=primary, 1=secondary): 2 chunks, both -> the sole (no-collision) profile 0. */
    private static byte[] collisions(boolean secondary) throws IOException {
        return binary(out -> {
            out.writeBytes("GCOL");
            out.writeShort(1);
            out.writeByte(secondary ? 1 : 0);
            out.writeByte(2);
            out.writeInt(2);
            out.writeShort(0);
            out.writeShort(0);
        });
    }

    /**
     * GPAL v1, 4 lines x 16 colors. Line 0 is reserved for the player/Tails palette (loaded by the
     * player art path, not by zone chunks) and is left black. Lines 1-3 hold this zone's original
     * sky blues, pipe greens, and HUD-safe white.
     */
    private static byte[] palettes() throws IOException {
        return binary(out -> {
            out.writeBytes("GPAL");
            out.writeShort(1);
            out.writeShort(4);
            out.writeShort(16);
            out.writeShort(0);
            writePaletteLine(out, new int[][] {
                    {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
                    {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
                    {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
                    {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
            }); // line 0: player/Tails palette (owned elsewhere)
            writePaletteLine(out, new int[][] {
                    {0, 0, 0}, {0, 4, 7}, {0, 5, 7}, {1, 5, 7},
                    {2, 6, 7}, {3, 6, 7}, {4, 7, 7}, {5, 7, 7},
                    {6, 7, 7}, {7, 7, 7}, {0, 2, 4}, {0, 3, 5},
                    {1, 4, 6}, {2, 5, 6}, {3, 5, 6}, {4, 6, 6},
            }); // line 1: sky blues (flat sky fill = index 2, dimmer band = index 4)
            writePaletteLine(out, new int[][] {
                    {0, 0, 0}, {0, 3, 0}, {0, 4, 0}, {0, 5, 0},
                    {0, 6, 0}, {0, 7, 0}, {1, 7, 1}, {2, 7, 2},
                    {0, 2, 0}, {0, 4, 1}, {1, 5, 1}, {1, 6, 1},
                    {2, 6, 2}, {3, 7, 3}, {4, 7, 4}, {5, 7, 5},
            }); // line 2: pipe greens
            writePaletteLine(out, new int[][] {
                    {0, 0, 0}, {7, 7, 7}, {6, 6, 6}, {5, 5, 5},
                    {4, 4, 4}, {3, 3, 3}, {2, 2, 2}, {1, 1, 1},
                    {7, 7, 6}, {7, 6, 5}, {6, 5, 4}, {5, 4, 3},
                    {7, 7, 7}, {6, 6, 6}, {5, 5, 5}, {4, 4, 4},
            }); // line 3: HUD-safe white/greys
        });
    }

    private static void writePaletteLine(DataOutputStream out, int[][] colors) throws IOException {
        for (int[] c : colors) {
            int r = c[0], g = c[1], b = c[2];
            out.writeShort((b << 9) | (g << 5) | (r << 1));
        }
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

    // ---- objects[] JSON fragment ----

    /**
     * One controller at (128,128) plus a pipe every 224px from x=512 through x&lt;=9700
     * (last pipe at x=9696), subtype cycling 0-4 by index. All entries use renderFlags=0 and
     * respawnTracked=false, so rawYWord == y.
     */
    private static String objectsJsonFragment() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append("{\"placementId\":1,\"x\":128,\"y\":128,\"objectKey\":\"sample-flappy:controller\",")
                .append("\"subtype\":0,\"renderFlags\":0,\"respawnTracked\":false,\"rawYWord\":128}");
        int placementId = 2;
        int index = 0;
        for (int x = 512; x <= 9700; x += 224) {
            int subtype = index % 5;
            int y = 128;
            sb.append(",{\"placementId\":").append(placementId).append(",\"x\":").append(x)
                    .append(",\"y\":").append(y).append(",\"objectKey\":\"sample-flappy:pipe\",")
                    .append("\"subtype\":").append(subtype)
                    .append(",\"renderFlags\":0,\"respawnTracked\":false,\"rawYWord\":").append(y)
                    .append("}");
            placementId++;
            index++;
        }
        sb.append("]");
        return sb.toString();
    }
}
