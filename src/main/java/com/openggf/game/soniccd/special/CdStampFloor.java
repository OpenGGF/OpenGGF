package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.device.CdStampGeometry;

import java.util.Objects;

/**
 * The special stage floor: the stage's stamp art, its 128 x 128 stamp map and the stamp
 * animations, with the rotated, perspective-projected render the Mega-CD graphics operation
 * performs on them each update.
 *
 * <p>Configuration is fixed by {@code InitGfxOperation} (scddisasm/src/Special Stage/Sub.asm:2563-2574): 32 x 32 stamps,
 * a 4096 x 4096 map, no repeat, a 256 x 96 image; {@code RunGfxOperation} (scddisasm/src/Special Stage/Sub.asm:2615-2621)
 * disables priority mode, so every pixel is written, including colour 0. Stamp art is
 * decompressed to stamp number 4 onwards ({@code LoadStamps}, scddisasm/src/Special Stage/Sub.asm:486-493), Stage 3's
 * second set to stamp number $200 ({@code LoadMap_SS3}, scddisasm/src/Special Stage/Sub.asm:441-448); the rest of the
 * stamp space is cleared at start (Sub.asm:223-229), so unloaded stamps read as colour 0.
 *
 * <p>Pixels follow the general graphics device: per output pixel, sample then step; start
 * coordinates are unsigned 13.3, steps signed 5.11, positions wrap at 24 bits; outside the
 * 4096-pixel map the pixel is 0; stamp-map entries and stamp art are addressed by
 * {@link CdStampGeometry} (translated from MAME, see {@code CdStampGraphics}).
 *
 * <p>Stamp numbers index all of Word RAM. Numbers below $380 hold the stage's art (or cleared
 * memory). Numbers from $500 read Word RAM above the map, which is cleared at start and never
 * written; every stage uses $7FC (map entry $FFFF) as its empty cell, read as colour 0 (native
 * Word RAM $28000-$3FFFF stays zero throughout the stage-select SS1 run). Unsupported, rejected at
 * load: numbers $380-$4FC, which would read the live sprite buffer, trace table, image or map.
 */
public final class CdStampFloor {
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 96;
    static final int MAP_STAMPS = 128;
    private static final int STAMP_SPACE_BYTES = 0x1C000;
    private static final int STAMP_BYTES = 0x80;
    private static final int STAMP_EDGE = 32;
    private static final int MAP_PIXELS = MAP_STAMPS * STAMP_EDGE;
    private static final int POSITION_MASK = 0xFFFFFF;
    /** Stamp numbers from here read cleared, never-written Word RAM above the map (see above). */
    private static final int FIRST_UNWRITTEN_STAMP = 0x500;

    private final byte[] stampSpace;
    private final short[] map;
    private final byte[] stampTypes;
    private final int[][] hazardAnim;
    private final int[][] fanAnim;
    private int hazardAnimDelay;
    private int hazardAnimFrame;
    private int fanAnimFrame;

    CdStampFloor(byte[] stampSpace, short[] map, byte[] stampTypes, int[][] hazardAnim, int[][] fanAnim) {
        if (stampSpace.length != STAMP_SPACE_BYTES) throw new IllegalArgumentException("Stamp space must be $1C000 bytes");
        if (map.length != MAP_STAMPS * MAP_STAMPS) throw new IllegalArgumentException("Map must be 128 x 128");
        this.stampSpace = stampSpace;
        this.map = map;
        this.stampTypes = Objects.requireNonNull(stampTypes);
        this.hazardAnim = hazardAnim;
        this.fanAnim = fanAnim;
        for (short entry : map) requireStamp(entry);
        for (int[][] table : new int[][][] {hazardAnim, fanAnim}) {
            for (int[] e : table) {
                if ((e[0] & 1) != 0 || e[0] < 0 || e[0] >= map.length * 2) throw new IllegalArgumentException("Animation offset outside map");
                for (int i = 1; i < e.length; i++) requireStamp(e[i]);
            }
        }
    }

    private static void requireStamp(int entry) {
        int stamp = entry & 0x7FC;
        if (stamp >= 0x380 && stamp < 0x500) throw new IllegalArgumentException("Stamp number overlaps live Word RAM buffers");
    }

    /** Raw map entry at a stamp cell. */
    public int mapEntry(int column, int row) { return map[column + row * MAP_STAMPS] & 0xFFFF; }

    short[] mapView() { return map; }

    /**
     * {@code AnimateStamps} (scddisasm/src/Special Stage/Sub.asm:1240-1279): the fan frame advances every update through
     * three frames; the hazard frame advances on every second update through four frames.
     * Each entry writes its frame's stamp into its map cell.
     */
    public void animate() {
        fanAnimFrame += 2;
        if (fanAnimFrame >= 6) fanAnimFrame = 0;
        hazardAnimDelay++;
        if ((hazardAnimDelay & 1) == 0) {
            hazardAnimFrame = (hazardAnimFrame + 2) & 7;
            for (int[] e : hazardAnim) map[e[0] >> 1] = (short) e[1 + (hazardAnimFrame >> 1)];
        }
        for (int[] e : fanAnim) map[e[0] >> 1] = (short) e[1 + (fanAnimFrame >> 1)];
    }

    /**
     * {@code GetStampAtPos} (scddisasm/src/Special Stage/Sub.asm:1481-1495) then the stage's stamp-type table
     * ({@code ObjSonic_GetStampType}, scddisasm/src/Special Stage/Sub.asm:1457-1468). Returns the raw entry in the high
     * half and the type in the low byte.
     */
    public int stampAt(int x, int y) {
        int offset = (((x & 0xFFFF) >>> 5) * 2 + ((((y & 0xFFFF) >>> 5) << 8) & 0xFFFF)) & 0xFFFF;
        int index = (short) offset >> 1;
        if (index < 0 || index >= map.length) throw new IllegalStateException("Stamp lookup outside the map");
        int raw = map[index] & 0xFFFF;
        int typeIndex = (raw & 0x7FF) >>> 2;
        if (typeIndex >= stampTypes.length) throw new IllegalStateException("Stamp type index outside the $200-byte window");
        return (raw << 16) | (stampTypes[typeIndex] & 0xFF);
    }

    /**
     * One graphics operation: renders {@code trace} (96 lines of x, y, dx, dy words) into a
     * row-major 256 x 96 image of 4-bit colour indices.
     */
    public void render(CdStampTrace trace, byte[] image) {
        if (image.length != IMAGE_WIDTH * IMAGE_HEIGHT) throw new IllegalArgumentException("Image must be 256 x 96");
        for (int line = 0; line < IMAGE_HEIGHT; line++) {
            int x = (trace.x(line) & 0xFFFF) << 8;
            int y = (trace.y(line) & 0xFFFF) << 8;
            int dx = (short) trace.dx(line);
            int dy = (short) trace.dy(line);
            int row = line * IMAGE_WIDTH;
            for (int dot = 0; dot < IMAGE_WIDTH; dot++) {
                image[row + dot] = (byte) colourAt(x >>> 11, y >>> 11);
                x = (x + dx) & POSITION_MASK;
                y = (y + dy) & POSITION_MASK;
            }
        }
    }

    /** Colour of map pixel {@code (px, py)}, each 0-8191; outside the map (no repeat) it is 0. */
    private int colourAt(int px, int py) {
        if (px >= MAP_PIXELS || py >= MAP_PIXELS) return 0;
        int entry = map[(px >>> 5) + (py >>> 5) * MAP_STAMPS] & 0xFFFF;
        int number = CdStampGeometry.stampNumber(entry, STAMP_EDGE);
        if (number == 0 || number >= FIRST_UNWRITTEN_STAMP) return 0;
        int nibble = (number << 8) + CdStampGeometry.artNibble(STAMP_EDGE, entry >>> 13, px & (STAMP_EDGE - 1),
                py & (STAMP_EDGE - 1));
        int pair = stampSpace[nibble >>> 1] & 0xFF;
        return (nibble & 1) == 0 ? pair >>> 4 : pair & 0x0F;
    }

    int hazardAnimDelay() { return hazardAnimDelay; }
    int hazardAnimFrame() { return hazardAnimFrame; }
    int fanAnimFrame() { return fanAnimFrame; }

    void restoreAnimation(int delay, int hazardFrame, int fanFrame, short[] savedMap) {
        hazardAnimDelay = delay;
        hazardAnimFrame = hazardFrame;
        fanAnimFrame = fanFrame;
        System.arraycopy(savedMap, 0, map, 0, map.length);
    }

    static int stampSpaceOffset(int stampNumber) { return stampNumber * STAMP_BYTES; }
    static int stampSpaceBytes() { return STAMP_SPACE_BYTES; }
}
