package com.openggf.game.sonic3k.bluespheres;

/** Plane name-table writes the Blue Spheres screens make. */
final class S3kBlueSpheresPlanes {
    private static final int ROW = S3kBlueSpheresAssets.PLANE_WIDTH;

    private S3kBlueSpheresPlanes() {
    }

    /**
     * Plane_Map_To_VRAM: {@code width x height} words to a byte offset in a
     * name table, each row {@code $80} bytes after the last. VRAM addressing is
     * linear, so a row that runs past column 63 continues on the next line.
     */
    static void copy(int[] plane, int byteOffset, int[] words, int width, int height) {
        int start = byteOffset / 2;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = (start + row * ROW + col) & (plane.length - 1);
                plane[index] = words[row * width + col] & 0xFFFF;
            }
        }
    }

    /**
     * sub_5B318/sub_5B36C (sonic3k.asm:121697-121836): credits-font text as two
     * rows of glyph cells starting {@code byteOffset} into plane A, each cell
     * offset by {@code artTile}. Letters are two cells wide; I, period,
     * brackets and {@code !} one; {@code &} and {@code ?} two; a space is an
     * empty cell.
     */
    static void writeText(S3kBlueSpheresAssets assets, int[] plane, int byteOffset, String text, int artTile) {
        int cell = byteOffset / 2;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == ' ') {
                put(plane, cell++, 0, 0);
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
                    if (character < 'A' || character > 'Z') {
                        throw new IllegalArgumentException("No credits glyph for " + character);
                    }
                    offset = (character - 'A') * 8;
                    width = 2;
                }
            }
            int glyph = S3kBlueSpheresAssets.CREDITS_GLYPHS + offset;
            for (int column = 0; column < width; column++) {
                int top = assets.word(glyph + column * 2);
                int bottom = assets.word(glyph + (width + column) * 2);
                put(plane, cell++, (top + artTile) & 0xFFFF, (bottom + artTile) & 0xFFFF);
            }
        }
    }

    private static void put(int[] plane, int cell, int top, int bottom) {
        plane[cell & (plane.length - 1)] = top;
        plane[(cell + ROW) & (plane.length - 1)] = bottom;
    }
}
