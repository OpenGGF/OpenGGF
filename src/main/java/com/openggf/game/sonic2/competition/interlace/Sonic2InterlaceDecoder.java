package com.openggf.game.sonic2.competition.interlace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Native Mode 5 interlace-2 CPU sampling, independently of engine Pattern/Level.
 * Hardware interpretation corroborated by Genesis Plus GX vdp_render.c,
 * revision 075f18f57f7fb2e47bf9e7076f924f956e685c99: GET_LSB_TILE_IM2 (139-148),
 * render_bg_m5_im2 (2167-2196), render_obj_m5_im2 (4077-4129), parse_satb_m5
 * (4575-4609). https://github.com/ekeeke/Genesis-Plus-GX/blob/075f18f57f7fb2e47bf9e7076f924f956e685c99/core/vdp_render.c
 */
public final class Sonic2InterlaceDecoder {
    public static final int PATTERN_COUNT = 1024;
    public static final int PATTERN_BYTES = 64;

    private Sonic2InterlaceDecoder() { }

    /** Explicit field identity; rendering never advances or invents a field clock. */
    public enum Field {
        EVEN(0), ODD(1);
        private final int parity;
        Field(int parity) { this.parity = parity; }
        public int parity() { return parity; }
        public int physicalLine(int fieldLine) { return fieldLine * 2 + parity; }
        /** First field scanline belonging to a native physical top coordinate. */
        public int projectY(int physicalY) { return Math.floorDiv(physicalY + 1 - parity, 2); }
    }

    /** Already-native attributes: bit 10 is ignored, and must not be halved again. */
    public record NameWord(int value) {
        public NameWord { value &= 0xFFFF; }
        public int patternIndex() { return value & 0x3FF; }
        public int palette() { return value >>> 13 & 3; }
        public boolean priority() { return (value & 0x8000) != 0; }
        public boolean hFlip() { return (value & 0x0800) != 0; }
        public boolean vFlip() { return (value & 0x1000) != 0; }
    }

    /** Name-table dimensions in native cells; scroll offsets are source-pixel offsets. */
    public record Plane(int tableAddress, int widthCells, int heightCells, int scrollX, int scrollY) {
        public Plane {
            if ((tableAddress & 1) != 0 || tableAddress < 0 || tableAddress >= Sonic2InterlaceVram.SIZE
                    || !validDimension(widthCells) || !validDimension(heightCells))
                throw new IllegalArgumentException("Invalid Mode 5 plane table");
        }
        private static boolean validDimension(int value) { return value == 32 || value == 64 || value == 128; }
    }

    /** Raw hardware SAT words, before field projection. Y includes the native $100 bias. */
    public record Sprite(int rawY, int size, int link, NameWord name, int rawX) {
        public Sprite {
            rawY &= 0x3FF;
            size &= 0xF;
            link &= 0x7F;
            rawX &= 0x1FF;
            Objects.requireNonNull(name, "name");
        }
        public int x() { return rawX - 0x80; }
        public int y() { return rawY - 0x100; }
        public int widthCells() { return (size >>> 2) + 1; }
        public int heightCells() { return (size & 3) + 1; }
        public int widthPixels() { return widthCells() * 8; }
        public int heightPixels() { return heightCells() * 16; }
    }

    /** Color zero is transparent; palette/priority still describe the sampled name word. */
    public record Sample(int color, NameWord name) {
        public boolean opaque() { return color != 0; }
        public int paletteColor() { return name.palette() * 16 + color; }
    }

    /** Retainable source image and SAT order for one field. */
    public record Input(Sonic2InterlaceVram vram, Field field, List<Sprite> sprites) {
        public Input {
            Objects.requireNonNull(vram, "vram");
            Objects.requireNonNull(field, "field");
            sprites = List.copyOf(sprites);
        }
    }

    /** All 128 native pixels, row-major. No alternate-row selection or scaling. */
    public static byte[] decodePattern(Sonic2InterlaceVram vram, int patternIndex) {
        byte[] pixels = new byte[128];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 8; x++)
            pixels[y * 8 + x] = (byte) patternPixel(vram, patternIndex, x, y);
        return pixels;
    }

    public static int patternPixel(Sonic2InterlaceVram vram, int patternIndex, int x, int y) {
        Objects.checkIndex(x, 8);
        Objects.checkIndex(y, 16);
        int packed = vram.readByte((patternIndex & 0x3FF) * PATTERN_BYTES + y * 4 + x / 2);
        return (packed >>> ((x & 1) == 0 ? 4 : 0)) & 15;
    }

    public static Sample sampleCell(Sonic2InterlaceVram vram, NameWord name, int x, int nativeRow) {
        Objects.checkIndex(x, 8);
        Objects.checkIndex(nativeRow, 16);
        return new Sample(patternPixel(vram, name.patternIndex(), name.hFlip() ? 7 - x : x,
                name.vFlip() ? 15 - nativeRow : nativeRow), name);
    }

    /** Caller may supply per-line scroll by constructing the line's Plane input. */
    public static Sample samplePlane(Sonic2InterlaceVram vram, Plane plane, Field field,
            int screenX, int fieldLine) {
        int x = Math.floorMod(screenX + plane.scrollX(), plane.widthCells() * 8);
        int y = Math.floorMod(field.physicalLine(fieldLine) + plane.scrollY(), plane.heightCells() * 16);
        var name = planeName(vram, plane, x / 8, y / 16);
        return sampleCell(vram, name, x & 7, y & 15);
    }

    public static NameWord planeName(Sonic2InterlaceVram vram, Plane plane, int column, int row) {
        int index = Math.floorMod(row, plane.heightCells()) * plane.widthCells()
                + Math.floorMod(column, plane.widthCells());
        // The VDP's name-table row addressing aliases at 8 KiB, including larger
        // declared plane dimensions (render_bg_m5_im2, pinned source:2192-2196).
        return new NameWord(vram.readWord(plane.tableAddress() + ((index * 2) & 0x1FFF)));
    }

    /** Local native pixels; whole-sprite mirroring precedes column-major pattern addressing. */
    public static Sample sampleSprite(Sonic2InterlaceVram vram, Sprite sprite, int x, int nativeY) {
        Objects.checkIndex(x, sprite.widthPixels());
        Objects.checkIndex(nativeY, sprite.heightPixels());
        if (sprite.name().hFlip()) x = sprite.widthPixels() - 1 - x;
        if (sprite.name().vFlip()) nativeY = sprite.heightPixels() - 1 - nativeY;
        int pattern = (sprite.name().patternIndex() + x / 8 * sprite.heightCells() + nativeY / 16) & 0x3FF;
        return new Sample(patternPixel(vram, pattern, x & 7, nativeY & 15), sprite.name());
    }

    /** Follow hardware links, retaining repeated entries up to the table's H40/H32 capacity. */
    public static List<Sprite> readSat(Sonic2InterlaceVram vram, int tableAddress, boolean h40) {
        if (tableAddress < 0 || tableAddress >= Sonic2InterlaceVram.SIZE || (tableAddress & 7) != 0)
            throw new IllegalArgumentException("SAT base must be an aligned VRAM address");
        int limit = h40 ? 80 : 64;
        var result = new ArrayList<Sprite>();
        int index = 0;
        for (int count = 0; count < limit; count++) {
            int address = tableAddress + index * 8;
            var sprite = new Sprite(vram.readWord(address), vram.readByte(address + 2),
                    vram.readByte(address + 3), new NameWord(vram.readWord(address + 4)),
                    vram.readWord(address + 6));
            result.add(sprite);
            index = sprite.link();
            if (index == 0 || index >= limit) break;
        }
        return List.copyOf(result);
    }
}
