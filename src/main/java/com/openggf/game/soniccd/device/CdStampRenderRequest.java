package com.openggf.game.soniccd.device;

import java.util.Objects;

/**
 * Immutable parameters of a 2M Word RAM stamp transform. Addresses are byte offsets into the
 * caller's 256 KiB RAM; pixels are 4-bit color indices. There is no ownership or clock policy here.
 *
 * Semantics: GPGX 051d430d3d1b54625f9900c8f152d7f232e06daf,
 * core/cd_hw/gfx.c, gfx_start (604-653) and priority table in gfx_init (359-373).
 * The title configures these fields in InitGfxOperation and RunGfxOperation,
 * docs/scddisasm/src/Title Screen/Sub.asm:270-278,322-327 at 1ecc5f3a.
 * Those sources are research references, never runtime inputs.
 */
public record CdStampRenderRequest(StampSize stampSize, MapSize mapSize, boolean repeat, Priority priority,
                                    int mapByteAddress, int imageColumnStrideCells, int widthPixels) {
    public enum StampSize {
        PIXELS_16(16), PIXELS_32(32);

        private final int pixels;
        StampSize(int pixels) { this.pixels = pixels; }
        public int pixels() { return pixels; }
    }

    public enum MapSize {
        PIXELS_256(256), PIXELS_4096(4096);

        private final int pixels;
        MapSize(int pixels) { this.pixels = pixels; }
        public int pixels() { return pixels; }
    }

    /** The fourth register encoding preserves the destination, even though it is reserved. */
    public enum Priority { NORMAL, UNDERWRITE, OVERWRITE, PRESERVE }

    public CdStampRenderRequest {
        Objects.requireNonNull(stampSize, "stampSize");
        Objects.requireNonNull(mapSize, "mapSize");
        Objects.requireNonNull(priority, "priority");
        int tableBytes = tableBytes(stampSize, mapSize);
        if (mapByteAddress < 0 || mapByteAddress > CdStampGraphics.WORD_RAM_BYTES - tableBytes
                || (mapByteAddress & (tableBytes - 1)) != 0) {
            throw new IllegalArgumentException("Map byte address must be table-aligned within 2M Word RAM");
        }
        if (imageColumnStrideCells < 1 || imageColumnStrideCells > 32) {
            throw new IllegalArgumentException("Image column stride must contain 1 through 32 cells");
        }
        if (widthPixels < 0 || widthPixels > 511) {
            throw new IllegalArgumentException("Line width must contain 0 through 511 pixels");
        }
    }

    /**
     * Decode unsigned 16-bit register words 58, 02, 5A, 5C and 62 respectively.
     * This decodes transform fields only. A live owner must separately enforce 2M RAM access,
     * readiness and stable parameters; this method does not grant access or start a scheduler.
     */
    public static CdStampRenderRequest fromRegisterWords(int stampModeWord, int wordRamModeWord,
                                                          int mapBaseWord, int verticalCellsWord,
                                                          int horizontalDotsWord) {
        requireWord(stampModeWord);
        requireWord(wordRamModeWord);
        requireWord(mapBaseWord);
        requireWord(verticalCellsWord);
        requireWord(horizontalDotsWord);
        StampSize stamp = (stampModeWord & 2) == 0 ? StampSize.PIXELS_16 : StampSize.PIXELS_32;
        MapSize map = (stampModeWord & 4) == 0 ? MapSize.PIXELS_256 : MapSize.PIXELS_4096;
        int mapAddress = (mapBaseWord << 2) & (CdStampGraphics.WORD_RAM_BYTES - tableBytes(stamp, map));
        return new CdStampRenderRequest(stamp, map, (stampModeWord & 1) != 0,
                Priority.values()[(wordRamModeWord >>> 3) & 3], mapAddress,
                (verticalCellsWord & 31) + 1, horizontalDotsWord & 511);
    }

    int sourcePositionMask() { return (mapSize.pixels() << 11) - 1; }
    int stampPositionShift() { return stampSize == StampSize.PIXELS_16 ? 15 : 16; }
    int mapRowShift() { return Integer.numberOfTrailingZeros(mapSize.pixels() / stampSize.pixels()); }
    int stampNumberMask() { return stampSize == StampSize.PIXELS_16 ? 0x7ff : 0x7fc; }
    int columnStepNibbles() { return imageColumnStrideCells * 64 - 7; }

    private static int tableBytes(StampSize stamp, MapSize map) {
        int stamps = map.pixels() / stamp.pixels();
        return stamps * stamps * 2;
    }

    static void requireWord(int value) {
        if ((value & ~0xffff) != 0) { throw new IllegalArgumentException("Not an unsigned 16-bit register word"); }
    }
}
