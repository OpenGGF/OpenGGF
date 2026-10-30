package practice;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.mods.ui.TextLayout;

import java.util.List;
import java.util.Locale;

/**
 * Act Practice's page look, drawn only through the public scene canvas and fonts so the example
 * reads as part of the product: a navy page, a header band with the bold title over a gold rule,
 * light {@link NameFont} sentences wrapped by measured width, a gold-edged focus band, status icons
 * and a footer of key caps in {@link CompactFont} capitals. Every width comes from the face that
 * draws the text. A mod cannot use the engine's own menu classes; this is the creator-side echo of
 * them, as the bundled Time Attack mode keeps its own.
 */
final class PracticePage {
    private PracticePage() { }

    static final int INK = 0xFF02081C;
    static final int HEAD = 0xFF0A245C;
    static final int FOCUS_FILL = 0xFF1C4AA8;
    static final int GOLD = 0xFFFFD64D;
    static final int CYAN = 0xFF80E8FF;
    static final int AMBER = 0xFFFFB840;
    static final int RED = 0xFFFF6B6B;
    static final int GREEN = 0xFF73EB94;
    static final int TEXT = 0xFFE8EDFF;
    static final int MUTED = 0xFFB8C4E6;
    static final int DIM = 0xFF8794BD;

    private static final int BACKGROUND = 0x030A26;
    private static final int CHECKER = 0x14000000 | 0x3A5BA8;
    private static final int BODY = 0xB805123B;
    private static final int CAP_FACE = 0xFF1A2B5E;
    private static final int CAP_EDGE = 0xFFC9D4F5;
    private static final int CAP_SHADOW = 0xFF00050F;

    static final int HEADER_H = 29;
    static final int FOOTER_Y = 205;
    static final int BODY_TOP = HEADER_H + 6;
    static final int MARGIN = 8;
    static final int CARD_H = 34;
    static final String ARROWS_LR = "LEFT/RIGHT";

    /** What a notice reports, which picks its icon and colour. */
    enum Tone { INFO, GOOD, WARNING, ERROR }

    /** One footer hint: a key cap and what it does here. */
    record Hint(String key, String label) { }

    static Hint hint(String key, String label) { return new Hint(key, label); }

    /** The page background, body plate and header band with {@code title}. */
    static void page(SceneCanvas canvas, String title) {
        int width = canvas.width();
        canvas.clear(BACKGROUND);
        for (int y = 0; y < 224; y += 32) {
            for (int x = ((y / 32) % 2) * 32; x < width; x += 64) {
                canvas.fill(x, y, Math.min(32, width - x), Math.min(32, 224 - y), CHECKER);
            }
        }
        canvas.fill(0, HEADER_H, width, FOOTER_Y - HEADER_H, BODY);
        canvas.fill(0, 0, width, HEADER_H, HEAD);
        canvas.fill(0, HEADER_H - 2, width, 1, GOLD);
        canvas.fill(0, HEADER_H - 1, width, 1, 0xFF664405);
        canvas.text(TextLayout.ellipsis(title, width - 2 * MARGIN, canvas::textWidth), MARGIN + 1, 10, GOLD);
    }

    /** The focus band: a blue fill with a one-pixel gold edge whose corners stay open. */
    static void focus(PixelCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, FOCUS_FILL);
        canvas.fill(x + 1, y - 1, w - 2, 1, GOLD);
        canvas.fill(x + 1, y + h, w - 2, 1, GOLD);
        canvas.fill(x - 1, y + 1, 1, h - 2, GOLD);
        canvas.fill(x + w, y + 1, 1, h - 2, GOLD);
    }

    /** Wrapped {@link NameFont} lines from {@code y}, every word whole; returns the next line's y. */
    static int paragraph(PixelCanvas canvas, String text, int x, int y, int width, int argb) {
        for (String line : TextLayout.wrap(text, width, NameFont::width)) {
            NameFont.draw(canvas, line, x, y, argb);
            y += NameFont.LINE_HEIGHT;
        }
        return y;
    }

    static int colour(Tone tone) {
        return switch (tone) {
            case INFO -> CYAN;
            case GOOD -> GREEN;
            case WARNING -> AMBER;
            case ERROR -> RED;
        };
    }

    /** An icon and a wrapped sentence in the tone's colour; returns the next line's y. */
    static int notice(PixelCanvas canvas, Tone tone, String text, int x, int y, int width) {
        icon(canvas, tone, x, y + 3);
        return paragraph(canvas, text, x + 10, y, width - 10, colour(tone));
    }

    /** A 7x7 status icon: i (info), a check (good), ! (warning) or a cross (error). */
    static void icon(PixelCanvas canvas, Tone tone, int x, int y) {
        int c = colour(tone);
        switch (tone) {
            case INFO -> {
                outline(canvas, x, y, 7, 7, c);
                canvas.fill(x + 3, y + 2, 1, 1, c);
                canvas.fill(x + 3, y + 4, 1, 2, c);
            }
            case GOOD -> {
                canvas.fill(x, y + 3, 1, 2, c);
                canvas.fill(x + 1, y + 4, 1, 2, c);
                canvas.fill(x + 2, y + 5, 1, 2, c);
                for (int i = 0; i < 4; i++) canvas.fill(x + 3 + i, y + 4 - i, 1, 2, c);
            }
            case WARNING -> {
                for (int i = 0; i < 4; i++) canvas.fill(x + 3 - i, y + i * 2, 1 + 2 * i, 2, c);
                canvas.fill(x + 3, y + 2, 1, 3, INK);
                canvas.fill(x + 3, y + 6, 1, 1, INK);
            }
            case ERROR -> {
                for (int i = 0; i < 7; i++) {
                    canvas.fill(x + i, y + i, 1, 1, c);
                    canvas.fill(x + 6 - i, y + i, 1, 1, c);
                }
            }
        }
    }

    /** A solid 4x7 triangle pointing left ({@code direction < 0}) or right. */
    static void arrow(PixelCanvas canvas, int x, int y, int direction) {
        for (int i = 0; i < 4; i++) {
            canvas.fill(direction < 0 ? x + 3 - i : x + i, y + i, 1, 7 - 2 * i, GOLD);
        }
    }

    /**
     * Which of {@code count} choices shows, centred on {@code centreX}: a dot per choice with a dark
     * ring, the chosen one a wider gold pill.
     */
    static void dots(PixelCanvas canvas, int centreX, int y, int count, int chosen) {
        int width = 0;
        for (int i = 0; i < count; i++) width += (i == chosen ? 8 : 3) + (i > 0 ? 4 : 0);
        int x = centreX - width / 2;
        for (int i = 0; i < count; i++) {
            int w = i == chosen ? 8 : 3;
            canvas.fill(x - 1, y - 1, w + 2, 5, INK);
            canvas.fill(x, y, w, 3, i == chosen ? GOLD : DIM);
            x += w + 4;
        }
    }

    /** A game's display name for its id. */
    static String gameName(String gameId) {
        return switch (gameId) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3 & KNUCKLES";
            default -> gameId.toUpperCase(Locale.ROOT);
        };
    }

    /** The footer band with key caps at the right, as the engine's menus place them. */
    static void footer(PixelCanvas canvas, List<Hint> hints) {
        int width = canvas.width();
        canvas.fill(0, FOOTER_Y, width, 224 - FOOTER_Y, INK);
        canvas.fill(0, FOOTER_Y, width, 1, 0xFF1C2960);
        int x = width - MARGIN - hintsWidth(hints);
        int y = FOOTER_Y + 4;
        for (Hint hint : hints) {
            x += cap(canvas, x, y, hint.key());
            String label = hint.label().toUpperCase(Locale.ROOT);
            CompactFont.draw(canvas, label, x + 4, y + 2, 1, TEXT);
            x += 4 + CompactFont.width(label, 1) + 10;
        }
    }

    static int hintsWidth(List<Hint> hints) {
        int width = 0;
        for (Hint hint : hints) {
            if (width > 0) width += 10;
            width += capWidth(hint.key()) + 4 + CompactFont.width(hint.label().toUpperCase(Locale.ROOT), 1);
        }
        return width;
    }

    static int capWidth(String key) {
        return ARROWS_LR.equals(key) ? 19 : Math.max(11, CompactFont.width(key, 1) + 6);
    }

    /** A key cap, 11 pixels tall, with its top-left at (x, y); returns its width. */
    static int cap(PixelCanvas canvas, int x, int y, String key) {
        int w = capWidth(key);
        canvas.fill(x, y + 1, w, 10, CAP_SHADOW);
        canvas.fill(x, y, w, 10, CAP_FACE);
        outline(canvas, x, y, w, 10, CAP_EDGE);
        if (ARROWS_LR.equals(key)) {
            triangle(canvas, x + 3, y + 2, true);
            triangle(canvas, x + w - 7, y + 2, false);
        } else {
            CompactFont.draw(canvas, key, x + (w - CompactFont.width(key, 1)) / 2, y + 2, 1, 0xFFFFFFFF);
        }
        return w;
    }

    private static void triangle(PixelCanvas canvas, int x, int y, boolean left) {
        for (int i = 0; i < 4; i++) {
            int span = 7 - 2 * i;
            canvas.fill(left ? x + 3 - i : x + i, y + i, 1, span, 0xFFFFFFFF);
        }
    }

    static void outline(PixelCanvas canvas, int x, int y, int w, int h, int argb) {
        canvas.fill(x, y, w, 1, argb);
        canvas.fill(x, y + h - 1, w, 1, argb);
        canvas.fill(x, y + 1, 1, h - 2, argb);
        canvas.fill(x + w - 1, y + 1, 1, h - 2, argb);
    }
}
