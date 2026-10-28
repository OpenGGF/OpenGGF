package com.openggf.graphics;

/**
 * Original, hand-authored five-column menu lettering. Each hexadecimal pair is
 * one top-to-bottom row of five pixels, most-significant bit at the left.
 * These masks are native glyph artwork, not samples of the nine-pixel font.
 * Capitals end on row six; lowercase descenders may use row seven within the
 * same eight-pixel cell. The sixth column remains transparent spacing.
 *
 * <p>Two faces share this artwork. {@link Face#FIXED} is the tuned 6x8 cell
 * lettering of {@code MenuPixelFont.compact()}, measured by the transport and
 * trace overlays. The proportional faces redraw a few narrow lowercase letters
 * and advance each glyph by its ink width plus one spacing column, so menu text
 * loses the monospace texture that cropping alone would keep: three quarters of
 * the masks already use all five columns. Digits stay tabular at the cell
 * width; native symbols keep their whole cell.
 */
final class MenuGlyphs {
    static final int ADVANCE = 6;
    static final int HEIGHT = 8;
    static final int FULL_ADVANCE = 9;
    static final int FULL_HEIGHT = 10;
    static final int COLUMNS = 16;
    static final int ATLAS_WIDTH = COLUMNS * ADVANCE;
    static final int ATLAS_HEIGHT = 6 * HEIGHT;
    static final int FULL_ATLAS_WIDTH = COLUMNS * FULL_ADVANCE;
    static final int FULL_ATLAS_HEIGHT = 6 * FULL_HEIGHT;
    // The final atlas cell is unused by the 95 printable glyphs.
    static final int SOLID_X = ATLAS_WIDTH - ADVANCE;
    static final int SOLID_Y = ATLAS_HEIGHT - HEIGHT;
    static final float SOLID_U = (SOLID_X + .5f) / ATLAS_WIDTH;
    static final float SOLID_V = 1f - (SOLID_Y + .5f) / ATLAS_HEIGHT;
    static final int LIGHT_SPACE_ADVANCE = 3;
    static final int BOLD_SPACE_ADVANCE = 4;
    private static final int[] FULL_COLUMN_POSITIONS = {0, 1, 3, 5, 6};
    private static final byte[][] ROWS = new byte[95][];
    private static final byte[][] PROPORTIONAL_ROWS = new byte[95][];
    private static final byte[] LIGHT_LEFT = new byte[95], LIGHT_WIDTH = new byte[95], LIGHT_ADVANCE = new byte[95];
    private static final byte[] BOLD_LEFT = new byte[95], BOLD_WIDTH = new byte[95], BOLD_ADVANCE = new byte[95];

    /** Native lettering faces; every metric is in unscaled native pixels. */
    enum Face {
        /** Tuned fixed 6x8 cells, kept for compact overlays and their measurements. */
        FIXED(ADVANCE, HEIGHT),
        /** Proportional light lettering in 6x8 cells. */
        LIGHT(ADVANCE, HEIGHT),
        /** Proportional bold lettering derived from the light masks, in 9x10 cells. */
        BOLD(FULL_ADVANCE, FULL_HEIGHT);

        final int cellWidth;
        final int cellHeight;

        Face(int cellWidth, int cellHeight) {
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
        }
    }

    static {
        glyph(' ', "00000000000000");
        glyph('!', "04040404040004");
        glyph('"', "0A0A0A00000000");
        glyph('#', "0A0A1F0A1F0A0A");
        glyph('$', "040F140E051E04");
        glyph('%', "18190204081303");
        glyph('&', "0C12140815120D");
        glyph('\'', "04040800000000");
        glyph('(', "02040808080402");
        glyph(')', "08040202020408");
        glyph('*', "00150E1F0E1500");
        glyph('+', "0004041F040400");
        glyph(',', "00000000000408");
        glyph('-', "0000001F000000");
        glyph('.', "00000000000C0C");
        glyph('/', "01010204081010");
        glyph('0', "0E11131519110E");
        glyph('1', "040C040404040E");
        glyph('2', "0E11010204081F");
        glyph('3', "1E01010E01011E");
        glyph('4', "02060A121F0202");
        glyph('5', "1F10101E01011E");
        glyph('6', "0E10101E11110E");
        glyph('7', "1F010204080808");
        glyph('8', "0E11110E11110E");
        glyph('9', "0E11110F01010E");
        glyph(':', "000C0C000C0C00");
        glyph(';', "000C0C00040408");
        glyph('<', "01020408040201");
        glyph('=', "00001F001F0000");
        glyph('>', "10080402040810");
        glyph('?', "0E110102040004");
        glyph('@', "0E11171517100F");
        glyph('A', "0E11111F111111");
        glyph('B', "1E11111E11111E");
        glyph('C', "0F10101010100F");
        glyph('D', "1E11111111111E");
        glyph('E', "1F10101E10101F");
        glyph('F', "1F10101E101010");
        glyph('G', "0F10101711110F");
        glyph('H', "1111111F111111");
        glyph('I', "0E04040404040E");
        glyph('J', "0702020202120C");
        glyph('K', "11121418141211");
        glyph('L', "1010101010101F");
        glyph('M', "111B1515111111");
        glyph('N', "11191915131311");
        glyph('O', "0E11111111110E");
        glyph('P', "1E11111E101010");
        glyph('Q', "0E11111115120D");
        glyph('R', "1E11111E141211");
        glyph('S', "0F10100E01011E");
        glyph('T', "1F040404040404");
        glyph('U', "1111111111110E");
        glyph('V', "11111111110A04");
        glyph('W', "11111115151B11");
        glyph('X', "11110A040A1111");
        glyph('Y', "11110A04040404");
        glyph('Z', "1F01020408101F");
        glyph('[', "0E08080808080E");
        glyph('\\', "10100804020101");
        glyph(']', "0E02020202020E");
        glyph('^', "040A1100000000");
        glyph('_', "0000000000001F");
        glyph('`', "08040200000000");
        glyph('a', "00000E010F110F");
        glyph('b', "1010161911111E");
        glyph('c', "00000F1010100F");
        glyph('d', "01010D1311110F");
        glyph('e', "00000E111F100E");
        glyph('f', "0609091C080808");
        glyph('g', "00000F11110F010E");
        glyph('h', "10101619111111");
        glyph('i', "04000C0404040E");
        glyph('j', "020006020202120C");
        glyph('k', "10101214181412");
        glyph('l', "0C04040404040E");
        glyph('m', "00001A15151515");
        glyph('n', "00001619111111");
        glyph('o', "00000E1111110E");
        glyph('p', "00001E11111E1010");
        glyph('q', "00000F11110F0101");
        glyph('r', "00001619101010");
        glyph('s', "00000F100E011E");
        glyph('t', "08081E08080906");
        glyph('u', "0000111111130D");
        glyph('v', "00001111110A04");
        glyph('w', "0000111115150A");
        glyph('x', "0000110A040A11");
        glyph('y', "00001111110F010E");
        glyph('z', "00001F0204081F");
        glyph('{', "03040408040403");
        glyph('|', "04040404040404");
        glyph('}', "18040402040418");
        glyph('~', "00000815020000");

        for (int i = 0; i < ROWS.length; i++) PROPORTIONAL_ROWS[i] = ROWS[i].clone();
        // Narrow lowercase for proportional menus, drawn from the left column.
        // Capitals, m/w, digits and every descender row keep their shape.
        proportional('c', "00000E1010100E");
        proportional('f', "0C081C08080808");
        proportional('i', "10001010101010");
        proportional('j', "04000C0404041408");
        proportional('l', "10101010101008");
        proportional('r', "00001618101010");
        proportional('s', "00000E100C021C");
        proportional('t', "08081C0808080C");
        proportional('z', "00001E0408101E");
        for (int index = 0; index < 95; index++) measure(index);
    }

    private MenuGlyphs() { }

    private static void glyph(char character, String hexadecimalRows) {
        if (hexadecimalRows.length() != 14 && hexadecimalRows.length() != 16) {
            throw new IllegalArgumentException("Seven or eight glyph rows required");
        }
        byte[] rows = new byte[HEIGHT];
        for (int i = 0; i < hexadecimalRows.length() / 2; i++) {
            rows[i] = (byte) Integer.parseInt(hexadecimalRows.substring(i * 2, i * 2 + 2), 16);
            if ((rows[i] & ~31) != 0) throw new IllegalArgumentException("Five glyph columns required");
        }
        ROWS[character - 32] = rows;
    }

    private static void proportional(char character, String hexadecimalRows) {
        byte[] fixed = ROWS[character - 32];
        glyph(character, hexadecimalRows);
        PROPORTIONAL_ROWS[character - 32] = ROWS[character - 32];
        ROWS[character - 32] = fixed;
    }

    private static void measure(int index) {
        char character = (char) (index + 32);
        if (character == ' ') {
            LIGHT_ADVANCE[index] = LIGHT_SPACE_ADVANCE;
            BOLD_ADVANCE[index] = BOLD_SPACE_ADVANCE;
            return;
        }
        int ink = 0;
        for (byte row : PROPORTIONAL_ROWS[index]) ink |= row;
        // Tabular digits keep their authored cell origin so counters never jitter.
        int left = character >= '0' && character <= '9' ? 0 : Integer.numberOfLeadingZeros(ink) - 27;
        int right = character >= '0' && character <= '9' ? 4 : 31 - Integer.numberOfTrailingZeros(ink) - 27;
        LIGHT_LEFT[index] = (byte) left;
        LIGHT_WIDTH[index] = (byte) (right - left + 1);
        LIGHT_ADVANCE[index] = (byte) (right - left + 2);
        // Bold strokes cover their mapped column and the next one.
        int boldLeft = FULL_COLUMN_POSITIONS[left];
        int boldWidth = FULL_COLUMN_POSITIONS[right] + 2 - boldLeft;
        BOLD_LEFT[index] = (byte) boldLeft;
        BOLD_WIDTH[index] = (byte) boldWidth;
        BOLD_ADVANCE[index] = (byte) (boldWidth + 1);
    }

    /** Arrows, marks and separators drawn as native geometry centred in a whole cell. */
    static boolean symbol(int codePoint) {
        return codePoint >= '\u2190' && codePoint <= '\u2193'
                || codePoint == '\u2713' || codePoint == '\u2717' || codePoint == '\u00b7' || codePoint == '\u2022';
    }

    /** Pen advance for one code point, including the trailing spacing column. */
    static int advance(Face face, int codePoint) {
        if (face == Face.FIXED || symbol(codePoint)) return face.cellWidth;
        int index = normalized(codePoint) - 32;
        return face == Face.LIGHT ? LIGHT_ADVANCE[index] : BOLD_ADVANCE[index];
    }

    /** First atlas column sampled for a normalized character. */
    static int inkLeft(Face face, char character) {
        return switch (face) {
            case FIXED -> 0;
            case LIGHT -> LIGHT_LEFT[character - 32];
            case BOLD -> BOLD_LEFT[character - 32];
        };
    }

    /** Atlas columns sampled for a normalized character; zero for the space. */
    static int inkWidth(Face face, char character) {
        return switch (face) {
            case FIXED -> character == ' ' ? 0 : ADVANCE;
            case LIGHT -> LIGHT_WIDTH[character - 32];
            case BOLD -> BOLD_WIDTH[character - 32];
        };
    }

    static char normalized(int codePoint) {
        return codePoint > Character.MAX_VALUE ? '?' : normalized((char) codePoint);
    }

    static char normalized(char character) {
        return switch (character) {
            case '\u2018', '\u2019' -> '\'';
            case '\u201c', '\u201d' -> '"';
            case '\u2013', '\u2014' -> '-';
            case '\u2190' -> '<';
            case '\u2192' -> '>';
            case '\u2191' -> '^';
            case '\u2193' -> 'v';
            default -> character >= 32 && character <= 126 ? character : '?';
        };
    }

    /** Fixed compact artwork. */
    static int row(char character, int row) {
        return ROWS[normalized(character) - 32][row];
    }

    /** Proportional light artwork, the source of the bold weight. */
    static int proportionalRow(char character, int row) {
        return PROPORTIONAL_ROWS[normalized(character) - 32][row];
    }

    /**
     * Bold lettering keeps the authored rows, spreads the five source columns
     * across seven native positions, and adds one ink pixel to their right.
     * The extra space between inner stems keeps M/m/W/w readable as weight is
     * added. Ink occupies columns zero through seven; column eight is spacing.
     * One top row is inset, and row eight retains lowercase descenders. No
     * interpolation, independently drawn glyphs, outline or shadow is used.
     * Bold is proportional only, so it derives from the proportional masks.
     */
    static int fullRow(char character, int y) {
        if (y == 0 || y == FULL_HEIGHT - 1) return 0;
        int source = proportionalRow(character, y - 1);
        int expanded = 0;
        for (int x = 0; x < FULL_COLUMN_POSITIONS.length; x++) {
            if ((source & (1 << (4 - x))) != 0)
                expanded |= 3 << (FULL_ADVANCE - 2 - FULL_COLUMN_POSITIONS[x]);
        }
        return expanded;
    }

    /** Bottom-left-origin, opaque white fixed glyphs on transparent cells for GL upload. */
    static byte[] atlasRgba() {
        return lightAtlasRgba(ROWS);
    }

    /** The proportional light masks in the same cell layout, with the same solid texel. */
    static byte[] proportionalAtlasRgba() {
        return lightAtlasRgba(PROPORTIONAL_ROWS);
    }

    private static byte[] lightAtlasRgba(byte[][] rows) {
        byte[] rgba = atlasRgba(rows, ADVANCE, HEIGHT, false);
        int solidOffset = ((ATLAS_HEIGHT - 1 - SOLID_Y) * ATLAS_WIDTH + SOLID_X) * 4;
        whitePixel(rgba, solidOffset);
        return rgba;
    }

    static byte[] fullAtlasRgba() {
        return atlasRgba(PROPORTIONAL_ROWS, FULL_ADVANCE, FULL_HEIGHT, true);
    }

    private static byte[] atlasRgba(byte[][] rows, int advance, int height, boolean full) {
        int width = COLUMNS * advance;
        int atlasHeight = 6 * height;
        int inkColumns = full ? FULL_ADVANCE : 5;
        byte[] rgba = new byte[width * atlasHeight * 4];
        for (int index = 0; index < rows.length; index++) {
            int originX = (index % COLUMNS) * advance;
            int originY = (index / COLUMNS) * height;
            for (int y = 0; y < height; y++) {
                int mask = full ? fullRow((char) (index + 32), y) : rows[index][y];
                for (int x = 0; x < inkColumns; x++) {
                    if ((mask & (1 << (inkColumns - 1 - x))) == 0) continue;
                    int offset = ((atlasHeight - 1 - originY - y) * width + originX + x) * 4;
                    whitePixel(rgba, offset);
                }
            }
        }
        return rgba;
    }

    private static void whitePixel(byte[] rgba, int offset) {
        for (int channel = 0; channel < 4; channel++) rgba[offset + channel] = (byte) 255;
    }
}
