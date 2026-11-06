package com.openggf.game.sonic2.specialstage;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.game.GameServices;

import com.openggf.data.Rom;
import com.openggf.level.Palette;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Loads Special Stage palette data from ROM for Sonic 2.
 *
 * Palette structure (from s2disasm):
 * - Pal_SS (Main): 96 bytes loaded to palette lines 0, 1, 2
 *   - Line 0: Background/sky colors
 *   - Line 1: Track/ground colors
 *   - Line 2: Object colors (rings, bombs, shadows)
 * - Pal_SS1-7: 32 bytes each loaded to palette line 3
 *   - Line 3: Per-stage varying colors
 *
 * Genesis color format: ----BBB0GGG0RRR0 (big-endian 16-bit)
 * - Bits 1-3: Red (0-7)
 * - Bits 5-7: Green (0-7)
 * - Bits 9-11: Blue (0-7)
 */
public class Sonic2SpecialStagePalette {
    private static final Logger LOGGER = Logger.getLogger(Sonic2SpecialStagePalette.class.getName());


    /**
     * Creates a set of 4 palettes for the specified special stage by loading from ROM.
     *
     * @param stageIndex Stage index (0-6)
     * @return Array of 4 Palette objects
     */
    public static Palette[] createPalettes(int stageIndex) {
        try {
            return createPalettes(GameServices.rom().getRom(), stageIndex);
        } catch (IOException e) {
            LOGGER.warning("Failed to load palettes from ROM, using fallback: " + e.getMessage());
            return createFallbackPalettes();
        }
    }

    /** Session-ROM path: no process-global cache or substitute palette. */
    static Palette[] createPalettes(Rom rom, int stageIndex) throws IOException {
        int stage = Math.max(0, Math.min(stageIndex, Sonic2SpecialStageConstants.SPECIAL_STAGE_COUNT - 1));
        byte[] main = readExact(rom, Sonic2SpecialStageConstants.PALETTE_MAIN_OFFSET,
                Sonic2SpecialStageConstants.PALETTE_MAIN_SIZE);
        byte[] stageColours = readExact(rom, Sonic2SpecialStageConstants.PALETTE_STAGE_OFFSETS[stage],
                Sonic2SpecialStageConstants.PALETTE_STAGE_SIZE);
        Palette[] result = new Palette[4];
        for (int line = 0; line < result.length; line++) {
            result[line] = new Palette();
            loadPaletteLineFromBytes(result[line], line == 3 ? stageColours : main,
                    line == 3 ? 0 : line * 32);
        }
        return result;
    }

    static int[] getEmeraldColors(Rom rom, int stageIndex) throws IOException {
        int stage = Math.max(0, Math.min(stageIndex, Sonic2SpecialStageConstants.SPECIAL_STAGE_COUNT - 1));
        byte[] data = readExact(rom, Sonic2SpecialStageConstants.PALETTE_EMERALD_OFFSET,
                Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE);
        int[] result = new int[3];
        for (int color = 0; color < result.length; color++) {
            int offset = stage * 6 + color * 2;
            result[color] = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        }
        return result;
    }

    private static byte[] readExact(Rom rom, long offset, int size) throws IOException {
        byte[] result = rom.readBytes(address(rom, offset), size);
        if (result == null || result.length != size) {
            throw new IOException("Incomplete special-stage palette at ROM $" + Long.toHexString(offset));
        }
        return result;
    }

    /**
     * Loads palette data from ROM and creates palette objects.
     */


    /**
     * Loads cached palette data from ROM if not already loaded.
     */


    /**
     * Loads 16 colors from raw ROM bytes into a Palette object.
     * Each color is a big-endian 16-bit Genesis color.
     *
     * @param palette The palette to populate
     * @param data Raw palette bytes (32 bytes = 16 colors)
     * @param offset Starting offset in the data array
     */
    private static void loadPaletteLineFromBytes(Palette palette, byte[] data, int offset) {
        for (int colorIndex = 0; colorIndex < 16; colorIndex++) {
            int byteOffset = offset + (colorIndex * 2);
            if (byteOffset + 1 < data.length) {
                // Read big-endian 16-bit Genesis color
                int genesisColor = ((data[byteOffset] & 0xFF) << 8) | (data[byteOffset + 1] & 0xFF);
                palette.setColor(colorIndex, genesisColorToRgb(genesisColor));
            }
        }
    }

    /**
     * Converts a Genesis 9-bit color (----BBB0GGG0RRR0 format) to RGB.
     * Preserve the shared canonical encoding of each native three-bit channel.
     */
    private static Palette.Color genesisColorToRgb(int genesis) {
        // Native-intensity palette views must retain the same canonical input
        // as ordinary CRAM lines, before profile/output conversion. n*36 loses
        // that encoding (white252 instead of255; level2=72 instead of73).
        Palette.Color color = new Palette.Color();
        color.fromSegaFormat(genesis);
        return color;
    }

    /**
     * Creates a Palette.Color from a 9-bit Genesis color value.
     */
    public static Palette.Color genesisColorToPaletteColor(int genesis) {
        return genesisColorToRgb(genesis);
    }

    /**
     * Creates fallback palettes when ROM loading fails.
     * Uses a simple grayscale gradient for visibility.
     */
    private static Palette[] createFallbackPalettes() {
        Palette[] palettes = new Palette[4];
        for (int i = 0; i < 4; i++) {
            palettes[i] = new Palette();
            for (int c = 0; c < 16; c++) {
                int gray = (c * 16) & 0xFF;
                palettes[i].setColor(c, new Palette.Color((byte) gray, (byte) gray, (byte) gray));
            }
        }
        return palettes;
    }

    /**
     * Gets the per-stage emerald colors for loading into palette line 3.
     * Each stage has 3 colors (6 bytes) that replace colors 11-13 in palette line 3.
     *
     * @param stageIndex Stage index (0-6)
     * @return Array of 3 Genesis color values, or null on error
     */
    public static int[] getEmeraldColors(int stageIndex) {
        try { return getEmeraldColors(GameServices.rom().getRom(), stageIndex); }
        catch (IOException failure) {
            LOGGER.warning("Failed to load emerald palette: " + failure.getMessage());
            return null;
        }
    }

    /**
     * Loads the emerald palette data from ROM if not already cached.
     */


    /**
     * Clears cached palette data. Call this when ROM changes or on reset.
     */
    public static void clearCache() {
        // Palette reads belong to the supplied immutable ROM; no process-global cache.
    }

    /**
     * Debug method to dump palette data to logs.
     */
    public static void dumpPaletteData(int stageIndex) {
        try {
            Palette[] palettes = createPalettes(GameServices.rom().getRom(), stageIndex);
            LOGGER.fine("Loaded " + palettes.length + " special-stage palettes for " + stageIndex);
        } catch (IOException failure) {
            LOGGER.warning("Failed to dump palette data: " + failure.getMessage());
        }
    }
}

