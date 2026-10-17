package com.openggf.game.sonic3k;

import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import java.util.Arrays;

/**
 * Find_Tile_FG/BG ($EFF0/$EFB6): encoded word row pointers address the current
 * layout and the two resident ROM definition tables. This is not a general RAM bus.
 * Layout aliases use the existing COW Map bytes; asset aliases read live definitions.
 */
final class S3kCollisionLayout {
    private static final int LAYOUT_BASE = 0x8000, LAYOUT_END = 0x9000, TILE_END = 0xA800;
    private byte[] encoded;
    private long lastTouchedEpoch = -1;
    private final int width, height;
    private final int[] first = new int[0x1000];
    private final int[] next;

    S3kCollisionLayout(byte[] encoded, int width, int height) {
        if (encoded.length != 0x1000) throw new IllegalArgumentException("Load_Level layout extent");
        this.encoded = encoded.clone();this.width = width;this.height = height;
        next = new int[width * height * 2];Arrays.fill(first, -1);Arrays.fill(next, -1);
        for (int row = 0; row < Math.min(height, 32); row++) {
            for (int layer = 0; layer < 2; layer++) {
                int pointer = rowPointer(layer, row * 4);
                if (pointer == 0) continue;
                for (int col = 0; col < width; col++) {
                    int address = (pointer + col) & 0xFFFF;
                    if (address < LAYOUT_BASE || address >= LAYOUT_END) continue;
                    int index = row * width * 2 + layer * width + col;
                    next[index] = first[address - LAYOUT_BASE];first[address - LAYOUT_BASE] = index;
                }
            }
        }
    }

    byte[] encoded() { return encoded; } // Internal COW value; no copy on an unchanged capture.

    Map newMap(Sonic3kLevel owner, byte[] restored) {
        LayoutMap result = new LayoutMap(owner, width, height, restored);
        if (restored == null) {
            byte[] data = result.getData();
            for (int layer = 0; layer < 2; layer++) {
                int columns = Math.min(width, word(layer * 2));
                int rows = Math.min(height, word(4 + layer * 2));
                for (int row = 0; row < rows; row++) {
                    int pointer = rowPointer(layer, row * 4);
                    for (int col = 0; col < columns; col++) {
                        int address = (pointer + col) & 0xFFFF;
                        if (pointer != 0 && address >= LAYOUT_BASE && address < LAYOUT_END)
                            data[row * width * 2 + layer * width + col] = encoded[address - LAYOUT_BASE];
                    }
                }
            }
        }
        return result;
    }

    private int word(int offset) { return ((encoded[offset] & 0xFF) << 8) | (encoded[offset + 1] & 0xFF); }

    private int rowPointer(int layer, int rowIndex) {
        int offset = 8 + layer * 2 + rowIndex;
        return ((encoded[offset] & 0xFF) << 8) | (encoded[offset + 1] & 0xFF);
    }

    ChunkDesc descriptor(Sonic3kLevel level, byte layer, int x, int y, int screenYWrapValue) {
        if (layer < 0 || layer > 1) throw new IllegalArgumentException("collision plane");
        int xWord = x & 0xFFFF, yWord = y & 0xFFFF;
        // LevelSetup writes $7C; looping ScreenInit writes $3C alongside $7FF.
        // Screen_Y_wrap_value is already a production camera register, not a visual layer height.
        int rowMask = (screenYWrapValue >>> 5) & 0x7C;
        int rowIndex = (yWord >>> 5) & rowMask;
        int address = (rowPointer(layer, rowIndex) + (xWord >>> 7)) & 0xFFFF;
        int block = readByte(level, address);
        if (block >= level.getBlockCount()) throw unknown(address, "128x128 definition " + block);
        return level.getBlock(block).getChunkDesc((xWord & 0x70) >>> 4, (yWord & 0x70) >>> 4);
    }

    private int readByte(Sonic3kLevel level, int address) {
        if (address >= LAYOUT_BASE && address < LAYOUT_END) {
            return encoded[address - LAYOUT_BASE] & 0xFF;
        }
        if (address < LAYOUT_BASE) {
            // MOVEA.W sign-extends this layout-byte pointer. A positive word
            // selects ROM000000..007FFF, not resident definition RAM FF0000.
            // The later ChunkAddrArray lookup separately preserves D1's FFFF.
            return level.collisionRomByteAt(address);
        }
        if (address < TILE_END) {
            int offset = address - LAYOUT_END, chunk = offset >>> 3;
            if (chunk >= level.getChunkCount()) throw unknown(address, "unwritten 16x16 bytes");
            int word = level.getChunk(chunk).getPatternDesc((offset >>> 1) & 1, (offset >>> 2) & 1).get();
            return ((offset & 1) == 0 ? word >>> 8 : word) & 0xFF;
        }
        throw unknown(address, "outside the source-backed layout/16x16 regions");
    }

    private static IllegalStateException unknown(int address, String reason) {
        return new IllegalStateException(String.format("Unmodelled S3K collision address $%06X: %s", ((short) address) & 0xFFFFFF, reason));
    }

    private void setAliases(LayoutMap map, int layer, int x, int y, byte value) {
        if (y >= 32) return;
        int pointer = rowPointer(layer, y * 4);
        if (pointer == 0) return; // No encoded row was installed for this logical map row.
        int address = (pointer + x) & 0xFFFF;
        if (address < LAYOUT_BASE || address >= LAYOUT_END)
            throw unknown(address, "layout mutation reaches a different asset owner");
        // LevelSnapshot advances the epoch before the per-game adapter shares this
        // workspace. Copy only on an actual write in a new epoch, never per frame.
        long epoch = map.owner.currentEpoch();
        if (lastTouchedEpoch != epoch) { encoded = encoded.clone();lastTouchedEpoch = epoch; }
        encoded[address - LAYOUT_BASE] = value;
        byte[] data = map.getData();
        for (int i = first[address - LAYOUT_BASE]; i >= 0; i = next[i]) data[i] = value;
    }

    /** Existing mutation surfaces/rewind still own COW and flattened Map bytes. */
    private static final class LayoutMap extends Map {
        private final Sonic3kLevel owner;
        LayoutMap(Sonic3kLevel owner, int width, int height, byte[] data) {
            super(2, width, height, data);this.owner = owner;
        }
        @Override public void setValue(int layer, int x, int y, byte value) {
            super.setValue(layer, x, y, value);
            if (owner.collisionLayout != null) owner.collisionLayout.setAliases(this, layer, x, y, value);
        }
    }
}
