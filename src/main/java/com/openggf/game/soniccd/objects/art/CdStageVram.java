package com.openggf.game.soniccd.objects.art;

import com.openggf.data.compression.NemesisReader;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Pattern;
import com.openggf.level.resources.PlcParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Arrays;

/**
 * The stage's object tiles, indexed by VRAM tile number, as the level's pattern load cues
 * leave them. {@code LevelStart} decodes the level PLC and PLC 1 (the standard object art)
 * with {@code LoadPLCImm} before the first frame (Level/Main.asm:156-166;
 * Functions (General).asm:794); later cues go through {@code LoadPLC}/{@code ProcessPLCs}
 * (Functions (General).asm:588-695) and their owners load them here by ID. The PLC format
 * is the shared one ({@link PlcParser}); entry addresses are main-CPU pointers into the
 * stage file. The queued decode budget is not modelled: a cue's tiles are present once
 * it is requested.
 */
public final class CdStageVram {
    /** 64 KB of VRAM in 32-byte tiles. */
    public static final int TILE_COUNT = 0x800;

    private final Pattern[] tiles = new Pattern[TILE_COUNT];

    public CdStageVram() {
        for (int i = 0; i < TILE_COUNT; i++) tiles[i] = new Pattern();
    }

    /** Decodes every Nemesis entry of PLC {@code plcId} into its VRAM tiles. */
    public void loadPlc(CdStageImage image, int plcLists, int plcId) throws IOException {
        var definition = PlcParser.parse(image.reader(), plcLists, plcId);
        for (var entry : definition.entries()) {
            byte[] data = NemesisReader.decompress(Channels.newChannel(
                    new ByteArrayInputStream(image.tail(image.offsetOf(entry.romAddr())))));
            write(entry.tileIndex(), data);
        }
    }

    /** Writes uncompressed tile bytes at a VRAM tile index (DMA or VDP data-port writes). */
    public void write(int tileIndex, byte[] data) {
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        for (int t = 0; t < count && tileIndex + t < TILE_COUNT; t++) {
            tiles[tileIndex + t].fromSegaFormat(Arrays.copyOfRange(data, t * Pattern.PATTERN_SIZE_IN_ROM,
                    (t + 1) * Pattern.PATTERN_SIZE_IN_ROM));
        }
    }

    /**
     * Whether the stage's PLCLists index has an entry {@code plcId}: the index is a run of word
     * offsets ending where the first list begins (LoadPLC: add.w d0,d0; move.w (a1,d0.w),d0,
     * Functions (General).asm:588-593), so it holds {@code firstOffset / 2} lists.
     */
    public static boolean hasPlc(CdStageImage image, int plcLists, int plcId) {
        return plcId >= 0 && plcId * 2 < image.u16(plcLists);
    }

    /** An independent copy, for sheets drawn from a later VRAM state. */
    public CdStageVram copy() {
        CdStageVram out = new CdStageVram();
        for (int i = 0; i < TILE_COUNT; i++) out.tiles[i].copyFrom(tiles[i]);
        return out;
    }

    public Pattern tile(int index) {
        return tiles[index & (TILE_COUNT - 1)];
    }

    /** Copies {@code count} tiles from {@code base}; a sheet owns its copy. */
    public Pattern[] window(int base, int count) {
        Pattern[] out = new Pattern[count];
        for (int i = 0; i < count; i++) {
            out[i] = new Pattern();
            out[i].copyFrom(tile(base + i));
        }
        return out;
    }
}
