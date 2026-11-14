package com.openggf.game.soniccd.special;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.Objects;

/**
 * The special stage's disc data, read from {@code SPSS__.BIN} and {@code SPMM__.MMD} through
 * {@link CdSpecialStageCatalogue}. Stage numbers are 0-based as {@code specStageID} holds them
 * (0-6 are the seven Time Stone stages, 7 the secret stage).
 */
public final class CdSpecialStageAssets {
    public static final int STAGE_COUNT = 8;

    private final byte[] sub;
    private final byte[] main;

    private CdSpecialStageAssets(byte[] sub, byte[] main) {
        this.sub = sub;
        this.main = main;
    }

    public static CdSpecialStageAssets load(CdDiscDataSource disc) throws IOException {
        Objects.requireNonNull(disc, "disc");
        return new CdSpecialStageAssets(disc.readFile(CdNativeFile.SPECSUB), disc.readFile(CdNativeFile.SPECMAIN));
    }

    private byte[] file(CdSpecialStageCatalogue.Block block) {
        return switch (block.file()) {
            case "SPSS__.BIN" -> sub;
            case "SPMM__.MMD" -> main;
            default -> throw new IllegalStateException(block.name() + " is not a file block");
        };
    }

    byte[] subProgram() { return sub; }

    byte[] raw(String name) {
        var block = CdSpecialStageCatalogue.block(name);
        return Arrays.copyOfRange(file(block), block.offset(), block.offset() + block.length());
    }

    byte[] kosinski(String name) throws IOException {
        var block = CdSpecialStageCatalogue.block(name);
        return KosinskiReader.decompress(Channels.newChannel(
                new ByteArrayInputStream(file(block), block.offset(), block.length())));
    }

    /** Nemesis-compressed art from the catalogue, decoded to 32-byte tiles. */
    byte[] nemesis(String name) throws IOException {
        var block = CdSpecialStageCatalogue.block(name);
        // The decoder reads ahead of the last code, so it is given the rest of the file.
        byte[] data = file(block);
        return com.openggf.data.compression.NemesisReader.decompress(Channels.newChannel(
                new ByteArrayInputStream(data, block.offset(), data.length - block.offset())));
    }

    /** One {@code DrawTilemaps} entry (scddisasm/src/Special Stage/Main.asm:3707-4152): data, VRAM destination and size in cells. */
    record Tilemap(int[] words, int vramAddress, int width, int height) { }

    /**
     * Tilemap {@code id} (1-based, as {@code DrawTilemaps} takes it) for {@code stage}. The entry's
     * pointer addresses the main program's RAM image: SPMM__.MMD loads at its header's address less
     * the $100-byte header, and the stage's data block ({@code SpecStageData}) is copied right after
     * the program image ({@code SpecStgDataCopy}, _Global Variables.inc:8). Each map starts with a
     * word that is 0 for raw words and non-zero for Kosinski data ({@code tst.w (a0)+}).
     */
    Tilemap tilemap(int stage, int id) throws IOException {
        var table = CdSpecialStageCatalogue.block("Tilemaps");
        if (id < 1 || id * 12 > table.length()) throw new IllegalArgumentException("Tilemap " + id);
        int at = table.offset() + (id - 1) * 12;
        long pointer = long32(main, at);
        long command = long32(main, at + 4);
        int width = word(main, at + 8) + 1, height = word(main, at + 10) + 1;
        long origin = long32(main, 2) - 0x100;
        byte[] source;
        int offset;
        if (pointer - origin < main.length) {
            source = main;
            offset = (int) (pointer - origin);
        } else {
            var index = CdSpecialStageCatalogue.block("SpecStageData");
            source = sub;
            offset = (int) (long32(sub, index.offset() + stage * 6) - 0x10000 + (pointer - origin - main.length));
        }
        boolean compressed = word(source, offset) != 0;
        byte[] data = compressed ? KosinskiReader.decompress(Channels.newChannel(
                new ByteArrayInputStream(source, offset + 2, source.length - offset - 2)))
                : Arrays.copyOfRange(source, offset + 2, offset + 2 + width * height * 2);
        int[] words = new int[width * height];
        for (int i = 0; i < words.length; i++) words[i] = word(data, i * 2);
        int vram = (int) (((command >>> 16) & 0x3FFF) | ((command & 3) << 14));
        return new Tilemap(words, vram, width, height);
    }

    /** A catalogued dc table of words. */
    short[] wordTable(String name) { return words(raw(name)); }

    private static long long32(byte[] d, int at) {
        return ((d[at] & 0xFFL) << 24) | ((d[at + 1] & 0xFFL) << 16) | ((d[at + 2] & 0xFFL) << 8) | (d[at + 3] & 0xFFL);
    }

    private static int word(byte[] d, int at) { return ((d[at] & 0xFF) << 8) | (d[at + 1] & 0xFF); }

    /** {@code SineTable} (scddisasm/src/Special Stage/Sub.asm:2657-2673): the 128-entry quarter-wave table. */
    public short[] sineTable() {
        return words(raw("SineTable"));
    }

    /**
     * The floor of {@code stage}: {@code LoadStageMap}/{@code LoadStamps} (scddisasm/src/Special Stage/Sub.asm:406-493),
     * {@code GetStampTypes} (scddisasm/src/Special Stage/Sub.asm:1660-1677) and {@code InitStampAnim} (scddisasm/src/Special Stage/Sub.asm:1183-1234).
     */
    public CdStampFloor floor(int stage) throws IOException {
        requireStage(stage);
        int n = stage + 1;
        byte[] space = new byte[CdStampFloor.stampSpaceBytes()];
        if (n == 3) {
            place(space, kosinski("Stamps_SS3_1"), 4);
            place(space, kosinski("Stamps_SS3_2"), 0x200);
        } else {
            place(space, kosinski("Stamps_SS" + n), 4);
        }
        byte[] mapBytes = kosinski("StampMap_SS" + n);
        if (mapBytes.length != CdStampFloor.MAP_STAMPS * CdStampFloor.MAP_STAMPS * 2) {
            throw new IOException("Stamp map " + n + " is not 128 x 128");
        }
        return new CdStampFloor(space, words(mapBytes), stampTypeWindow("StampTypes_SS" + n),
                animation(raw("HazardAnim_SS" + n), CdSpecialStageCatalogue.scalar("HazardAnimCount_SS" + n), 4),
                animation(raw("FanAnim_SS" + n), CdSpecialStageCatalogue.scalar("FanAnimCount_SS" + n), 3));
    }

    /** Animation scripts of a 3D-section object located by the catalogue (e.g. {@code MapSpr_Sonic}). */
    CdSpecialAnimationScripts animationScripts(String table) {
        var block = CdSpecialStageCatalogue.block(table);
        return new CdSpecialAnimationScripts(file(block), block.offset());
    }

    /** {@code ObjSonic_GetStartPos .StartPos} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:545-553): X, Y, yaw. */
    public int[] startPosition(int stage) {
        requireStage(stage);
        short[] table = words(raw("SonicStartPos"));
        return new int[] {table[stage * 3] & 0xFFFF, table[stage * 3 + 1] & 0xFFFF, table[stage * 3 + 2] & 0xFFFF};
    }

    /** {@code ObjSonic_Animate .WalkAnims} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1365-1370): five tilt rows of four. */
    public byte[] walkAnimations() {
        return raw("SonicWalkAnims");
    }

    /**
     * Demo input for the Stage 1 or Stage 6 demo (Main.asm:4776-4789), decompressed as
     * {@code Start} does (Main.asm:152-160): one controller byte per read.
     */
    public byte[] demo(int stage) throws IOException {
        return switch (stage) {
            case 0 -> kosinski("Stage1Demo");
            case 5 -> kosinski("Stage6Demo");
            default -> throw new IllegalArgumentException("No demo for stage " + (stage + 1));
        };
    }

    /**
     * The stage's stamp-type table as {@code ObjSonic_GetStamps} indexes it: (entry & $7FF) / 4
     * reaches $1FF, past every table (the empty cell $FFFF does), where the program's following
     * bytes are read. FixBugs = 0: the index is not bounded, so the window covers all $200 bytes.
     */
    private byte[] stampTypeWindow(String name) {
        var block = CdSpecialStageCatalogue.block(name);
        return Arrays.copyOfRange(file(block), block.offset(), block.offset() + 0x200);
    }

    private static void place(byte[] space, byte[] stamps, int firstStamp) {
        int at = CdStampFloor.stampSpaceOffset(firstStamp);
        if (at + stamps.length > space.length) throw new IllegalStateException("Stamps overrun stamp space");
        System.arraycopy(stamps, 0, space, at, stamps.length);
    }

    private static int[][] animation(byte[] data, int count, int frames) {
        int stride = 2 + frames * 2;
        if (data.length < count * stride) throw new IllegalStateException("Short stamp animation table");
        int[][] entries = new int[count][frames + 1];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j <= frames; j++) {
                int at = i * stride + j * 2;
                entries[i][j] = ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
            }
        }
        return entries;
    }

    static short[] words(byte[] data) {
        short[] out = new short[data.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = (short) (((data[2 * i] & 0xFF) << 8) | (data[2 * i + 1] & 0xFF));
        return out;
    }

    private static void requireStage(int stage) {
        if (stage < 0 || stage >= STAGE_COUNT) throw new IllegalArgumentException("Special stage " + stage + " outside 0-7");
    }
}
