package com.openggf.game.sonic3k.ending;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayerCharacter;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * sub_5B18E's native scrolling-text machine and its 512x256 Plane A nametable.
 * This is comparison-free runtime state: all text, glyphs and timing are ROM
 * reads. Keeping the plane makes restore/replay reproduce cleared stale rows.
 */
public final class S3kCreditsState {
    public static final int PLANE_COLUMNS = 64;
    public static final int PLANE_ROWS = 32;
    public static final int SNAPSHOT_BYTES = 7 * Integer.BYTES
            + PLANE_COLUMNS * PLANE_ROWS * Short.BYTES;

    private int routine; // _unkFA86: 0 init, 2 delay, 4 scroll, 6 complete
    private int rowIndex; // _unkFA84, byte offset in the selected relative pointer table
    private int partIndex; // _unkFAAE, byte offset in off_5B284
    private int delay; // _unkF660, signed predecrement
    private int scroll; // Camera_Y_pos, 16.16
    private int speed; // _unkFAC8, 16.16
    private int clearedRows;
    private final short[] plane = new short[PLANE_COLUMNS * PLANE_ROWS];

    /** sub_5B514: locked-on table half, region, player mode and signed progress. */
    public void initialize(RomByteReader rom, PlayerCharacter character, int progress, boolean pal) {
        if (progress != -2 && progress != 0 && progress != 2)
            throw new IllegalArgumentException("Native ending progress must be -2, 0 or 2");
        int characterOffset = character == PlayerCharacter.KNUCKLES ? 12
                : character == PlayerCharacter.TAILS_ALONE ? 6 : 0;
        int offset = 0x24 + (pal ? 0x12 : 0) + characterOffset
                + (progress < 0 ? 0 : progress == 0 ? 2 : 4);
        delay = rom.readU16BE(S3kEndingResources.CREDIT_DELAY + offset);
        speed = rom.readU16BE(S3kEndingResources.CREDIT_SPEED + offset) << 8;
        routine = 2;
        rowIndex = partIndex = scroll = clearedRows = 0;
        Arrays.fill(plane, (short) 0);
    }

    /** Native ScreenEvents call; return true only on loc_5B29E's completion edge. */
    public boolean step(RomByteReader rom) {
        if (routine == 2) {
            delay = (short) (delay - 1);
            if (delay < 0) routine = 4;
            return false; // loc_5B1F4 does not fall through to loc_5B204
        }
        if (routine != 4) return false;
        int previous = scroll;
        scroll += speed;
        int position = (short) (scroll >> 16);
        if (((position ^ (previous >> 16)) & 0x10) == 0) return false;
        int planePosition = ((position << 4) + 0xE00) & 0xFFFF;
        int row = ((planePosition & 0xF00) >> 7) & 31;
        // sub_5B2D6 clears 40 columns in two rows, never the hidden 24 columns.
        Arrays.fill(plane, row * PLANE_COLUMNS, row * PLANE_COLUMNS + 40, (short) 0);
        Arrays.fill(plane, ((row + 1) & 31) * PLANE_COLUMNS,
                ((row + 1) & 31) * PLANE_COLUMNS + 40, (short) 0);
        clearedRows += 2;
        int table = rom.readU32BE(S3kEndingResources.CREDIT_PARTS + partIndex);
        int pointer = table + rom.readU16BE(table + rowIndex);
        int descriptor = rom.readU16BE(pointer);
        if (descriptor == 0xFFF8) {
            partIndex += 4;
            rowIndex = 0;
            return false;
        }
        if (descriptor == 0xFFF4) {
            routine = 6;
            return true;
        }
        if ((planePosition & 0xFF00) != (descriptor & 0xFF00)) return false;
        writeRow(rom, pointer + 2, descriptor);
        rowIndex += 2;
        return false;
    }

    /** sub_5B36C's ASCII decoder and column-major glyph pairs. */
    private void writeRow(RomByteReader rom, int text, int descriptor) {
        int base = (descriptor & 0xFFE) / 2;
        int palette = (descriptor & 1) << 13;
        int columns = 0;
        for (int address = text; address < text + 80; address++) {
            int character = rom.readU8(address);
            if (character == 0) return;
            if (character == ' ') {
                writeCell(base + columns++, 0, 0);
                continue;
            }
            int offset;
            int width;
            switch (character) {
                case 'I' -> { offset = 8 * 8; width = 1; }
                case '.' -> { offset = 26 * 8; width = 1; }
                case '(' -> { offset = 26 * 8 + 4; width = 1; }
                case ')' -> { offset = 26 * 8 + 8; width = 1; }
                case '&' -> { offset = 26 * 8 + 12; width = 2; }
                case '!' -> { offset = 26 * 8 + 20; width = 1; }
                case '?' -> { offset = 26 * 8 + 24; width = 2; }
                default -> {
                    if (character < 'A' || character > 'Z')
                        throw new IllegalStateException("Unsupported ROM credit character " + character);
                    offset = (character - 'A') * 8;
                    width = 2;
                }
            }
            int glyph = S3kEndingResources.CREDIT_GLYPHS + offset;
            for (int column = 0; column < width; column++) {
                int top = rom.readU16BE(glyph + column * 2);
                int bottom = rom.readU16BE(glyph + (width + column) * 2);
                int artTile=S3kEndingResources.CREDIT_TILE|palette|0x8000;
                writeCell(base + columns++, top+artTile, bottom+artTile);
            }
        }
        throw new IllegalStateException("Unterminated ROM credits row");
    }

    private void writeCell(int top, int upper, int lower) {
        int cell = top & (plane.length - 1);
        plane[cell] = (short) upper;
        plane[(cell + PLANE_COLUMNS) & (plane.length - 1)] = (short) lower;
    }

    public int routine() { return routine; }
    public int rowIndex() { return rowIndex; }
    public int partIndex() { return partIndex; }
    public int delay() { return delay; }
    public int scroll() { return scroll; }
    public int speed() { return speed; }
    public int clearedRows() { return clearedRows; }
    public boolean active() { return routine != 0; }
    public boolean complete() { return routine == 6; }
    /** A never-initialized credits plane is native zero RAM, not 4KiB per preending keyframe. */
    public int snapshotBytes() { return 7 * Integer.BYTES + (routine == 0 ? 0 : plane.length * Short.BYTES); }
    public int tile(int column, int row) { return plane[(row & 31) * PLANE_COLUMNS + (column & 63)] & 0xFFFF; }
    public void captureTo(ByteBuffer buffer) {
        buffer.putInt(routine).putInt(rowIndex).putInt(partIndex).putInt(delay)
                .putInt(scroll).putInt(speed).putInt(clearedRows);
        if(routine!=0)for (short tile : plane) buffer.putShort(tile);
    }
    public void restoreFrom(ByteBuffer buffer) {
        routine = buffer.getInt(); rowIndex = buffer.getInt(); partIndex = buffer.getInt();
        delay = buffer.getInt(); scroll = buffer.getInt(); speed = buffer.getInt(); clearedRows = buffer.getInt();
        if(routine==0)Arrays.fill(plane,(short)0);
        else for (int i = 0; i < plane.length; i++) plane[i] = buffer.getShort();
    }
}
