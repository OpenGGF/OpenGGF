package infinite;

import com.openggf.control.InputHandler;
import com.openggf.graphics.GraphicsManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Arcade-style attract board for the title screen. After {@link #IDLE_FRAMES} without input
 * on the active title it fades in over the title and pages through ZONE LEADERS (each zone's
 * top score), then the top 10 of every zone that has scores, then fades back to the title
 * and waits again. Any key or button dismisses it at once, and that press does not reach
 * the title, so it cannot start a game by accident. Code-drawn in the zone picker's style.
 */
final class LeaderboardScreen {
    static final int IDLE_FRAMES = 600;
    static final int PAGE_FRAMES = 300;
    static final int FADE_FRAMES = 12;
    static final int GLYPH_W = 5, GLYPH_H = 7;
    /** Digits and punctuation in the zone picker's 5x7 style (its GLYPHS hold A-Z). */
    private static final String SYMBOLS = "0123456789.-!";
    private static final String SYMBOL_GLYPHS =
            "01110100011001110101110011000101110"   // 0
            + "00100011000010000100001000010001110" // 1
            + "01110100010000100010001000100011111" // 2
            + "11110000010000101110000010000111110" // 3
            + "00010001100101010010111110001000010" // 4
            + "11111100001111000001000011000101110" // 5
            + "00110010001000011110100011000101110" // 6
            + "11111000010001000100010000100001000" // 7
            + "01110100011000101110100011000101110" // 8
            + "01110100011000101111000010001001100" // 9
            + "00000000000000000000000000110001100" // .
            + "00000000000000011111000000000000000" // -
            + "00100001000010000100001000000000100"; // !
    private static final int GOLD = 0xFFD030, PALE = 0xFFF8C8, WHITE = 0xFFFFFF, DIM = 0x7888C8;
    private static final int PANEL = 0x0A1034, OUTLINE = 0x101848, CHROME = 0xA8E0FF;

    private final List<String> zoneNames;
    private final Supplier<Leaderboard> leaderboard;
    private int idle;
    // Frames since the board appeared, or -1 while the title shows.
    private int shown = -1;
    // Page 0 is ZONE LEADERS; the rest are the zones with scores, captured when the board appears.
    private final List<Integer> pages = new ArrayList<>();

    LeaderboardScreen(List<String> zoneNames, Supplier<Leaderboard> leaderboard) {
        this.zoneNames = List.copyOf(zoneNames);
        this.leaderboard = leaderboard;
    }

    boolean showing() { return shown >= 0; }
    /** -1 for ZONE LEADERS, otherwise the zone whose top 10 is showing. */
    int pageZone() { return pages.get(Math.min(pages.size() - 1, shown / PAGE_FRAMES)); }
    int pageCount() { return pages.size(); }

    void reset() { idle = 0; shown = -1; }

    /**
     * Advances the idle countdown or the board. Returns true when this frame's input dismissed
     * the board, so the caller keeps the press from the title and its zone picker.
     */
    boolean update(InputHandler input, boolean active) {
        if (!active) { reset(); return false; }
        boolean pressed = input != null && pressed(input);
        if (shown >= 0) {
            if (pressed) { reset(); return true; }
            if (++shown >= pages.size() * PAGE_FRAMES) reset();
            return false;
        }
        idle = pressed ? 0 : idle + 1;
        if (idle >= IDLE_FRAMES) start();
        return false;
    }

    /** Shows the board now, as the idle countdown does. */
    void start() {
        pages.clear();
        pages.add(-1);
        var board = leaderboard.get();
        for (int zone = 0; zone < zoneNames.size(); zone++) {
            if (!board.top(zone).isEmpty()) pages.add(zone);
        }
        shown = 0;
    }

    private static boolean pressed(InputHandler input) {
        var logical = input.logical();
        return input.isAnyKeyJustPressed() || logical.anyActionPressed() || logical.menuStart()
                || logical.menuAccept() || logical.menuBack() || logical.menuUp() || logical.menuDown()
                || logical.menuLeft() || logical.menuRight();
    }

    void draw(GraphicsManager graphics, int viewport) {
        if (shown < 0) return;
        int total = pages.size() * PAGE_FRAMES;
        float alpha = Math.min(1f, Math.min(shown, total - shown) / (float) FADE_FRAMES);
        int inPage = shown % PAGE_FRAMES;
        // Each page's content fades through the panel so pages flick over like an arcade attract loop.
        float content = alpha * Math.min(1f, Math.min(inPage + 1, PAGE_FRAMES - inPage) / (float) (FADE_FRAMES / 2));
        int cx = viewport / 2;
        for (int y = 0; y < 224; y++) fill(graphics, 0, y, viewport, PANEL, alpha * 0.92f);
        fill(graphics, 0, 40, viewport, CHROME, alpha);
        fill(graphics, 0, 41, viewport, OUTLINE, alpha);
        int zone = pageZone();
        if (zone < 0) drawLeaders(graphics, cx, content);
        else drawTopTen(graphics, cx, zone, content);
        // Page pips, as under the zone picker.
        int first = cx - (pages.size() - 1) * 6;
        int page = Math.min(pages.size() - 1, shown / PAGE_FRAMES);
        for (int i = 0; i < pages.size(); i++) fill(graphics, first + i * 12 - 1, 214, 3, i == page ? GOLD : DIM, alpha);
    }

    private void drawLeaders(GraphicsManager graphics, int cx, float alpha) {
        centred(graphics, "ZONE LEADERS", cx, 14, 2, GOLD, alpha);
        var board = leaderboard.get();
        for (int zone = 0; zone < zoneNames.size(); zone++) {
            int y = 56 + zone * 24;
            var top = board.top(zone);
            text(graphics, zoneNames.get(zone), cx - 150, y, 2, zone % 2 == 0 ? PALE : WHITE, alpha);
            String score = top.isEmpty() ? "-" : Integer.toString(top.get(0).score());
            right(graphics, score, cx + 150, y, 2, top.isEmpty() ? DIM : GOLD, alpha);
        }
    }

    private void drawTopTen(GraphicsManager graphics, int cx, int zone, float alpha) {
        centred(graphics, zoneNames.get(zone), cx, 6, 2, GOLD, alpha);
        centred(graphics, "TOP 10", cx, 26, 1, PALE, alpha);
        var top = leaderboard.get().top(zone);
        for (int i = 0; i < Leaderboard.SIZE; i++) {
            int y = 50 + i * 16;
            int rgb = i == 0 ? GOLD : i < top.size() ? WHITE : DIM;
            right(graphics, (i + 1) + ".", cx - 110, y, 2, rgb, alpha);
            if (i >= top.size()) { right(graphics, "-", cx + 40, y, 2, rgb, alpha); continue; }
            var entry = top.get(i);
            right(graphics, Integer.toString(entry.score()), cx + 40, y, 2, rgb, alpha);
            right(graphics, String.format(Locale.ROOT, "%.2fX", entry.speed()), cx + 140, y, 2, rgb, alpha);
        }
    }

    private static int width(String text, int scale) { return Math.max(0, text.length() * (GLYPH_W + 1) * scale - scale); }

    private static void centred(GraphicsManager graphics, String text, int cx, int y, int scale, int rgb, float alpha) {
        text(graphics, text, cx - width(text, scale) / 2, y, scale, rgb, alpha);
    }

    private static void right(GraphicsManager graphics, String text, int rightX, int y, int scale, int rgb, float alpha) {
        text(graphics, text, rightX - width(text, scale), y, scale, rgb, alpha);
    }

    /** Upright 5x7 text with a one-pixel navy drop shadow. */
    static void text(GraphicsManager graphics, String text, int x, int y, int scale, int rgb, float alpha) {
        glyphs(graphics, text, x + 1, y + 1, scale, OUTLINE, alpha);
        glyphs(graphics, text, x, y, scale, rgb, alpha);
    }

    private static void glyphs(GraphicsManager graphics, String text, int x, int y, int scale, int rgb, float alpha) {
        String upper = text.toUpperCase(Locale.ROOT);
        for (int letter = 0; letter < upper.length(); letter++) {
            String glyph = glyph(upper.charAt(letter));
            if (glyph == null) continue;
            int left = x + letter * (GLYPH_W + 1) * scale;
            for (int gy = 0; gy < GLYPH_H; gy++) {
                for (int gx = 0; gx < GLYPH_W; gx++) {
                    if (glyph.charAt(gy * GLYPH_W + gx) != '1') continue;
                    int end = gx;
                    while (end < GLYPH_W && glyph.charAt(gy * GLYPH_W + end) == '1') end++;
                    for (int sy = 0; sy < scale; sy++) {
                        fill(graphics, left + gx * scale, y + gy * scale + sy, (end - gx) * scale, rgb, alpha);
                    }
                    gx = end;
                }
            }
        }
    }

    private static String glyph(char c) {
        int size = GLYPH_W * GLYPH_H;
        if (c >= 'A' && c <= 'Z') return ZoneMenu.GLYPHS.substring((c - 'A') * size, (c - 'A' + 1) * size);
        int symbol = SYMBOLS.indexOf(c);
        return symbol < 0 ? null : SYMBOL_GLYPHS.substring(symbol * size, (symbol + 1) * size);
    }

    private static void fill(GraphicsManager graphics, int x, int y, int width, int rgb, float alpha) {
        TitleWordmark.rect(graphics, x, y, width, (rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, Math.min(1f, alpha));
    }
}
