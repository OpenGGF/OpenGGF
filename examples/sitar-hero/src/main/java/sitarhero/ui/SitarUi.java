package sitarhero.ui;

import com.openggf.mods.scene.SceneCanvas;
import java.util.Locale;

/** Creator-side pixel UI: master-title framing and the example golf mod's 5x7 glyph design. */
public final class SitarUi {
    public static final int GOLD = 0xFFFFD45B, CREAM = 0xFFFFF3CB, CYAN = 0xFF69DFFF;
    public static final int DIM = 0xFFA6BEDF, INK = 0xFF08132D;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String GLYPHS = "0E11111F1111111E11111E11111E0F10101010100F1E11111111111E1F10101E10101F1F10101E1010100F10101311110F1111111F1111111F04040404041F0702020212120C111214181412111010101010101F111B1515111111111915131111110E11111111110E1E11111E1010100E11111115120D1E11111E1412110F10100E01011E1F0404040404041111111111110E11111111110A0411111115151B1111110A040A111111110A040404041F01020408101F0E11131519110E040C040404040E0E11010204081F1E01010E01011E02060A121F02021F10101E01011E0E10101E11110E1F0102040808080E11110E11110E0E11110F01010E";
    private static final String PUNCTUATION = ".:-_/+=><!?%[]()*,'&;";
    private static final String PUNCTUATION_GLYPHS = "00000000000004000400000400000000001F0000000000000000001F010102040810100004041F04040000001F001F00001008040204081001020408040201040404040400040E110102040004191A0204080B130E08080808080E0E02020202020E020408080804020804020202040800150E1F0E1500000000000C04080C0408000000000C12140815120D00000400000408";
    private SitarUi() { }

    public static int width(String text, int scale) { return text.isEmpty() ? 0 : text.length() * 6 * scale - scale; }
    public static String fit(String text, int pixels) {
        int count = Math.max(0, (pixels + 1) / 6);
        return text.length() <= count ? text : count <= 3 ? ".".repeat(count) : text.substring(0, count - 3) + "...";
    }
    public static void text(SceneCanvas c, String text, int x, int y, int color) {
        pixels(c, text, x + 1, y + 1, 1, INK); pixels(c, text, x, y, 1, color);
    }
    public static void center(SceneCanvas c, String text, int x, int y, int area, int color) {
        text(c, text, x + (area - width(text, 1)) / 2, y, color);
    }
    public static void label(SceneCanvas c, String text, int x, int y, int area, int color) {
        int count = Math.max(0, area / 10);
        if (text.length() > count) text = count <= 3 ? ".".repeat(count) : text.substring(0, count - 3) + "...";
        c.text(text.toUpperCase(Locale.ROOT), x, y, color);
    }
    public static void wrapped(SceneCanvas c, String text, int x, int y, int area, int color) {
        int count = Math.max(1, (area + 1) / 6); String line = "";
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > count) {
                text(c, line, x, y, color); y += 10; line = "";
            }
            line += (line.isEmpty() ? "" : " ") + word;
        }
        if (!line.isEmpty()) text(c, line, x, y, color);
    }
    public static void frame(SceneCanvas c, int x, int y, int w, int h, int color) {
        c.fill(x, y, w, 1, color); c.fill(x, y + h - 1, w, 1, color);
        c.fill(x, y, 1, h, color); c.fill(x + w - 1, y, 1, h, color);
    }
    public static void panel(SceneCanvas c, int x, int y, int w, int h) {
        c.fill(x + 2, y + 2, w, h, 0x80000618);
        c.fill(x, y, w, h, 0xE611244D); frame(c, x, y, w, h, 0xFF365E95);
        c.fill(x + 1, y + 1, w - 2, 1, 0xFF476AA2);
    }
    public static void choice(SceneCanvas c, String label, int x, int y, int w, int h, boolean selected) {
        if (selected) highlight(c, x, y, w, h);
        choiceLabel(c, label, x, y, w, h, selected);
    }
    /** The selection bar on its own, so a list can glide it between rows. */
    public static void highlight(SceneCanvas c, int x, int y, int w, int h) {
        c.fill(x, y, w, h, 0xFF173B6C); frame(c, x, y, w, h, CYAN);
        c.fill(x + 1, y + 1, 3, h - 2, GOLD);
    }
    /** A row's marker and label without its bar. */
    public static void choiceLabel(SceneCanvas c, String label, int x, int y, int w, int h, boolean selected) {
        if (selected) text(c, ">", x + 9, y + (h - 7) / 2, GOLD);
        label(c, label, x + 21, y + (h - 10) / 2, w - 28, selected ? GOLD : CREAM);
    }
    public static void page(SceneCanvas c, int step) {
        page(c, step, "ARCADE");
    }
    public static void page(SceneCanvas c, int step, String section) {
        // A light veil: panels carry the text, so the ROM venue can stay visible around them.
        c.fill(0, 0, c.width(), c.height(), 0x78000D28);
        header(c, step, section);
    }
    /** The top bar alone: logo, then the four selection steps or a section name. */
    public static void header(SceneCanvas c, int step, String section) {
        c.fill(0, 0, c.width(), 35, 0xF00B2457);
        c.fill(0, 34, c.width(), 1, GOLD);
        logo(c, 12, 9);
        if (step > 0) {
            for (int i = 1; i <= 4; i++) {
                int x = c.width() - 196 + (i - 1) * 47;
                c.fill(x, 10, 41, 16, i == step ? 0xFF173B6C : 0xFF0A1939);
                frame(c, x, 10, 41, 16, i == step ? CYAN : 0xFF365E95);
                center(c, i == 1 ? "ACTOR" : i == 2 ? "ROLE" : i == 3 ? "LEVEL" : "SONG", x, 15, 41, i == step ? GOLD : DIM);
            }
        } else text(c, section, c.width() - 49, 15, CYAN);
    }
    public static void footer(SceneCanvas c, String first, String second) {
        c.fill(0, 198, c.width(), 26, 0xF5030B22); c.fill(0, 198, c.width(), 1, 0xFF365E95);
        text(c, fit(first, c.width() - 24), 12, 202, CYAN);
        text(c, fit(second, c.width() - 24), 12, 213, CREAM);
    }
    public static void logo(SceneCanvas c, int x, int y) { logo(c, x, y, 2); }
    /** The two-tone outlined wordmark at an integer pixel scale. */
    public static void logo(SceneCanvas c, int x, int y, int scale) {
        String text = "SITAR HERO";
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) pixels(c, text, x + dx, y + dy, scale, INK);
        pixels(c, text, x + 1, y + scale, scale, 0xFF941B2F);
        pixels(c, text, x, y, scale, 0xFFFF7840);
        c.clip(x, y, width(text, scale), 3 * scale + scale / 2); pixels(c, text, x, y, scale, GOLD); c.unclip();
    }
    /** Large outlined text centred on {@code centreX}, for counts and grades. */
    public static void big(SceneCanvas c, String text, int centreX, int y, int scale, int color) {
        int x = centreX - width(text, scale) / 2;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) pixels(c, text, x + dx, y + dy, scale, INK);
        pixels(c, text, x, y, scale, color);
    }
    /** A 9x9 pixel star: filled when earned, an outline when not. */
    public static void star(SceneCanvas c, int x, int y, boolean earned) {
        int fill = earned ? GOLD : 0xFF233A63, edge = earned ? 0xFFFFF3CB : DIM;
        c.fill(x + 4, y, 1, 2, edge); c.fill(x + 3, y + 2, 3, 1, fill);
        c.fill(x, y + 3, 9, 2, fill); c.fill(x + 1, y + 5, 7, 1, fill);
        c.fill(x + 2, y + 6, 5, 1, fill); c.fill(x + 1, y + 7, 2, 2, fill); c.fill(x + 6, y + 7, 2, 2, fill);
        c.fill(x, y + 3, 9, 1, edge);
    }
    private static void pixels(SceneCanvas c, String text, int x, int y, int scale, int color) {
        for (int letter = 0; letter < text.length(); letter++) {
            char ch = Character.toUpperCase(text.charAt(letter)); if (ch == ' ') continue;
            int index = ALPHABET.indexOf(ch);
            String glyph;
            if (index >= 0) glyph = GLYPHS.substring(index * 14, index * 14 + 14);
            else {
                int punctuation = PUNCTUATION.indexOf(ch);
                if (punctuation < 0) punctuation = PUNCTUATION.indexOf('?');
                glyph = PUNCTUATION_GLYPHS.substring(punctuation * 14, punctuation * 14 + 14);
            }
            for (int row = 0; row < 7; row++) {
                int bits = Integer.parseInt(glyph.substring(row * 2, row * 2 + 2), 16);
                for (int col = 0; col < 5; col++) {
                    if ((bits & (16 >> col)) == 0) continue;
                    int start = col;
                    while (col + 1 < 5 && (bits & (16 >> (col + 1))) != 0) col++;
                    c.fill(x + letter * 6 * scale + start * scale, y + row * scale, (col - start + 1) * scale, scale, color);
                }
            }
        }
    }
}
