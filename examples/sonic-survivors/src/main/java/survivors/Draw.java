package survivors;

import com.openggf.graphics.GLCommand;
import com.openggf.mods.ui.BitmapFont;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.mods.ui.UiPrimitives;
import com.openggf.level.objects.ObjectServices;

/**
 * Code-drawn screen-space text and panels: a 5x7 font (plus a 3x5 one for damage numbers),
 * filled rectangles, outlines and circles. Screen overlays use a camera-independent canvas;
 * world-space weapon effects keep their native coordinates.
 */
final class Draw {
    private Draw() { }

    static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,:!?-+/%()'><x*=";
    // Seven 5-bit rows per glyph, top to bottom.
    static final String GLYPHS =
            "01110100011000111111100011000110001"   // A
            + "11110100011000111110100011000111110" // B
            + "01111100001000010000100001000001111" // C
            + "11110100011000110001100011000111110" // D
            + "11111100001000011110100001000011111" // E
            + "11111100001000011110100001000010000" // F
            + "01111100001000010011100011000101111" // G
            + "10001100011000111111100011000110001" // H
            + "11111001000010000100001000010011111" // I
            + "00111000100001000010100101001001100" // J
            + "10001100101010011000101001001010001" // K
            + "10000100001000010000100001000011111" // L
            + "10001110111010110101100011000110001" // M
            + "10001110011010110011100011000110001" // N
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
            + "11111000010001000100010001000011111" // Z
            + "01110100111010110101101011100101110" // 0
            + "00100011000010000100001000010001110" // 1
            + "01110100010000100110010001000011111" // 2
            + "11110000010000101110000010000111110" // 3
            + "00010001100101010010111110001000010" // 4
            + "11111100001111000001000011000101110" // 5
            + "00110010001000011110100011000101110" // 6
            + "11111000010001000100010000100001000" // 7
            + "01110100011000101110100011000101110" // 8
            + "01110100011000101111000010001001100" // 9
            + "00000000000000000000000000110001100" // .
            + "00000000000000000000011000010001000" // ,
            + "00000011000110000000011000110000000" // :
            + "00100001000010000100001000000000100" // !
            + "01110100010000100110001000000000100" // ?
            + "00000000000000011111000000000000000" // -
            + "00000001000010011111001000010000000" // +
            + "00001000100001000100010000100010000" // /
            + "11001110100001000100010000101110011" // %
            + "00010001000100001000010000010000010" // (
            + "01000001000001000010000100010001000" // )
            + "00100001000100000000000000000000000" // '
            + "01000001000001000001000100010001000" // >
            + "00010001000100010000010000010000010" // <
            + "00000000001000101010001000101010001" // x (times)
            + "00000101010111011111011101010100000" // *
            + "00000000001111100000111110000000000"; // =

    static final String SMALL_CHARS = "0123456789+-x";
    static final String SMALL_GLYPHS =
            "111101101101111" + "010110010010111" + "111001111100111" + "111001111001111"
            + "101101111001001" + "111100111001111" + "111100111101111" + "111001010010010"
            + "111101111101111" + "111101111001111" + "000010111010000" + "000000111000000"
            + "000101010101000";

    static final int WHITE = 0xFFFFFF, GOLD = 0xFFD030, YELLOW = 0xFFF060, RED = 0xFF4030,
            CYAN = 0x60E8FF, GREEN = 0x60FF60, NAVY = 0x101848, GREY = 0xA0A0B0, ORANGE = 0xFF9020,
            PINK = 0xFF70D0, BLUE = 0x4070FF, BLACK = 0x000000, PURPLE = 0xB070FF;

    static BitmapFont createFont() { return BitmapFont.binary(CHARS, GLYPHS, 5, 7, 6); }
    static BitmapFont createSmallFont() { return BitmapFont.binary(SMALL_CHARS, SMALL_GLYPHS, 3, 5, 4); }
    private static LevelOverlayCanvas canvas(ObjectServices services) {
        return new LevelOverlayCanvas(services.graphicsManager(), services.camera().getWidth(), services.camera().getHeight());
    }
    private static int colour(int rgb, float alpha) {
        return Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24 | rgb & 0xFFFFFF;
    }

    /** Width in pixels of {@code text} at {@code scale} (6px advance per glyph at scale 1). */
    static int width(String text, int scale) {
        return text.isEmpty() ? 0 : text.length() * 6 * scale - scale;
    }

    static void text(ObjectServices s, String text, int x, int y, int scale, int rgb, float alpha) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        var glyphText = new StringBuilder(upper);
        for (int i = 0; i < upper.length() && i < text.length(); i++) {
            if (upper.charAt(i) == 'X' && text.charAt(i) == 'x') glyphText.setCharAt(i, 'x');
        }
        s.gameService(MenuArt.class).font.draw(canvas(s), glyphText.toString(), x, y, scale, colour(rgb, alpha));
    }

    /** Text with a one-pixel dark drop shadow. */
    static void shadow(ObjectServices s, String text, int x, int y, int scale, int rgb, float alpha) {
        text(s, text, x + scale, y + scale, scale, NAVY, alpha * 0.85f);
        text(s, text, x, y, scale, rgb, alpha);
    }

    static void centred(ObjectServices s, String text, int y, int scale, int rgb, float alpha) {
        shadow(s, text, (s.camera().getWidth() - width(text, scale)) / 2, y, scale, rgb, alpha);
    }

    /** Full alphabet labels anchored to world objects (the small font contains digits only). */
    static void labelWorld(ObjectServices s, String text, int x, int y, int rgb) {
        shadow(s, text, x - s.camera().getX(), y - s.camera().getY(), 1, rgb, 1f);
    }

    /** 3x5 digits in world coordinates, for floating damage numbers. */
    static void smallWorld(ObjectServices s, String text, int x, int y, int scale, int rgb, float alpha) {
        s.gameService(MenuArt.class).smallFont.glyphs(text, x, y, scale, (px, py, w, h) -> {
            rectWorld(s, px + scale, py + scale, w, h, NAVY, alpha);
            rectWorld(s, px, py, w, h, rgb, alpha);
        });
    }

    static void rect(ObjectServices s, int x, int y, int w, int h, int rgb, float alpha) {
        canvas(s).fill(x, y, w, h, colour(rgb, alpha));
    }

    static void rectWorld(ObjectServices s, int x, int y, int w, int h, int rgb, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0f) return;
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        s.graphicsManager().registerCommand(alpha >= 1f
                ? new GLCommand(GLCommand.CommandType.RECTI, 0, r, g, b, x, y, x + w, y + h)
                : new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                        r, g, b, alpha, x, y, x + w, y + h));
    }

    /** A translucent panel with a two-tone rim. */
    static void panel(ObjectServices s, int x, int y, int w, int h, float alpha) {
        rect(s, x - 2, y - 2, w + 4, h + 4, NAVY, alpha);
        rect(s, x - 1, y - 1, w + 2, 1, 0x6890E0, alpha);
        rect(s, x - 1, y + h, w + 2, 1, 0x284080, alpha);
        rect(s, x, y, w, h, 0x0A1440, alpha * 0.86f);
    }

    static void outline(ObjectServices s, int x, int y, int w, int h, int rgb, float alpha) {
        UiPrimitives.frame(canvas(s), x, y, w, h, colour(rgb, alpha));
    }

    /** A filled bar: frame, background and a {@code fraction} fill. */
    static void bar(ObjectServices s, int x, int y, int w, int h, double fraction, int rgb, float alpha) {
        rect(s, x - 1, y - 1, w + 2, h + 2, NAVY, alpha);
        rect(s, x, y, w, h, 0x202840, alpha);
        rect(s, x, y, (int) Math.round(w * Math.max(0, Math.min(1, fraction))), h, rgb, alpha);
    }

    /** A ring outline of the given thickness, in world coordinates, drawn as short spans. */
    static void circleWorld(ObjectServices s, int cx, int cy, int radius, int thickness, int rgb, float alpha) {
        if (radius <= 0) return;
        int inner = Math.max(0, radius - thickness);
        for (int dy = -radius; dy <= radius; dy++) {
            int outerHalf = (int) Math.sqrt((double) radius * radius - dy * dy);
            int innerHalf = Math.abs(dy) >= inner ? -1 : (int) Math.sqrt((double) inner * inner - dy * dy);
            if (innerHalf < 0) {
                rectWorld(s, cx - outerHalf, cy + dy, outerHalf * 2 + 1, 1, rgb, alpha);
            } else {
                rectWorld(s, cx - outerHalf, cy + dy, outerHalf - innerHalf, 1, rgb, alpha);
                rectWorld(s, cx + innerHalf + 1, cy + dy, outerHalf - innerHalf, 1, rgb, alpha);
            }
        }
    }

    /** A jagged lightning line between two world points. */
    static void boltWorld(ObjectServices s, int x0, int y0, int x1, int y1, int seed, int rgb, float alpha) {
        int steps = Math.max(2, (int) (Math.hypot(x1 - x0, y1 - y0) / 6));
        int px = x0, py = y0;
        for (int i = 1; i <= steps; i++) {
            int jitter = i == steps ? 0 : ((seed * 31 + i * 17) % 7) - 3;
            int nx = x0 + (x1 - x0) * i / steps + jitter, ny = y0 + (y1 - y0) * i / steps - jitter;
            lineWorld(s, px, py, nx, ny, rgb, alpha);
            px = nx;
            py = ny;
        }
    }

    static void lineWorld(ObjectServices s, int x0, int y0, int x1, int y1, int rgb, float alpha) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = n == 0 ? x0 : x0 + (x1 - x0) * i / n, y = n == 0 ? y0 : y0 + (y1 - y0) * i / n;
            rectWorld(s, x, y, 2, 2, rgb, alpha);
        }
    }
    /** Scales ROM mapping tiles about their native origin without allocating replacement art. */
    static void scaledSprite(ObjectServices s, String key, int frame, int x, int y,
                             boolean flip, float scale) {
        var manager = s.renderManager();
        var sheet = manager.getSheet(key);
        var renderer = manager.getRenderer(key);
        if (sheet == null || renderer == null || !renderer.isReady()
                || frame < 0 || frame >= sheet.getFrameCount()) return;
        var desc = new com.openggf.level.PatternDesc();
        for (var piece : sheet.getFrame(frame).pieces()) {
            com.openggf.level.render.SpritePieceRenderer.renderPiece(piece, 0, 0,
                    renderer.getPatternBase(), sheet.getPaletteIndex(), flip, false,
                    (pattern, h, v, palette, dx, dy) -> {
                        desc.set((pattern & 0x7FF) | (h ? 0x800 : 0) | (v ? 0x1000 : 0)
                                | ((palette & 3) << 13) | (piece.priority() ? 0x8000 : 0));
                        s.graphicsManager().renderPatternWithIdScaled(pattern, desc,
                                x + dx * scale, y + dy * scale, 8 * scale, 8 * scale);
                    });
        }
    }
}
