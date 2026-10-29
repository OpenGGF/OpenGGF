package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.LineMetrics;
import com.openggf.graphics.text.TextFace;

import java.util.List;

/**
 * Shared host-menu chrome for the master title, Game Setup and Settings: key-cap
 * control hints, chips, status boxes, description boxes and focus frames on the
 * native 320x224 grid. Everything is drawn with {@link MenuStyle#fill} and the menu
 * font, so call order is draw order. Text widths and fits are measured by
 * {@link HostText} in the active {@link HostTextContext} language, matching the drawn glyphs.
 */
public final class MenuChrome {
    private MenuChrome() { }

    public static final int SCREEN_H = 224;
    public static final int HEADER_H = 29;
    public static final int FOOTER_Y = 205;
    public static final int FOOTER_H = SCREEN_H - FOOTER_Y;
    /** Native ASCII cell advances; measure real text with {@link #compactWidth} / {@link #labelWidth}. */
    public static final int COMPACT_ADVANCE = 6;
    public static final int LABEL_ADVANCE = 9;

    // Palette, matching the existing hub navy/gold so the new screens sit beside old pages.
    public static final float[] INK = {.008f, .031f, .11f};
    public static final float[] NAVY = {.02f, .07f, .23f};
    public static final float[] HEAD = {.04f, .14f, .36f};
    public static final float[] BUTTON = {.05f, .16f, .38f};
    public static final float[] EDGE = {.17f, .31f, .62f};
    public static final float[] FOCUS_FILL = {.11f, .29f, .66f};
    public static final float[] GOLD = {1f, .84f, .3f};
    /** Deep navy text keeps plain menu lettering legible on gold buttons. */
    public static final float[] GOLD_INK = {.06f, .14f, .45f};
    public static final float[] CYAN = {.5f, .91f, 1f};
    public static final float[] AMBER = {1f, .72f, .25f};
    public static final float[] TEXT = {.91f, .93f, 1f};
    public static final float[] WHITE = {1f, 1f, 1f};
    public static final float[] DIM = {.53f, .58f, .74f};
    public static final float[] MUTED = {.72f, .77f, .9f};
    public static final float[] RED = {1f, .42f, .42f};

    /** Key-cap glyph names for direction pairs. */
    public static final String ARROWS_LR = "←→";
    public static final String ARROWS_UD = "↑↓";

    /** One control hint: a key cap followed by what it does here. */
    public record Hint(String key, String label) {
        public Hint {
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("key");
            label = label == null ? "" : label;
        }
    }

    public static Hint hint(String key, String label) { return new Hint(key, label); }

    /** Left/Right hint, drawn as a pair of arrow triangles. */
    public static Hint leftRight(String label) { return new Hint(ARROWS_LR, label); }

    public static String horizontalKey(InputHandler input) {
        return controller(input) ? MenuInput.horizontalLabel(input) : ARROWS_LR;
    }

    public static String verticalKey(InputHandler input) {
        return controller(input) ? "D-Pad U/D" : ARROWS_UD;
    }

    public static Hint leftRight(InputHandler input, String label) {
        return hint(horizontalKey(input), label);
    }

    public static Hint upDown(InputHandler input, String label) {
        return hint(verticalKey(input), label);
    }

    public static String confirmKey(InputHandler input) {
        return input == null ? "Enter" : MenuInput.confirmLabel(input);
    }

    public static String backKey(InputHandler input) {
        return input == null ? "Esc" : MenuInput.backLabel(input);
    }

    public static String detailsKey(InputHandler input) {
        return input == null ? "F1" : MenuInput.detailsLabel(input);
    }

    public static boolean controller(InputHandler input) {
        return input != null && MenuInput.controller(input);
    }

    // -------------------------------------------------------------------------
    // Primitives
    // -------------------------------------------------------------------------

    public static void fill(PixelFont font, int x, int y, int w, int h, float[] c, float a) {
        MenuStyle.fill(font, x, y, w, h, c[0], c[1], c[2], a);
    }

    public static void fill(PixelFont font, int x, int y, int w, int h, float[] c) {
        fill(font, x, y, w, h, c, 1f);
    }

    public static void outline(PixelFont font, int x, int y, int w, int h, float[] c, float a, int thickness) {
        fill(font, x, y, w, thickness, c, a);
        fill(font, x, y + h - thickness, w, thickness, c, a);
        fill(font, x, y + thickness, thickness, h - 2 * thickness, c, a);
        fill(font, x + w - thickness, y + thickness, thickness, h - 2 * thickness, c, a);
    }

    public static void compact(PixelFont font, String text, int x, int y, float[] c) {
        compact(font, text, x, y, c, 1f);
    }

    public static void compact(PixelFont font, String text, int x, int y, float[] c, float a) {
        if (text == null || text.isEmpty()) return;
        font.drawText(text, x, y, MenuStyle.COMPACT, c[0], c[1], c[2], a);
    }

    public static void label(PixelFont font, String text, int x, int y, float[] c) {
        label(font, text, x, y, c, 1f);
    }

    public static void label(PixelFont font, String text, int x, int y, float[] c, float a) {
        if (text == null || text.isEmpty()) return;
        font.drawText(text, x, y, 1f, c[0], c[1], c[2], a);
    }

    public static int compactWidth(String text) {
        return text == null ? 0 : HostText.shared().width(text, TextFace.COMPACT, HostTextContext.current());
    }

    public static int labelWidth(String text) {
        return text == null ? 0 : HostText.shared().width(text, TextFace.LABEL, HostTextContext.current());
    }

    /** Pitch for stacked compact lines in the active language (8 for native lettering). */
    public static int compactLineHeight() {
        return compactMetrics().lineHeight();
    }

    /** Pitch for stacked label lines in the active language (10 for native lettering). */
    public static int labelLineHeight() {
        return HostText.shared().lineMetrics(TextFace.LABEL, HostTextContext.current()).lineHeight();
    }

    public static LineMetrics compactMetrics() {
        return HostText.shared().lineMetrics(TextFace.COMPACT, HostTextContext.current());
    }

    /** Clip compact text to a pixel width with a trailing ellipsis. */
    public static String fitCompact(String text, int maxWidth) { return MenuStyle.fit(text, maxWidth); }

    /** Clip full-size text to a pixel width with a trailing ellipsis. */
    public static String fitLabel(String text, int maxWidth) { return MenuStyle.fitLabel(text, maxWidth); }

    /** A solid pixel triangle pointing {@code dir} (one of L, R, U, D) inside a size x (2*size-1) box. */
    public static void triangle(PixelFont font, int x, int y, int size, char dir, float[] c, float a) {
        for (int i = 0; i < size; i++) {
            int span = 2 * i + 1;
            switch (dir) {
                case 'L' -> fill(font, x + i, y + size - 1 - i, 1, span, c, a);
                case 'R' -> fill(font, x + size - 1 - i, y + size - 1 - i, 1, span, c, a);
                case 'U' -> fill(font, x + size - 1 - i, y + i, span, 1, c, a);
                case 'D' -> fill(font, x + size - 1 - i, y + size - 1 - i, span, 1, c, a);
                default -> throw new IllegalArgumentException("direction " + dir);
            }
        }
    }

    /** 0..1 breathing value used for focus frames; period of roughly one second. */
    public static float pulse(int frame) {
        return (float) (0.5 + 0.5 * Math.sin(frame * (Math.PI * 2 / 64.0)));
    }

    /** Mix two colours. */
    public static float[] mix(float[] a, float[] b, float t) {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Ease-out cubic for slides and pops. */
    public static float easeOut(float t) {
        float c = Math.clamp(t, 0f, 1f) - 1f;
        return c * c * c + 1f;
    }

    // -------------------------------------------------------------------------
    // Composite pieces
    // -------------------------------------------------------------------------

    /** Animated selection frame: tinted fill, two-pixel gold border that breathes, and corner ticks. */
    public static void focus(PixelFont font, int x, int y, int w, int h, int frame) {
        float glow = pulse(frame);
        fill(font, x, y, w, h, FOCUS_FILL, .92f);
        float[] border = mix(GOLD, WHITE, glow * .35f);
        outline(font, x - 2, y - 2, w + 4, h + 4, border, 1f, 2);
        // Bright corner ticks give the frame a little sparkle as it breathes.
        float a = .45f + .55f * glow;
        fill(font, x - 3, y - 3, 3, 1, WHITE, a);
        fill(font, x - 3, y - 3, 1, 3, WHITE, a);
        fill(font, x + w, y + h + 2, 3, 1, WHITE, a);
        fill(font, x + w + 2, y + h, 1, 3, WHITE, a);
    }

    /** A quiet frame for a hovered or secondary element. */
    public static void panel(PixelFont font, int x, int y, int w, int h) {
        fill(font, x, y, w, h, BUTTON);
        outline(font, x, y, w, h, EDGE, 1f, 1);
    }

    /** Width of a key cap for {@code key}. */
    public static int keyCapWidth(String key) {
        if (ARROWS_LR.equals(key) || ARROWS_UD.equals(key)) return 19;
        return Math.max(11, compactWidth(capText(key)) + 6);
    }

    /** Key caps print in capitals: the compact font's lowercase descenders would hit the cap edge. */
    private static String capText(String key) {
        return key.toUpperCase(HostTextContext.current().locale());
    }

    /** Draws a key cap whose top-left is ({@code x}, {@code y}); 11 pixels tall. Returns its width. */
    public static int keyCap(PixelFont font, int x, int y, String key, boolean round) {
        int w = keyCapWidth(key);
        fill(font, x, y + 1, w, 10, new float[] {.0f, .02f, .07f});
        fill(font, x, y, w, 10, new float[] {.1f, .17f, .37f});
        outline(font, x, y, w, 10, new float[] {.79f, .83f, .96f}, 1f, 1);
        if (round) {
            // Knock out the corners so face-button caps read as round.
            fill(font, x, y, 1, 1, INK);
            fill(font, x + w - 1, y, 1, 1, INK);
            fill(font, x, y + 9, 1, 1, INK);
            fill(font, x + w - 1, y + 9, 1, 1, INK);
        }
        if (ARROWS_LR.equals(key)) {
            triangle(font, x + 3, y + 2, 3, 'L', WHITE, 1f);
            triangle(font, x + w - 6, y + 2, 3, 'R', WHITE, 1f);
        } else if (ARROWS_UD.equals(key)) {
            triangle(font, x + 3, y + 2, 3, 'U', WHITE, 1f);
            triangle(font, x + w - 8, y + 3, 3, 'D', WHITE, 1f);
        } else {
            String text = capText(key);
            compact(font, text, x + (w - compactWidth(text)) / 2, y + 2, WHITE);
        }
        return w;
    }

    /** Width that {@link #hints} needs for a set of hints. */
    public static int hintsWidth(List<Hint> hints) {
        int width = 0;
        for (Hint h : hints) {
            if (width > 0) width += 12;
            width += keyCapWidth(h.key()) + (h.label().isEmpty() ? 0 : 4 + compactWidth(h.label()));
        }
        return width;
    }

    /** Draws hints left to right from {@code x}; returns the end x. */
    public static int hints(PixelFont font, int x, int y, List<Hint> hints, boolean controller, float[] labelColor) {
        int cursor = x;
        for (Hint h : hints) {
            boolean round = controller && h.key().length() <= 2 && !h.key().startsWith("←") && !h.key().startsWith("↑");
            cursor += keyCap(font, cursor, y, h.key(), round);
            if (!h.label().isEmpty()) {
                cursor += 4;
                compact(font, h.label(), cursor, y + 2, labelColor);
                cursor += compactWidth(h.label());
            }
            cursor += 12;
        }
        return cursor - 12;
    }

    /**
     * The shared footer: an optional status box on the left (draft state, never a control)
     * and key-cap hints aligned to the right. Hints that would collide with the status box
     * are dropped from the left, keeping the most important (rightmost) ones.
     */
    public static void footer(PixelFont font, int width, String status, List<Hint> hints, boolean controller) {
        fill(font, 0, FOOTER_Y, width, FOOTER_H, INK);
        fill(font, 0, FOOTER_Y, width, 1, new float[] {.11f, .16f, .37f});
        int left = 6;
        if (status != null && !status.isEmpty()) {
            int sw = compactWidth(status) + 8;
            fill(font, 6, FOOTER_Y + 4, sw, 11, new float[] {.23f, .16f, 0f});
            outline(font, 6, FOOTER_Y + 4, sw, 11, new float[] {.48f, .35f, .06f}, 1f, 1);
            compact(font, status, 10, FOOTER_Y + 6, GOLD);
            left = 6 + sw + 10;
        }
        List<Hint> shown = hints;
        while (!shown.isEmpty() && hintsWidth(shown) > width - 8 - left) shown = shown.subList(1, shown.size());
        hints(font, width - 8 - hintsWidth(shown), FOOTER_Y + 4, shown, controller, TEXT);
    }

    /** A status pill that never takes focus; amber marks a value set for this game only. Returns its width. */
    public static int chip(PixelFont font, int x, int y, String text, boolean amber) {
        int w = compactWidth(text) + 10;
        fill(font, x, y, w, 12, NAVY, .94f);
        // Status only: no control-like outline or focus treatment.
        compact(font, text, x + 5, y + 2, amber ? AMBER : TEXT);
        return w;
    }

    public static int chipWidth(String text) { return compactWidth(text) + 10; }

    /** Small amber square used beside values that differ from their default. */
    public static void changedMarker(PixelFont font, int x, int y) {
        fill(font, x, y, 4, 4, AMBER);
        fill(font, x + 1, y + 4, 4, 1, new float[] {.3f, .18f, 0f});
    }

    /** Header band with a title and an optional scope badge (THIS GAME ONLY / ALL GAMES). */
    public static void header(PixelFont font, int width, String title, String scope) {
        fill(font, 0, 0, width, HEADER_H, HEAD);
        fill(font, 0, HEADER_H - 2, width, 1, GOLD);
        fill(font, 0, HEADER_H - 1, width, 1, new float[] {.4f, .27f, .02f});
        label(font, title, 9, 9, GOLD);
        if (scope != null && !scope.isEmpty()) {
            int w = compactWidth(scope) + 10;
            int x = width - 8 - w;
            fill(font, x, 8, w, 12, NAVY);
            outline(font, x, 8, w, 12, CYAN, 1f, 1);
            compact(font, scope, x + 5, 10, CYAN);
        }
    }

    /** Full-screen navy page with the checkerboard; header and footer are drawn by the caller. */
    public static void background(PixelFont font, int width) {
        fill(font, 0, 0, width, SCREEN_H, new float[] {.012f, .04f, .15f});
        MenuStyle.checkerboard(font, width);
        fill(font, 0, HEADER_H, width, FOOTER_Y - HEADER_H, NAVY, .72f);
    }

    /** Section caption: small cyan caps with a fading rule to the right. */
    public static void section(PixelFont font, String text, int x, int y, int width) {
        compact(font, text, x, y, CYAN);
        int start = x + compactWidth(text) + 6;
        int end = x + width;
        for (int px = start, i = 0; px < end; px += 8, i++) {
            float a = Math.max(.08f, .55f - i * .05f);
            fill(font, px, y + 4, Math.min(6, end - px), 1, EDGE, a);
        }
    }

    public static final int HOLD_TOAST_H = 17;

    /** Width of {@link #holdToast} for a key and label. */
    public static int holdToastWidth(String key, String label) {
        return 5 + keyCapWidth(key) + 5 + compactWidth(label) + 7;
    }

    /**
     * A centred hold-to-confirm toast for shortcuts that work over gameplay: a key cap and
     * label on a navy panel that fills from the left as {@code progress} rises. Holding
     * lights the frame with the focus gold; {@code done} keeps it solid gold while the
     * action runs. Draw it at the top edge, so it never covers the stage's playfield centre.
     */
    public static void holdToast(PixelFont font, int width, int y, String key, String label,
                                 float progress, boolean done, int frame) {
        float p = Math.clamp(progress, 0f, 1f);
        int w = holdToastWidth(key, label);
        int h = HOLD_TOAST_H;
        int x = (width - w) / 2;
        fill(font, x + 1, y + h, w, 1, new float[] {0, 0, 0}, .45f);
        fill(font, x, y, w, h, NAVY, .96f);
        int fillW = Math.round((w - 2) * p);
        if (fillW > 0) {
            fill(font, x + 1, y + 1, fillW, h - 2, FOCUS_FILL, .92f);
            fill(font, x + 1, y + h - 3, fillW, 2, GOLD);
            if (fillW < w - 2) fill(font, x + fillW, y + 1, 1, h - 2, mix(GOLD, WHITE, .5f), .85f);
        }
        float[] border = done ? GOLD : p > 0 ? mix(GOLD, WHITE, pulse(frame) * .35f) : EDGE;
        outline(font, x, y, w, h, border, 1f, 1);
        int cap = keyCap(font, x + 5, y + 3, key, false);
        compact(font, label, x + 5 + cap + 5, y + 5, p > 0 || done ? WHITE : TEXT);
    }

    /**
     * Description box of compact lines at the active language's line pitch; colours per line.
     * Lines that would cross the bottom edge are not drawn: callers page longer text using
     * {@link #descriptionLines(int)}.
     */
    public static void descriptionBox(PixelFont font, int x, int y, int w, int h,
                                      List<String> lines, List<float[]> colors) {
        fill(font, x, y, w, h, NAVY, .96f);
        outline(font, x, y, w, h, EDGE, 1f, 1);
        fill(font, x + 1, y + 1, w - 2, 1, new float[] {.25f, .42f, .78f}, .6f);
        LineMetrics metrics = compactMetrics();
        int pitch = metrics.lineHeight();
        // Native lettering keeps its historical eight-pixel rows; taller scripts never overflow the box.
        int shown = pitch == 8 ? lines.size() : Math.min(lines.size(), descriptionLines(h));
        for (int i = 0; i < shown; i++) {
            float[] c = i < colors.size() && colors.get(i) != null ? colors.get(i) : MUTED;
            compact(font, fitCompact(lines.get(i), w - 12), x + 6, y + 3 + metrics.above() + i * pitch, c);
        }
    }

    /** Compact lines that fit inside a description box of height {@code h} in the active language. */
    public static int descriptionLines(int h) {
        return Math.max(1, (h - 4) / compactLineHeight());
    }
}
