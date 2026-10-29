package com.openggf.graphics;

/**
 * Original, hand-authored five-column menu lettering. Each hexadecimal pair is
 * one top-to-bottom row of five pixels, most-significant bit at the left.
 * These masks are native glyph artwork, not samples of the nine-pixel font.
 * Capitals end on row six; lowercase descenders may use row seven within the
 * same eight-pixel cell. The sixth column remains transparent spacing.
 *
 * <p>The proportional light and bold faces use ink-width advances, narrow lowercase masks
 * and tabular digits; the fixed compact face preserves its original 6x8 artwork and cells.
 *
 * <p>Beyond printable ASCII, the same grid carries authored Cyrillic and Latin
 * letters plus accented Latin composed from a base letter and two-row marks:
 * lowercase marks use the empty rows above the x-height, while capitals take a
 * five-row form so their mark still fits the eight-pixel cell (the classic 5x7
 * LCD treatment). Marks below use row seven. A letter with two marks above, or
 * a mark over an ascender, is not native; {@code HostText} falls back to a
 * bundled font for it rather than drawing a substitute. Host text reads the inventory
 * through the public lookups; atlas construction stays with {@link MenuPixelFont}.
 */
public final class MenuGlyphs {
    public static final int ADVANCE = 6;
    public static final int HEIGHT = 8;
    public static final int FULL_ADVANCE = 9;
    public static final int FULL_HEIGHT = 10;
    /** Baseline rows from the top of the compact and full cells. */
    public static final int BASELINE = 7;
    public static final int FULL_BASELINE = 8;
    static final int COLUMNS = 16;
    /** Atlas cell 95 follows the printable ASCII glyphs and holds the solid texel. */
    static final int SOLID_INDEX = 95;
    private static final int FIRST_EXTENDED_INDEX = 96;
    private static final int[] FULL_COLUMN_POSITIONS = {0, 1, 3, 5, 6};
    private static final byte[][] ROWS = new byte[95][];
    private static final java.util.Map<Integer, byte[]> AUTHORED = new java.util.HashMap<>();
    /** Five-row capitals that make room for a mark above; derived unless authored here. */
    private static final java.util.Map<Integer, byte[]> SHORT_CAPITALS = new java.util.HashMap<>();
    private static final int[] EXTENDED;
    private static final byte[][] EXTENDED_ROWS;

    static final int LIGHT_SPACE_ADVANCE = 3;
    static final int BOLD_SPACE_ADVANCE = 4;
    private static final byte[][] PROPORTIONAL_ROWS = new byte[95][];
    private static final byte[] LIGHT_LEFT = new byte[95], LIGHT_WIDTH = new byte[95], LIGHT_ADVANCE = new byte[95];
    private static final byte[] BOLD_LEFT = new byte[95], BOLD_WIDTH = new byte[95], BOLD_ADVANCE = new byte[95];

    /** Native lettering faces; every metric is in unscaled native pixels. */
    public enum Face {
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

    static {
        // Latin letters whose form is not a base plus marks.
        authored(0x00DF, "0C121216111116");   // ß
        authored(0x00E6, "00001A051F141B");   // æ
        authored(0x00C6, "0F14141F141417");   // Æ
        authored(0x0153, "00001A1517141B");   // œ
        authored(0x0152, "0F14141714140F");   // Œ
        authored(0x00F8, "00000F1315191E");   // ø
        authored(0x00D8, "0F13131519191E");   // Ø
        authored(0x0142, "0C04060C04040E");   // ł
        authored(0x0141, "08080A0C18080F");   // Ł
        authored(0x0111, "01070D1311110F");   // đ
        authored(0x0110, "0E09091D09090E");   // Đ
        authored(0x0131, "00000C0404040E");   // ı
        authored(0x0237, "000006020202120C"); // ȷ
        // Punctuation and signs used by Western and Central European text.
        authored(0x00BF, "0400040810110E");   // ¿
        authored(0x00A1, "04000404040404");   // ¡
        authored(0x00AB, "00050A140A0500");   // «
        authored(0x00BB, "00140A050A1400");   // »
        authored(0x2039, "00020408040200");   // ‹
        authored(0x203A, "00080402040800");   // ›
        authored(0x201E, "00000000000A0A14"); // „
        authored(0x201A, "0000000000040408"); // ‚
        authored(0x2026, "00000000000015");   // …
        authored(0x00B0, "0C12120C000000");   // °
        authored(0x20AC, "07081E081E0807");   // €
        authored(0x00A3, "0609081C08081F");   // £
        authored(0x00A2, "040E1514150E04");   // ¢
        authored(0x00A5, "110A1F041F0404");   // ¥
        authored(0x00A7, "0E100E110E010E");   // §
        authored(0x00B1, "0404041F04001F");   // ±
        authored(0x00D7, "00110A040A1100");   // ×
        authored(0x00F7, "0004001F000400");   // ÷
        authored(0x00B5, "0000111111131D10"); // µ
        // Cyrillic capitals; forms identical to Latin capitals reuse that artwork.
        alias(0x0410, 'A'); alias(0x0412, 'B'); alias(0x0415, 'E'); alias(0x041A, 'K');
        alias(0x041C, 'M'); alias(0x041D, 'H'); alias(0x041E, 'O'); alias(0x0420, 'P');
        alias(0x0421, 'C'); alias(0x0422, 'T'); alias(0x0425, 'X'); alias(0x0406, 'I');
        alias(0x0408, 'J'); alias(0x0405, 'S');
        authored(0x0411, "1F10101E11111E");   // Б
        authored(0x0413, "1F101010101010");   // Г
        authored(0x0490, "011F1010101010");   // Ґ
        authored(0x0414, "060A0A0A0A1F11");   // Д
        authored(0x0416, "1515150E151515");   // Ж
        authored(0x0417, "0E11010601110E");   // З
        authored(0x0418, "11111315191111");   // И
        authored(0x041B, "07090909090911");   // Л
        authored(0x041F, "1F111111111111");   // П
        authored(0x0423, "1111110F01110E");   // У
        authored(0x0424, "040E1515150E04");   // Ф
        authored(0x0426, "1212121212121F01"); // Ц
        authored(0x0427, "1111110F010101");   // Ч
        authored(0x0428, "1515151515151F");   // Ш
        authored(0x0429, "1515151515151F01"); // Щ
        authored(0x042A, "1808080E09090E");   // Ъ
        authored(0x042B, "11111119151519");   // Ы
        authored(0x042C, "1010101E11111E");   // Ь
        authored(0x042D, "0E11010701110E");   // Э
        authored(0x042E, "1215151D151512");   // Ю
        authored(0x042F, "0F11110F050911");   // Я
        authored(0x0404, "0E11101C10110E");   // Є
        // Cyrillic lowercase.
        alias(0x0430, 'a'); alias(0x0435, 'e'); alias(0x043E, 'o'); alias(0x0440, 'p');
        alias(0x0441, 'c'); alias(0x0443, 'y'); alias(0x0445, 'x'); alias(0x0456, 'i');
        alias(0x0458, 'j'); alias(0x0455, 's');
        authored(0x0431, "030C101E11110E");   // б
        authored(0x0432, "00001E111E111E");   // в
        authored(0x0433, "00001F10101010");   // г
        authored(0x0491, "00011F10101010");   // ґ
        authored(0x0434, "0000060A0A0A1F11"); // д
        authored(0x0436, "000015150E1515");   // ж
        authored(0x0437, "00000E1106110E");   // з
        authored(0x0438, "00001113151911");   // и
        authored(0x043A, "000011121C1211");   // к
        authored(0x043B, "00000709090911");   // л
        authored(0x043C, "0000111B151111");   // м
        authored(0x043D, "000011111F1111");   // н
        authored(0x043F, "00001F11111111");   // п
        authored(0x0442, "00001F04040404");   // т
        authored(0x0444, "00040E1515150E04"); // ф
        authored(0x0446, "0000121212121F01"); // ц
        authored(0x0447, "000011110F0101");   // ч
        authored(0x0448, "0000151515151F");   // ш
        authored(0x0449, "0000151515151F01"); // щ
        authored(0x044A, "000018080E090E");   // ъ
        authored(0x044B, "00001111191519");   // ы
        authored(0x044C, "000010101E111E");   // ь
        authored(0x044D, "00000E0107010E");   // э
        authored(0x044E, "000012151D1512");   // ю
        authored(0x044F, "00000F110F0509");   // я
        authored(0x0454, "00000E101C100E");   // є
        // Capitals whose duplicate-row reduction would lose their shape.
        shortCapital('K', "1112141A11");
        shortCapital('R', "1E111E1211");
        shortCapital('Z', "1F0204081F");
        EXTENDED = java.util.stream.IntStream.concat(
                        java.util.stream.IntStream.rangeClosed(0x00A0, 0x024F),
                        java.util.stream.IntStream.concat(
                                java.util.stream.IntStream.rangeClosed(0x0400, 0x04FF),
                                java.util.stream.IntStream.rangeClosed(0x2000, 0x20CF)))
                .filter(codePoint -> index(codePoint, false) < 0 && resolve(codePoint) != null)
                .toArray();
        EXTENDED_ROWS = new byte[EXTENDED.length][];
        for (int i = 0; i < EXTENDED.length; i++) EXTENDED_ROWS[i] = resolve(EXTENDED[i]);
    }

    static final int GLYPH_COUNT = FIRST_EXTENDED_INDEX + EXTENDED.length;
    static final int ATLAS_ROWS = (GLYPH_COUNT + COLUMNS - 1) / COLUMNS;
    static final int ATLAS_WIDTH = COLUMNS * ADVANCE;
    static final int ATLAS_HEIGHT = ATLAS_ROWS * HEIGHT;
    static final int FULL_ATLAS_WIDTH = COLUMNS * FULL_ADVANCE;
    static final int FULL_ATLAS_HEIGHT = ATLAS_ROWS * FULL_HEIGHT;
    static final int SOLID_X = SOLID_INDEX % COLUMNS * ADVANCE;
    static final int SOLID_Y = SOLID_INDEX / COLUMNS * HEIGHT;
    static final float SOLID_U = (SOLID_X + .5f) / ATLAS_WIDTH;
    static final float SOLID_V = 1f - (SOLID_Y + .5f) / ATLAS_HEIGHT;

    private MenuGlyphs() { }

    private static void glyph(char character, String hexadecimalRows) {
        ROWS[character - 32] = parse(hexadecimalRows);
    }

    private static void authored(int codePoint, String hexadecimalRows) {
        AUTHORED.put(codePoint, parse(hexadecimalRows));
    }

    private static void alias(int codePoint, char ascii) {
        AUTHORED.put(codePoint, ROWS[ascii - 32]);
    }

    private static void shortCapital(int codePoint, String hexadecimalRows) {
        if (hexadecimalRows.length() != 10) throw new IllegalArgumentException("Five capital rows required");
        byte[] rows = new byte[5];
        for (int i = 0; i < 5; i++) rows[i] = (byte) Integer.parseInt(hexadecimalRows.substring(i * 2, i * 2 + 2), 16);
        SHORT_CAPITALS.put(codePoint, rows);
    }

    private static byte[] parse(String hexadecimalRows) {
        if (hexadecimalRows.length() != 14 && hexadecimalRows.length() != 16) {
            throw new IllegalArgumentException("Seven or eight glyph rows required");
        }
        byte[] rows = new byte[HEIGHT];
        for (int i = 0; i < hexadecimalRows.length() / 2; i++) {
            rows[i] = (byte) Integer.parseInt(hexadecimalRows.substring(i * 2, i * 2 + 2), 16);
            if ((rows[i] & ~31) != 0) throw new IllegalArgumentException("Five glyph columns required");
        }
        return rows;
    }

    /** Pen advance for one code point, including the trailing spacing column. */
    public static int advance(Face face, int codePoint) {
        if (face == Face.FIXED || symbol(codePoint)) return face.cellWidth;
        int index = index(codePoint);
        if (index < 0) index = index('?');
        return advanceIndex(face, index);
    }

    static int inkLeft(Face face, char character) { return inkLeftIndex(face, character - 32); }

    static int inkWidth(Face face, char character) { return inkWidthIndex(face, character - 32); }

    /** Unscaled pen advance of an atlas cell, including its trailing spacing column. */
    public static int advanceIndex(Face face, int index) {
        if (face == Face.FIXED) return ADVANCE;
        if (index < 95) return face == Face.BOLD ? BOLD_ADVANCE[index] : LIGHT_ADVANCE[index];
        return inkWidthIndex(face, index) + 1;
    }

    /** First sampled column of the native atlas cell. */
    public static int inkLeftIndex(Face face, int index) {
        if (face == Face.FIXED) return 0;
        if (index < 95) return face == Face.BOLD ? BOLD_LEFT[index] : LIGHT_LEFT[index];
        int ink = 0;
        for (byte row : proportionalRows(index)) ink |= row;
        if (ink == 0) return 0;
        int left = Integer.numberOfLeadingZeros(ink) - 27;
        return face == Face.BOLD ? FULL_COLUMN_POSITIONS[left] : left;
    }

    /** Native atlas columns sampled for a glyph, zero for a blank cell. */
    public static int inkWidthIndex(Face face, int index) {
        if (index == 0) return 0;
        if (face == Face.FIXED) return ADVANCE;
        if (index < 95) return face == Face.BOLD ? BOLD_WIDTH[index] : LIGHT_WIDTH[index];
        int ink = 0;
        for (byte row : proportionalRows(index)) ink |= row;
        if (ink == 0) return 0;
        int left = Integer.numberOfLeadingZeros(ink) - 27;
        int right = 4 - Integer.numberOfTrailingZeros(ink);
        return face == Face.BOLD ? FULL_COLUMN_POSITIONS[right] + 2 - FULL_COLUMN_POSITIONS[left] : right - left + 1;
    }

    static char normalized(char character) {
        return switch (character) {
            case '‘', '’' -> '\'';
            case '“', '”' -> '"';
            case '–', '—' -> '-';
            case '←' -> '<';
            case '→' -> '>';
            case '↑' -> '^';
            case '↓' -> 'v';
            default -> character >= 32 && character <= 126 ? character : '?';
        };
    }

    static int row(char character, int row) {
        return ROWS[normalized(character) - 32][row];
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

    /** Native symbols drawn as solid-texel geometry rather than atlas cells. */
    public static boolean symbol(int codePoint) {
        return codePoint >= 0x2190 && codePoint <= 0x2193 || codePoint == 0x2713 || codePoint == 0x2717
                || codePoint == 0x00B7 || codePoint == 0x2022;
    }

    /** Spacing characters drawn as an empty native cell. */
    public static boolean blank(int codePoint) {
        return codePoint == ' ' || codePoint == 0x00A0 || codePoint == 0x202F
                || codePoint >= 0x2000 && codePoint <= 0x200A;
    }

    /**
     * Atlas cell for a single native codepoint, or -1. Typographic single/double
     * quotes and dashes keep their established ASCII artwork; symbols have no cell.
     */
    public static int index(int codePoint) {
        return index(codePoint, true);
    }

    private static int index(int codePoint, boolean extended) {
        if (codePoint >= 32 && codePoint <= 126) return codePoint - 32;
        if (blank(codePoint)) return 0;
        if (symbol(codePoint)) return -1;
        if (codePoint <= 0xFFFF) {
            char ascii = normalized((char) codePoint);
            if (ascii != '?') return ascii - 32;
        }
        if (!extended) return -1;
        int found = java.util.Arrays.binarySearch(EXTENDED, codePoint);
        return found >= 0 ? FIRST_EXTENDED_INDEX + found : -1;
    }

    /** Eight source rows for an atlas cell; the solid cell and unknown cells have none. */
    public static byte[] rows(int index) {
        if (index < 0 || index >= GLYPH_COUNT || index == SOLID_INDEX) return new byte[HEIGHT];
        return (index < ROWS.length ? ROWS[index] : EXTENDED_ROWS[index - FIRST_EXTENDED_INDEX]).clone();
    }

    /** Proportional artwork of a native atlas cell. */
    public static byte[] proportionalRows(int index) {
        return index >= 0 && index < PROPORTIONAL_ROWS.length ? PROPORTIONAL_ROWS[index].clone() : rows(index);
    }

    static int proportionalRow(char character, int row) {
        return PROPORTIONAL_ROWS[normalized(character) - 32][row];
    }

    /** Authored artwork or a base-plus-marks composition for {@code codePoint}, or null. */
    private static byte[] resolve(int codePoint) {
        byte[] authored = AUTHORED.get(codePoint);
        if (authored != null) return authored;
        String decomposed = java.text.Normalizer.normalize(new String(Character.toChars(codePoint)),
                java.text.Normalizer.Form.NFD);
        if (decomposed.codePointCount(0, decomposed.length()) < 2) return null;
        return compose(decomposed);
    }

    /**
     * Composes one base letter with at most one mark above and one mark below,
     * or returns null when the result would not be faithful on this grid.
     */
    public static byte[] compose(String decomposed) {
        int base = decomposed.codePointAt(0);
        int above = 0;
        int below = 0;
        for (int i = Character.charCount(base); i < decomposed.length(); ) {
            int mark = decomposed.codePointAt(i);
            i += Character.charCount(mark);
            if (markAbove(mark) != null) {
                if (above != 0) return null;
                above = mark;
            } else if (markBelow(mark) != 0) {
                if (below != 0) return null;
                below = mark;
            } else return null;
        }
        if (above == 0 && below == 0) return null;
        byte[] baseRows = baseRows(base, above != 0);
        if (baseRows == null) return null;
        byte[] rows = baseRows.clone();
        if (above != 0) {
            if (rows[0] != 0 || rows[1] != 0) return null;
            byte[] mark = markAbove(above);
            rows[0] = mark[0];
            rows[1] = mark[1];
        }
        if (below != 0) {
            if (rows[7] != 0) return null;
            rows[7] = (byte) markBelow(below);
        }
        return rows;
    }

    private static byte[] baseRows(int base, boolean markAbove) {
        if (markAbove) {
            // Soft-dotted letters lose their dot under a mark above.
            if (base == 'i' || base == 0x0456) base = 0x0131;
            else if (base == 'j' || base == 0x0458) base = 0x0237;
        }
        byte[] rows = base >= 32 && base <= 126 ? ROWS[base - 32] : AUTHORED.get(base);
        if (rows == null || !markAbove || rows[0] == 0 && rows[1] == 0) return rows;
        // Only capitals shorten for a mark; an ascender lowercase letter keeps its form.
        return Character.isUpperCase(base) ? shortCapital(base, rows) : null;
    }

    /** A capital reduced to rows two through six, keeping its first and last rows. */
    private static byte[] shortCapital(int base, byte[] rows) {
        if (rows[7] != 0) return null;
        byte[] authored = SHORT_CAPITALS.get(base);
        if (authored == null) {
            for (int letter = 'A'; letter <= 'Z' && authored == null; letter++) {
                if (ROWS[letter - 32] == rows) authored = SHORT_CAPITALS.get(letter);
            }
        }
        java.util.List<Byte> kept = new java.util.ArrayList<>();
        if (authored != null) {
            for (byte row : authored) kept.add(row);
        } else {
            for (int i = 0; i < 7; i++) kept.add(rows[i]);
            while (kept.size() > 5) {
                int remove = -1;
                for (int i = 1; i < kept.size(); i++) {
                    if (!kept.get(i).equals(kept.get(i - 1))) continue;
                    if (remove < 0 || Math.abs(i - 3) < Math.abs(remove - 3)) remove = i;
                }
                if (remove < 0) return null;
                kept.remove(remove);
            }
        }
        byte[] shortened = new byte[HEIGHT];
        for (int i = 0; i < 5; i++) shortened[i + 2] = kept.get(i);
        return shortened;
    }

    /** Two rows above the x-height for the combining marks this grid draws faithfully. */
    private static byte[] markAbove(int mark) {
        return switch (mark) {
            case 0x0300 -> new byte[] {0x08, 0x04}; // grave
            case 0x0301 -> new byte[] {0x02, 0x04}; // acute
            case 0x0302 -> new byte[] {0x04, 0x0A}; // circumflex
            case 0x0303 -> new byte[] {0x0D, 0x12}; // tilde
            case 0x0304 -> new byte[] {0x0E, 0x00}; // macron
            case 0x0306 -> new byte[] {0x11, 0x0E}; // breve
            case 0x0307 -> new byte[] {0x04, 0x00}; // dot above
            case 0x0308 -> new byte[] {0x0A, 0x00}; // diaeresis
            case 0x030A -> new byte[] {0x0E, 0x0A}; // ring above
            case 0x030B -> new byte[] {0x05, 0x0A}; // double acute
            case 0x030C -> new byte[] {0x0A, 0x04}; // caron
            default -> null;
        };
    }

    /** Row seven for combining marks below the baseline, or zero. */
    private static int markBelow(int mark) {
        return switch (mark) {
            case 0x0327 -> 0x04; // cedilla
            case 0x0328 -> 0x02; // ogonek
            case 0x0326, 0x0323 -> 0x04; // comma below, dot below
            default -> 0;
        };
    }

    /**
     * Bold lettering keeps the authored rows, spreads the five source columns
     * across seven native positions, and adds one ink pixel to their right.
     * The extra space between inner stems keeps M/m/W/w readable as weight is
     * added. Ink occupies columns zero through seven; column eight is spacing.
     * One top row is inset, and row eight retains lowercase descenders. No
     * interpolation, independently drawn glyphs, outline or shadow is used.
     */
    static int fullRow(char character, int y) {
        return fullRow(PROPORTIONAL_ROWS[normalized(character) - 32], y);
    }

    public static int fullRow(byte[] rows, int y) {
        if (y == 0 || y == FULL_HEIGHT - 1) return 0;
        int source = rows[y - 1];
        int expanded = 0;
        for (int x = 0; x < FULL_COLUMN_POSITIONS.length; x++) {
            if ((source & (1 << (4 - x))) != 0)
                expanded |= 3 << (FULL_ADVANCE - 2 - FULL_COLUMN_POSITIONS[x]);
        }
        return expanded;
    }

    /** Bottom-left-origin, opaque white glyphs on transparent cells for GL upload. */
    static byte[] atlasRgba() {
        byte[] rgba = atlasRgba(ADVANCE, HEIGHT, false, false);
        int solidOffset = ((ATLAS_HEIGHT - 1 - SOLID_Y) * ATLAS_WIDTH + SOLID_X) * 4;
        whitePixel(rgba, solidOffset);
        return rgba;
    }

    static byte[] proportionalAtlasRgba() {
        byte[] rgba = atlasRgba(ADVANCE, HEIGHT, false, true);
        whitePixel(rgba, ((ATLAS_HEIGHT - 1 - SOLID_Y) * ATLAS_WIDTH + SOLID_X) * 4);
        return rgba;
    }

    static byte[] fullAtlasRgba() {
        return atlasRgba(FULL_ADVANCE, FULL_HEIGHT, true, true);
    }

    private static byte[] atlasRgba(int advance, int height, boolean full, boolean proportional) {
        int width = COLUMNS * advance;
        int atlasHeight = ATLAS_ROWS * height;
        int inkColumns = full ? FULL_ADVANCE : 5;
        byte[] rgba = new byte[width * atlasHeight * 4];
        for (int index = 0; index < GLYPH_COUNT; index++) {
            byte[] rows = proportional ? proportionalRows(index) : rows(index);
            int originX = (index % COLUMNS) * advance;
            int originY = (index / COLUMNS) * height;
            for (int y = 0; y < height; y++) {
                int mask = full ? fullRow(rows, y) : rows[y];
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
