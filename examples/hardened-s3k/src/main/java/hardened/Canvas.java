package hardened;

import com.openggf.control.ButtonPrompts;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.debug.DebugColor;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One screen-space presentation adapter. Uses the engine's UI font and GL command queue.
 * Text is drawn only at whole-number scales: the font's 9x10 cell then lands on exact
 * pixels at the native 320x224 view and at every integer window scale. Fractional
 * scales resample the glyphs and smear below about 1x, so copy is kept to
 * {@link #LINE_CHARS} characters per line instead.
 */
final class Canvas implements AutoCloseable {
    static final int WIDTH = 320, HEIGHT = 224;
    static final int GLYPH = 9;
    /** Characters that fit inside a {@link #panel} with an 8px margin at scale 1. */
    static final int LINE_CHARS = 31;
    static final int GOLD = 0xFFE3A3, MINT = 0xD5EFDC, SAGE = 0xA4D0BB, MUTED = 0xB9CEC5;
    static final int AMBER = 0xFFCF74, ALERT = 0xFF6A4D, CLEAR = 0x9CEB9C, INK = 0x102326;
    private final PixelFontTextRenderer text = new PixelFontTextRenderer();
    private String confirm = "ENTER", back = "ESC", direction = "ARROWS", start = "START";

    private record Rect(int x, int y, int w, int h, int rgb, float alpha) implements GLCommandable {
        @Override public void execute(int cameraX, int cameraY, int width, int height) {
            new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                    alpha, x, y, x + w, y + h).execute(0, 0, width, height);
        }
    }

    void rect(int x, int y, int w, int h, int rgb, float alpha) {
        if (w > 0 && h > 0) graphics().registerCommand(new Rect(x, y, w, h, rgb, Math.clamp(alpha, 0f, 1f)));
    }
    void rect(int x, int y, int w, int h, int rgb) { rect(x, y, w, h, rgb, 1); }
    void border(int x, int y, int w, int h, int rgb) {
        rect(x, y, w, 1, rgb); rect(x, y + h - 1, w, 1, rgb);
        rect(x, y, 1, h, rgb); rect(x + w - 1, y, 1, h, rgb);
    }
    void label(String value, int x, int y, int rgb, int scale) {
        var graphics = graphics();
        graphics.flushScreenSpace();
        var matrix = graphics.getProjectionMatrixBuffer();
        if (matrix != null) text.setProjectionMatrix(matrix);
        text.drawShadowedText(value, x, y, new DebugColor(0xff000000 | rgb), scale);
    }
    void label(String value, int x, int y, int rgb) { label(value, x, y, rgb, 1); }
    int width(String value, int scale) { return text.measureWidth(value, scale); }
    void center(String value, int y, int rgb, int scale) {
        label(value, (WIDTH - width(value, scale)) / 2, y, rgb, scale);
    }
    void center(String value, int y, int rgb) { center(value, y, rgb, 1); }
    /** Headline at 2x when it fits the panel, otherwise at the always-legible 1x. */
    void headline(String value, int y, int rgb) {
        int scale = value.length() <= 16 ? 2 : 1;
        center(value, scale == 2 ? y : y + 5, rgb, scale);
    }
    /** Centered greedy word wrap for runtime text such as a fault reason. */
    int centerWrapped(String value, int y, int rgb, int maxLines) {
        var lines = wrap(value.toUpperCase(Locale.ROOT), LINE_CHARS);
        int count = Math.min(maxLines, lines.size());
        for (int line = 0; line < count; line++) center(lines.get(line), y + line * 12, rgb);
        return count;
    }
    static List<String> wrap(String value, int limit) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (word.length() > limit) word = word.substring(0, limit);
            if (!line.isEmpty() && line.length() + 1 + word.length() > limit) {
                lines.add(line.toString()); line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
    void panel(int y, int height) {
        rect(10, y + 2, 300, height, 0x000000, .65f);
        rect(8, y, 304, height, INK, .96f);
        border(8, y, 304, height, 0x87B89C);
    }
    /** Selected-row bar with arrow cursors, matching the title and result menus. */
    void option(String value, int y, boolean selected, int blinkTick) {
        if (selected) {
            rect(36, y - 3, 248, 15, 0x3E6650);
            border(36, y - 3, 248, 15, 0x6E9C80);
            int nudge = blinkTick / 16 % 2;
            label(">", 44 + nudge, y, AMBER);
            label("<", 267 - nudge, y, AMBER);
        }
        center(value, y, selected ? GOLD : MUTED);
    }

    /**
     * Refreshes prompt labels from the live bindings: a keyboard player sees their keys,
     * a pad player sees the pad family's buttons. Presentation only; never gameplay state.
     */
    void refreshPrompts(InputHandler input) {
        if (input == null) return;
        confirm = upper(MenuInput.confirmLabel(input));
        back = upper(MenuInput.backLabel(input));
        direction = upper(MenuInput.directionLabel(input));
        start = ButtonPrompts.label(input, 0, ButtonPrompts.Button.START).map(Canvas::upper).orElse("START");
    }
    String confirmPrompt() { return confirm; }
    String backPrompt() { return back; }
    String directionPrompt() { return direction; }
    String startPrompt() { return start; }
    private static String upper(String value) { return value.toUpperCase(Locale.ROOT); }

    /** UI-only crowned-spore emblem; mirrors the in-world crown. No ROM gameplay art is synthesized. */
    void warning(int x, int y, int tick, boolean danger) {
        int crown = danger && tick / 12 % 2 == 0 ? ALERT : AMBER;
        // Dark outline first, then the cap, stem and crown spikes.
        rect(x - 17, y - 9, 34, 7, 0x061012); rect(x - 13, y - 13, 26, 5, 0x061012);
        rect(x - 6, y - 3, 12, 14, 0x061012);
        rect(x - 16, y - 8, 32, 5, 0xE07A3C); rect(x - 12, y - 12, 24, 4, 0xF2A050);
        rect(x - 5, y - 3, 10, 13, 0x87B89C);
        rect(x - 1, y - 1, 2, 6, INK); rect(x - 1, y + 6, 2, 2, INK);
        for (int spike = -1; spike <= 1; spike++) {
            int height = spike == 0 ? 6 : 4, cx = x + spike * 8;
            rect(cx - 2, y - 13 - height, 5, height + 1, 0x061012);
            rect(cx - 1, y - 12 - height, 3, height - 2, crown);
            rect(cx, y - 13 - height, 1, 2, crown);
        }
    }
    private GraphicsManager graphics() { return GameServices.graphics(); }
    void flush() { graphics().flushScreenSpace(); }
    @Override public void close() { text.cleanup(); }
}
