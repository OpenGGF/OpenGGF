package com.openggf.game.soniccd.device;

import java.util.Objects;

/**
 * Immutable parameters of a 2M Word RAM stamp transform. Addresses are byte offsets into the
 * caller's 256 KiB RAM; pixels are 4-bit color indices. There is no ownership or clock policy here.
 *
 * The fields are the Mega-CD graphics registers as MAME's {@code sega_segacd_device} decodes them
 * (src/mame/sega/megacd.cpp at 9cf98d883e72e2ab8d4d73a7104989b4b306dda7: stamp size FF8058
 * bits 0-2, memory-mode priority FF8002 bits 3-4, stamp-map base FF805A, image-buffer V cell
 * size FF805C, H dot size FF8062; map-base alignment per stamp/map size from its
 * {@code SCD_GET_TILE_INFO_*} decoders). Register widths MAME does not mask (V cells 5 bits,
 * H dots 9 bits) are pinned by {@code TestCdStampGraphics} without a document or measurement;
 * Sonic CD's values stay inside them. The title configures these fields in InitGfxOperation and RunGfxOperation,
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

    /** Register value 3 is reserved; {@link CdStampGraphics} leaves the destination unchanged for it. */
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
        // MAME: 16-pixel stamps on the 4096-pixel map use a fixed table at 20000h (the whole upper half).
        int mapAddress = stamp == StampSize.PIXELS_16 && map == MapSize.PIXELS_4096 ? 0x20000
                : (mapBaseWord << 2) & (CdStampGraphics.WORD_RAM_BYTES - tableBytes(stamp, map));
        return new CdStampRenderRequest(stamp, map, (stampModeWord & 1) != 0,
                Priority.values()[(wordRamModeWord >>> 3) & 3], mapAddress,
                (verticalCellsWord & 31) + 1, horizontalDotsWord & 511);
    }

    private static int tableBytes(StampSize stamp, MapSize map) {
        int stamps = map.pixels() / stamp.pixels();
        return stamps * stamps * 2;
    }

    static void requireWord(int value) {
        if ((value & ~0xffff) != 0) { throw new IllegalArgumentException("Not an unsigned 16-bit register word"); }
    }
}
