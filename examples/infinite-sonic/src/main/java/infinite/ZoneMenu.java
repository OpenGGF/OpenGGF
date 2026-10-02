package infinite;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import java.util.List;

/**
 * Title-screen zone picker drawn in the gap between the emblem's ribbon tails (which end
 * near screen Y 178) and the "(C)SEGA 1991" line (Y 200-207, right of centre), with its
 * position pips below that line. Left/right cycles the course zones with wrap-around;
 * the title's Start/confirm then launches act 1 of the shown zone. Code-drawn like
 * {@link TitleWordmark}: a slanted navy banner with chrome edges, a gold italic 5x7 font
 * at 2x, bobbing chevrons that kick when pressed, and a sliding position pip row.
 */
final class ZoneMenu {
    static final int SCALE = 2;
    static final int GLYPH_W = 5, GLYPH_H = 7;
    static final int ADVANCE = (GLYPH_W + 1) * SCALE;
    static final int TEXT_H = GLYPH_H * SCALE;
    /** Rows lean right by one pixel per four rows, a lighter echo of the wordmark's italic. */
    static final int TEXT_LEAN = (TEXT_H - 1) / 4;
    static final int PANEL_Y = 181, PANEL_H = 18, PANEL_HALF = 104;
    /** Banner rows lean like the text, so its ends are parallel slants. */
    static final int PANEL_LEAN = (PANEL_H - 1) / 3;
    static final int TEXT_Y = PANEL_Y + (PANEL_H - TEXT_H) / 2;
    /** Names slide and clip inside the chevrons. */
    static final int TEXT_CLIP = 76, CLIP_FADE = 6;
    static final int ARROW_X = 90, ARROW_SIZE = 5;
    /** Below the copyright line; six pips span 60 px, clear of its left edge at centre + 64. */
    static final int PIP_Y = 212, PIP_SPACING = 12;
    static final int REVEAL_FRAMES = 14, REVEAL_RISE = 18;
    static final int SLIDE_FRAMES = 12, SLIDE_TRAVEL = 56;
    static final int KICK_FRAMES = 8, KICK = 5, BOB_PERIOD = 48;
    /** Sonic 1 sfx_Switch ($CD), the ROM's short button click. */
    static final int SFX_SWITCH = 0xCD;

    private static final String GLYPHS =
            "01110100011000111111100011000110001"   // A
            + "11110100011000111110100011000111110" // B
            + "01111100001000010000100001000001111" // C
            + "11110100011000110001100011000111110" // D
            + "11111100001000011110100001000011111" // E
            + "11111100001000011110100001000010000" // F
            + "01111100001000010011100011000101111" // G
            + "10001100011000111111100011000110001" // H
            + "11111001000010000100001000010011111" // I
            + "00111000100001000010000101001001100" // J
            + "10001100101010011000101001001010001" // K
            + "10000100001000010000100001000011111" // L
            + "10001110111010110101100011000110001" // M
            + "10001110011110110111100111000110001" // N
            + "01110100011000110001100011000101110" // O
            + "11110100011000111110100001000010000" // P
            + "01110100011000110001101011001001101" // Q
            + "11110100011000111110101001001010001" // R
            + "01111100001000001110000010000111110" // S
            + "11111001000010000100001000010000100" // T
            + "10001100011000110001100011000101110" // U
            + "10001100011000110001100010101000100" // V
            + "10001100011000110101101011101110001" // W
            + "10001100010101000100010101000110001" // X
            + "10001100010101000100001000010000100" // Y
            + "11111000010001000100010001000011111"; // Z

    private static final int OUTLINE = 0x101848, PANEL_TOP = 0x22348C, PANEL_BOTTOM = 0x0A1034;
    private static final int CHROME = 0xA8E0FF, CHROME_LOW = 0x3858C0, HORIZON = 0xFFF8C8;
    private static final int PIP = 0x5878C8, PIP_LIT = 0xFFD030;
    /** Gold text gradient, top to bottom, with a pale horizon row through the middle. */
    private final int[][] stops = {{0, 0xFFFFFF}, {5, 0xFFE860}, {9, 0xFFB000}, {13, 0xD86000}};
    private static final int HORIZON_ROW = 7;

    private final List<String> names;
    private final boolean[][][] fills;
    private final boolean[][][] edges;
    private final float[][] textColours;

    private int selected;
    private int previous;
    private int direction;
    private int slide = SLIDE_FRAMES;
    private int kickLeft, kickRight;
    private int frame;
    private int revealed = -1;

    ZoneMenu(List<String> names) {
        this.names = List.copyOf(names);
        fills = new boolean[this.names.size()][][];
        edges = new boolean[this.names.size()][][];
        for (int i = 0; i < fills.length; i++) {
            fills[i] = rasterise(this.names.get(i));
            edges[i] = TitleWordmark.dilate(fills[i], 1);
        }
        textColours = textColours();
    }

    int selected() { return selected; }
    int size() { return names.size(); }

    /** Hides the menu for the next title visit; the chosen zone is remembered. */
    void reset() {
        revealed = -1;
        slide = SLIDE_FRAMES;
        kickLeft = kickRight = 0;
    }

    /** Starts the reveal animation unless it is already running or done. */
    void reveal() {
        if (revealed < 0) revealed = 0;
    }

    /**
     * Advances animation and, when {@code interactive}, reads left/right presses. An early
     * press reveals the menu at once so the choice is never made blind.
     */
    void update(InputHandler input, boolean interactive) {
        frame++;
        if (revealed >= 0 && revealed < REVEAL_FRAMES) revealed++;
        if (slide < SLIDE_FRAMES) slide++;
        if (kickLeft > 0) kickLeft--;
        if (kickRight > 0) kickRight--;
        if (!interactive || input == null || names.size() < 2) return;
        var logical = input.logical();
        int step = (logical.menuRight() ? 1 : 0) - (logical.menuLeft() ? 1 : 0);
        if (step == 0) return;
        revealed = REVEAL_FRAMES;
        select(Math.floorMod(selected + step, names.size()), step);
        GameServices.audio().playSfx(SFX_SWITCH);
    }

    void select(int zone, int step) {
        previous = selected;
        selected = zone;
        direction = step;
        slide = 0;
        if (step < 0) kickLeft = KICK_FRAMES; else kickRight = KICK_FRAMES;
    }

    void draw(GraphicsManager graphics, int viewport) {
        if (revealed < 0 || names.isEmpty()) return;
        double reveal = easeOut((double) revealed / REVEAL_FRAMES);
        float alpha = (float) reveal;
        int rise = (int) Math.round((1 - reveal) * REVEAL_RISE);
        int cx = viewport / 2;
        drawPanel(graphics, cx, PANEL_Y + rise, alpha);
        drawNames(graphics, cx, TEXT_Y + rise, alpha);
        drawArrows(graphics, cx, PANEL_Y + rise + PANEL_H / 2, alpha);
        drawPips(graphics, cx, PIP_Y + rise, alpha);
    }

    private void drawPanel(GraphicsManager graphics, int cx, int top, float alpha) {
        for (int y = -2; y < PANEL_H + 2; y++) {
            int lean = (PANEL_H - 1 - Math.max(0, Math.min(PANEL_H - 1, y))) / 3 - PANEL_LEAN / 2;
            int left = cx - PANEL_HALF + lean, right = cx + PANEL_HALF + lean;
            if (y < 0 || y >= PANEL_H) {
                // Two-pixel navy rim, matching the wordmark's outline weight.
                fill(graphics, left - 2, top + y, right - left + 4, OUTLINE, alpha * 0.9f);
                continue;
            }
            fill(graphics, left - 2, top + y, 2, OUTLINE, alpha * 0.9f);
            fill(graphics, right, top + y, 2, OUTLINE, alpha * 0.9f);
            int rgb = y == 0 ? CHROME : y == 1 ? CHROME_LOW : y == PANEL_H - 1 ? CHROME_LOW
                    : mix(PANEL_TOP, PANEL_BOTTOM, (double) (y - 2) / (PANEL_H - 4));
            fill(graphics, left, top + y, right - left, rgb, alpha * (y <= 1 || y == PANEL_H - 1 ? 1f : 0.9f));
        }
    }

    /** The old name whips out over the first half of the slide, then the new one eases in. */
    private void drawNames(GraphicsManager graphics, int cx, int top, float alpha) {
        int clipLeft = cx - TEXT_CLIP, clipRight = cx + TEXT_CLIP;
        double t = (double) slide / SLIDE_FRAMES;
        if (t < 0.5) {
            double out = 2 * t;
            int offset = (int) Math.round(-direction * SLIDE_TRAVEL * out * out);
            drawName(graphics, previous, cx + offset, top, clipLeft, clipRight, alpha * (float) (1 - out));
            return;
        }
        double in = easeOut(2 * t - 1);
        int offset = (int) Math.round(direction * SLIDE_TRAVEL * (1 - in));
        drawName(graphics, selected, cx + offset, top, clipLeft, clipRight, alpha * (float) Math.min(1, in * 1.5));
    }

    private void drawName(GraphicsManager graphics, int zone, int cx, int top,
                          int clipLeft, int clipRight, float alpha) {
        if (alpha <= 0.02f) return;
        boolean[][] fill = fills[zone];
        int left = cx - fill[0].length / 2;
        drawMask(graphics, edges[zone], left + 1, top + 1, clipLeft, clipRight, 0, alpha * 0.5f);
        drawMask(graphics, edges[zone], left - 1, top - 1, clipLeft, clipRight, OUTLINE, alpha);
        for (int y = 0; y < fill.length; y++) {
            float[] c = textColours[y];
            boolean[] row = fill[y];
            for (int x = 0; x < row.length; x++) {
                if (!row[x]) continue;
                int end = x;
                while (end < row.length && row[end]) end++;
                clipped(graphics, left + x, top + y, end - x, clipLeft, clipRight, c[0], c[1], c[2], alpha);
                x = end;
            }
        }
    }

    private void drawArrows(GraphicsManager graphics, int cx, int midY, float alpha) {
        int bob = (int) Math.round(Math.sin(frame * 2 * Math.PI / BOB_PERIOD) * 1.5 + 1.5);
        int left = cx - ARROW_X - bob - KICK * kickLeft / KICK_FRAMES;
        int right = cx + ARROW_X + bob + KICK * kickRight / KICK_FRAMES;
        drawArrow(graphics, left, midY, -1, kickLeft, alpha);
        drawArrow(graphics, right, midY, 1, kickRight, alpha);
    }

    /** A solid chevron whose tip is at {@code tipX}, pointing {@code facing} (-1 left, 1 right). */
    private void drawArrow(GraphicsManager graphics, int tipX, int midY, int facing, int kick, float alpha) {
        float flash = (float) kick / KICK_FRAMES;
        // Rows narrow toward the tip; the outline is the body grown by one pixel.
        for (int dy = -ARROW_SIZE - 1; dy <= ARROW_SIZE + 1; dy++) {
            int length = ARROW_SIZE + 3 - Math.abs(dy);
            int x = facing < 0 ? tipX - 1 + Math.abs(dy) : tipX + 2 - Math.abs(dy) - length;
            fill(graphics, x, midY + dy, length, OUTLINE, alpha);
        }
        for (int dy = -ARROW_SIZE; dy <= ARROW_SIZE; dy++) {
            int length = ARROW_SIZE + 1 - Math.abs(dy);
            int x = facing < 0 ? tipX + Math.abs(dy) : tipX + 1 - Math.abs(dy) - length;
            int rgb = mix(textRgb(dy + ARROW_SIZE + 1), 0xFFFFFF, flash);
            fill(graphics, x, midY + dy, length, rgb, alpha);
        }
    }

    private void drawPips(GraphicsManager graphics, int cx, int midY, float alpha) {
        int count = names.size();
        int first = cx - (count - 1) * PIP_SPACING / 2;
        for (int i = 0; i < count; i++) diamond(graphics, first + i * PIP_SPACING, midY, 2, PIP, alpha * 0.85f);
        double t = easeOut((double) slide / SLIDE_FRAMES);
        int from = first + previous * PIP_SPACING, to = first + selected * PIP_SPACING;
        // Wrapping jumps straight to the far end rather than sweeping across every pip.
        int x = Math.abs(selected - previous) > 1 ? to : (int) Math.round(from + (to - from) * t);
        diamond(graphics, x, midY, 4, OUTLINE, alpha);
        diamond(graphics, x, midY, 3, PIP_LIT, alpha);
        diamond(graphics, x, midY, 1, 0xFFFFFF, alpha);
    }

    private static void diamond(GraphicsManager graphics, int cx, int cy, int radius, int rgb, float alpha) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            fill(graphics, cx - half, cy + dy, half * 2 + 1, rgb, alpha);
        }
    }

    private static void drawMask(GraphicsManager graphics, boolean[][] mask, int left, int top,
                                 int clipLeft, int clipRight, int rgb, float alpha) {
        for (int y = 0; y < mask.length; y++) {
            boolean[] row = mask[y];
            for (int x = 0; x < row.length; x++) {
                if (!row[x]) continue;
                int end = x;
                while (end < row.length && row[end]) end++;
                clipped(graphics, left + x, top + y, end - x, clipLeft, clipRight, r(rgb), g(rgb), b(rgb), alpha);
                x = end;
            }
        }
    }

    private static void clipped(GraphicsManager graphics, int x, int y, int width, int clipLeft, int clipRight,
                                float r, float g, float b, float alpha) {
        int start = Math.max(x, clipLeft), end = Math.min(x + width, clipRight);
        // Fade the pixels near each clip edge so sliding names dissolve into the banner.
        while (start < end && start - clipLeft < CLIP_FADE) {
            TitleWordmark.rect(graphics, start, y, 1, r, g, b, alpha * (start - clipLeft + 1) / (CLIP_FADE + 1));
            start++;
        }
        while (start < end && clipRight - end < CLIP_FADE) {
            TitleWordmark.rect(graphics, end - 1, y, 1, r, g, b, alpha * (clipRight - end + 1) / (CLIP_FADE + 1));
            end--;
        }
        TitleWordmark.rect(graphics, start, y, end - start, r, g, b, alpha);
    }

    private static void fill(GraphicsManager graphics, int x, int y, int width, int rgb, float alpha) {
        TitleWordmark.rect(graphics, x, y, width, r(rgb), g(rgb), b(rgb), Math.min(1f, alpha));
    }

    static boolean[][] rasterise(String name) {
        String text = name.toUpperCase(java.util.Locale.ROOT);
        int width = Math.max(1, text.length() * ADVANCE - SCALE + TEXT_LEAN);
        boolean[][] mask = new boolean[TEXT_H][width];
        for (int letter = 0; letter < text.length(); letter++) {
            int index = text.charAt(letter) - 'A';
            if (index < 0 || index >= 26) continue;
            int glyph = index * GLYPH_W * GLYPH_H;
            for (int y = 0; y < TEXT_H; y++) {
                int lean = (TEXT_H - 1 - y) / 4;
                for (int gx = 0; gx < GLYPH_W; gx++) {
                    if (GLYPHS.charAt(glyph + y / SCALE * GLYPH_W + gx) != '1') continue;
                    for (int dx = 0; dx < SCALE; dx++) mask[y][letter * ADVANCE + gx * SCALE + dx + lean] = true;
                }
            }
        }
        return mask;
    }

    private float[][] textColours() {
        float[][] colours = new float[TEXT_H][];
        for (int y = 0; y < TEXT_H; y++) {
            int rgb = textRgb(y);
            colours[y] = new float[]{r(rgb), g(rgb), b(rgb)};
        }
        return colours;
    }

    private int textRgb(int y) {
        if (y == HORIZON_ROW) return HORIZON;
        for (int i = 1; i < stops.length; i++) {
            if (y > stops[i][0]) continue;
            int[] from = stops[i - 1], to = stops[i];
            return mix(from[1], to[1], (double) (y - from[0]) / (to[0] - from[0]));
        }
        return stops[stops.length - 1][1];
    }

    private static int mix(int from, int to, double t) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = from >> shift & 0xFF, b = to >> shift & 0xFF;
            rgb |= (int) Math.round(a + (b - a) * t) << shift;
        }
        return rgb;
    }

    private static double easeOut(double t) {
        double u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    private static float r(int rgb) { return (rgb >> 16 & 0xFF) / 255f; }
    private static float g(int rgb) { return (rgb >> 8 & 0xFF) / 255f; }
    private static float b(int rgb) { return (rgb & 0xFF) / 255f; }
}
