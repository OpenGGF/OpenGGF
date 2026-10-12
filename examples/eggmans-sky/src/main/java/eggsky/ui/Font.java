package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;

/**
 * A compact 5x7 pixel font with a baked black outline, built in code into one atlas image
 * (white glyphs, so a draw tint colours them). Used for the HUD, menus and, scaled up with
 * {@link #drawBig}, for banners and the title logo. Lower case is drawn as small capitals.
 */
public final class Font {
    public static final int ADVANCE = 6;
    public static final int HEIGHT = 7;
    private static final String CHARS =
            " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,:;!?'\"-+/()%[]<>=*#&_$@^~|";
    private static final int CELL_W = 7;
    private static final int CELL_H = 9;
    private final SceneImage atlas;
    /** Plain glyphs without the outline (for big gradient text). */
    private final SceneImage bare;

    public Font() {
        int count = CHARS.length();
        int[] px = new int[count * CELL_W * CELL_H];
        int[] plain = new int[count * CELL_W * CELL_H];
        int w = count * CELL_W;
        for (int g = 0; g < count; g++) {
            int[] rows = glyph(CHARS.charAt(g));
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 5; x++) {
                    if ((rows[y] >> (4 - x) & 1) != 0) {
                        int ox = g * CELL_W + x + 1;
                        int oy = y + 1;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int i = (oy + dy) * w + ox + dx;
                                if (px[i] == 0) {
                                    px[i] = 0xFF000000;
                                }
                            }
                        }
                    }
                }
            }
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 5; x++) {
                    if ((rows[y] >> (4 - x) & 1) != 0) {
                        px[(y + 1) * w + g * CELL_W + x + 1] = 0xFFFFFFFF;
                        plain[(y + 1) * w + g * CELL_W + x + 1] = 0xFFFFFFFF;
                    }
                }
            }
        }
        atlas = new SceneImage(w, CELL_H, px);
        bare = new SceneImage(w, CELL_H, plain);
    }

    public static int width(String text) {
        return text == null || text.isEmpty() ? 0 : text.length() * ADVANCE - 1;
    }

    public void draw(SceneCanvas c, String text, int x, int y, int colour) {
        draw(c, text, x, y, colour, 1);
    }

    /** Text at {@code scale}, top-left at (x, y), outlined. */
    public void draw(SceneCanvas c, String text, float x, float y, int colour, float scale) {
        if (text == null) {
            return;
        }
        SceneDraw style = SceneDraw.plain().withScale(scale).withTint(colour);
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            int g = index(text.charAt(i));
            if (g > 0) {
                c.drawRegion(atlas, g * CELL_W, 0, CELL_W, CELL_H, cx - scale, y - scale, CELL_W * scale,
                        CELL_H * scale, style);
            }
            cx += ADVANCE * scale;
        }
    }

    public void centre(SceneCanvas c, String text, int cx, int y, int colour) {
        draw(c, text, cx - width(text) / 2, y, colour, 1);
    }

    public void right(SceneCanvas c, String text, int rx, int y, int colour) {
        draw(c, text, rx - width(text), y, colour, 1);
    }

    /**
     * Big banner text centred on {@code cx}: a dark drop shadow, then the glyphs in a vertical
     * gradient from {@code top} to {@code bottom}, drawn as horizontal slices.
     */
    public void drawBig(SceneCanvas c, String text, float cx, float y, int scale, int top, int bottom, int alpha) {
        float w = width(text) * scale;
        float x0 = cx - w / 2;
        int dark = (alpha << 24) | 0x080418;
        // Large text is drawn bold: each glyph twice, a fraction of a pixel apart.
        float bold = scale >= 4 ? scale * 0.4f : 0;
        float drop = Math.max(1, scale / 3f);
        for (int pass = 0; pass < 3; pass++) {
            float cxp = x0;
            for (int i = 0; i < text.length(); i++) {
                int g = index(text.charAt(i));
                if (g > 0) {
                    if (pass == 0) {
                        // Drop shadow.
                        c.drawRegion(bare, g * CELL_W + 1, 1, 5, 7, cxp + drop, y + drop, 5 * scale + bold, 7 * scale,
                                SceneDraw.plain().withTint(dark));
                    } else if (pass == 1) {
                        // A one-pixel outline all round.
                        for (int k = 0; k < 4; k++) {
                            float ox = k == 0 ? -1 : k == 1 ? 1 : 0;
                            float oy = k == 2 ? -1 : k == 3 ? 1 : 0;
                            c.drawRegion(bare, g * CELL_W + 1, 1, 5, 7, cxp + ox, y + oy, 5 * scale + bold, 7 * scale,
                                    SceneDraw.plain().withTint(dark));
                        }
                    } else {
                        for (int row = 0; row < 7; row++) {
                            int colour = eggsky.core.Colour.lerp(top, bottom, row / 6f);
                            colour = (alpha << 24) | (colour & 0xFFFFFF);
                            SceneDraw st = SceneDraw.plain().withTint(colour);
                            c.drawRegion(bare, g * CELL_W + 1, 1 + row, 5, 1, cxp, y + row * scale, 5 * scale, scale, st);
                            if (bold > 0) {
                                c.drawRegion(bare, g * CELL_W + 1, 1 + row, 5, 1, cxp + bold, y + row * scale, 5 * scale,
                                        scale, st);
                            }
                        }
                        // A highlight line along the top of each glyph's first row.
                        if (scale >= 3) {
                            c.drawRegion(bare, g * CELL_W + 1, 1, 5, 1, cxp, y, 5 * scale + bold, Math.max(1, scale / 3f),
                                    SceneDraw.plain().withTint((alpha << 24) | 0xFFFFFF));
                        }
                    }
                }
                cxp += ADVANCE * scale;
            }
        }
    }

    private static int index(char ch) {
        char u = Character.toUpperCase(ch);
        int i = CHARS.indexOf(u);
        return Math.max(0, i);
    }

    /** Seven rows of five bits. */
    private static int[] glyph(char ch) {
        String s = switch (ch) {
            case 'A' -> "01110 10001 10001 11111 10001 10001 10001";
            case 'B' -> "11110 10001 10001 11110 10001 10001 11110";
            case 'C' -> "01110 10001 10000 10000 10000 10001 01110";
            case 'D' -> "11110 10001 10001 10001 10001 10001 11110";
            case 'E' -> "11111 10000 10000 11110 10000 10000 11111";
            case 'F' -> "11111 10000 10000 11110 10000 10000 10000";
            case 'G' -> "01110 10001 10000 10111 10001 10001 01111";
            case 'H' -> "10001 10001 10001 11111 10001 10001 10001";
            case 'I' -> "01110 00100 00100 00100 00100 00100 01110";
            case 'J' -> "00111 00010 00010 00010 00010 10010 01100";
            case 'K' -> "10001 10010 10100 11000 10100 10010 10001";
            case 'L' -> "10000 10000 10000 10000 10000 10000 11111";
            case 'M' -> "10001 11011 10101 10101 10001 10001 10001";
            case 'N' -> "10001 10001 11001 10101 10011 10001 10001";
            case 'O' -> "01110 10001 10001 10001 10001 10001 01110";
            case 'P' -> "11110 10001 10001 11110 10000 10000 10000";
            case 'Q' -> "01110 10001 10001 10001 10101 10010 01101";
            case 'R' -> "11110 10001 10001 11110 10100 10010 10001";
            case 'S' -> "01111 10000 10000 01110 00001 00001 11110";
            case 'T' -> "11111 00100 00100 00100 00100 00100 00100";
            case 'U' -> "10001 10001 10001 10001 10001 10001 01110";
            case 'V' -> "10001 10001 10001 10001 10001 01010 00100";
            case 'W' -> "10001 10001 10001 10101 10101 10101 01010";
            case 'X' -> "10001 10001 01010 00100 01010 10001 10001";
            case 'Y' -> "10001 10001 10001 01010 00100 00100 00100";
            case 'Z' -> "11111 00001 00010 00100 01000 10000 11111";
            case '0' -> "01110 10001 10011 10101 11001 10001 01110";
            case '1' -> "00100 01100 00100 00100 00100 00100 01110";
            case '2' -> "01110 10001 00001 00010 00100 01000 11111";
            case '3' -> "11111 00010 00100 00010 00001 10001 01110";
            case '4' -> "00010 00110 01010 10010 11111 00010 00010";
            case '5' -> "11111 10000 11110 00001 00001 10001 01110";
            case '6' -> "00110 01000 10000 11110 10001 10001 01110";
            case '7' -> "11111 00001 00010 00100 01000 01000 01000";
            case '8' -> "01110 10001 10001 01110 10001 10001 01110";
            case '9' -> "01110 10001 10001 01111 00001 00010 01100";
            case '.' -> "00000 00000 00000 00000 00000 01100 01100";
            case ',' -> "00000 00000 00000 00000 01100 00100 01000";
            case ':' -> "00000 01100 01100 00000 01100 01100 00000";
            case ';' -> "00000 01100 01100 00000 01100 00100 01000";
            case '!' -> "00100 00100 00100 00100 00100 00000 00100";
            case '?' -> "01110 10001 00001 00010 00100 00000 00100";
            case '\'' -> "00100 00100 01000 00000 00000 00000 00000";
            case '"' -> "01010 01010 10100 00000 00000 00000 00000";
            case '-' -> "00000 00000 00000 11111 00000 00000 00000";
            case '+' -> "00000 00100 00100 11111 00100 00100 00000";
            case '/' -> "00001 00010 00010 00100 01000 01000 10000";
            case '(' -> "00010 00100 01000 01000 01000 00100 00010";
            case ')' -> "01000 00100 00010 00010 00010 00100 01000";
            case '%' -> "11000 11001 00010 00100 01000 10011 00011";
            case '[' -> "01110 01000 01000 01000 01000 01000 01110";
            case ']' -> "01110 00010 00010 00010 00010 00010 01110";
            case '<' -> "00010 00100 01000 10000 01000 00100 00010";
            case '>' -> "01000 00100 00010 00001 00010 00100 01000";
            case '=' -> "00000 00000 11111 00000 11111 00000 00000";
            case '*' -> "00000 10101 01110 11111 01110 10101 00000";
            case '#' -> "01010 01010 11111 01010 11111 01010 01010";
            case '&' -> "01100 10010 10100 01000 10101 10010 01101";
            case '_' -> "00000 00000 00000 00000 00000 00000 11111";
            case '$' -> "00100 01111 10100 01110 00101 11110 00100";
            case '@' -> "01110 10001 10111 10101 10111 10000 01111";
            case '^' -> "00100 01010 10001 00000 00000 00000 00000";
            case '~' -> "00000 00000 01000 10101 00010 00000 00000";
            case '|' -> "00100 00100 00100 00100 00100 00100 00100";
            default -> "00000 00000 00000 00000 00000 00000 00000";
        };
        String[] parts = s.split(" ");
        int[] rows = new int[7];
        for (int i = 0; i < 7; i++) {
            rows[i] = Integer.parseInt(parts[i], 2);
        }
        return rows;
    }
}
