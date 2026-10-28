package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;

import java.util.List;

/**
 * Shared host-menu chrome for the master title, Game Setup and Settings: key-cap
 * control hints, chips, status boxes, description boxes and focus frames on the
 * native 320x224 grid. Everything is drawn with {@link MenuStyle#fill} and the menu
 * font, so call order is draw order.
 */
public final class MenuChrome {
    private MenuChrome() { }

    public static final int SCREEN_H = 224;
    public static final int HEADER_H = 29;
    public static final int FOOTER_Y = 205;
    public static final int FOOTER_H = SCREEN_H - FOOTER_Y;
    /** Light and bold cell widths; proportional text measures with {@link #compactWidth}/{@link #labelWidth}. */
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
    /** Enabled/healthy state, kept clear of the amber that marks changed values. */
    public static final float[] GREEN = {.45f, .92f, .58f};
    public static final float[] GREEN_TRACK = {.08f, .36f, .22f};

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

    /** Proportional compact (light) text width, in pixels. */
    public static int compactWidth(String text) { return MenuPixelFont.textWidth(text, MenuStyle.COMPACT); }

    /** Proportional label (bold) text width, in pixels. */
    public static int labelWidth(String text) { return MenuPixelFont.textWidth(text, 1f); }

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

    /**
     * Animated selection frame: tinted fill and a two-pixel gold border that breathes.
     * The border's outer corner pixels are left open so the frame reads as rounded
     * at native size rather than as a hard box.
     */
    public static void focus(PixelFont font, int x, int y, int w, int h, int frame) {
        float glow = pulse(frame);
        fill(font, x, y, w, h, FOCUS_FILL, .92f);
        float[] border = mix(GOLD, WHITE, glow * .35f);
        fill(font, x - 1, y - 2, w + 2, 2, border, 1f);
        fill(font, x - 1, y + h, w + 2, 2, border, 1f);
        fill(font, x - 2, y - 1, 2, h + 2, border, 1f);
        fill(font, x + w, y - 1, 2, h + 2, border, 1f);
    }

    /** Standard page: background, header with optional scope badge, and an optional muted subtitle. */
    public static void page(PixelFont font, int width, String title, String scope, String subtitle) {
        background(font, width);
        header(font, width, title, scope);
        if (subtitle != null && !subtitle.isBlank()) {
            compact(font, fitCompact(subtitle, width - 18), 9, HEADER_H + 7, MUTED);
        }
    }

    /**
     * A framed button with a centred label: the focus frame when selected, otherwise a quiet
     * panel. A primary button that is not selected keeps a gold edge so the default action
     * is visible before the player moves to it.
     */
    public static void button(PixelFont font, int x, int y, int w, int h, String text,
                              boolean focused, boolean primary, int frame) {
        if (focused) focus(font, x, y, w, h, frame);
        else {
            panel(font, x, y, w, h);
            if (primary) outline(font, x, y, w, h, mix(GOLD, EDGE, .45f), 1f, 1);
        }
        String shown = fitCompact(text, w - 8);
        compact(font, shown, x + (w - compactWidth(shown)) / 2, y + (h - 8) / 2 + 1,
                focused ? WHITE : primary ? GOLD : TEXT);
    }

    /** On/off switch 15x7: a lit track with the knob at the right when on. */
    public static final int TOGGLE_W = 15;

    public static void toggle(PixelFont font, int x, int y, boolean on, boolean enabled) {
        float a = enabled ? 1f : .45f;
        float[] track = on ? GREEN_TRACK : new float[] {.09f, .14f, .3f};
        fill(font, x + 1, y, TOGGLE_W - 2, 7, track, a);
        fill(font, x, y + 1, TOGGLE_W, 5, track, a);
        float[] edge = on ? GREEN : EDGE;
        fill(font, x + 1, y, TOGGLE_W - 2, 1, edge, a);
        fill(font, x + 1, y + 6, TOGGLE_W - 2, 1, edge, a);
        fill(font, x, y + 1, 1, 5, edge, a);
        fill(font, x + TOGGLE_W - 1, y + 1, 1, 5, edge, a);
        int knob = on ? x + TOGGLE_W - 7 : x + 2;
        fill(font, knob, y + 2, 5, 3, on ? WHITE : DIM, a);
        fill(font, knob + 1, y + 1, 3, 5, on ? WHITE : DIM, a);
    }

    /** A status tag: compact capitals on a tint of their own colour. Returns its width. */
    public static int tag(PixelFont font, int x, int y, String text, float[] c) {
        int w = tagWidth(text);
        fill(font, x, y, w, 10, mix(INK, c, .22f), .95f);
        fill(font, x, y + 9, w, 1, mix(INK, c, .45f), .95f);
        compact(font, text, x + 3, y + 1, c);
        return w;
    }

    public static int tagWidth(String text) { return compactWidth(text) + 6; }

    /** Game badge (S1, S2, S3K) in the game's own colour; returns its width. */
    public static int gameBadge(PixelFont font, int x, int y, String gameId) {
        String text = switch (gameId == null ? "" : gameId) {
            case "s1" -> "S1";
            case "s2" -> "S2";
            case "s3k" -> "S3K";
            default -> gameId == null || gameId.isEmpty() ? "ALL" : gameId.toUpperCase(java.util.Locale.ROOT);
        };
        float[] c = switch (text) {
            case "S1" -> new float[] {.42f, .72f, 1f};
            case "S2" -> new float[] {.55f, .85f, 1f};
            case "S3K" -> new float[] {1f, .55f, .5f};
            default -> MUTED;
        };
        int w = compactWidth(text) + 6;
        fill(font, x, y, w, 10, mix(INK, c, .28f));
        outline(font, x, y, w, 10, mix(INK, c, .7f), 1f, 1);
        compact(font, text, x + 3, y + 1, c);
        return w;
    }

    /** A slim vertical scrollbar: track plus a thumb sized to the visible share. */
    public static void scrollbar(PixelFont font, int x, int top, int height, int first, int visible, int total) {
        if (total <= visible || height <= 0) return;
        int thumbH = Math.max(8, height * visible / total);
        int thumbY = top + (height - thumbH) * first / Math.max(1, total - visible);
        fill(font, x + 1, top, 2, height, EDGE, .6f);
        fill(font, x, thumbY, 4, thumbH, CYAN);
    }

    /** Seven-pixel pixel-art icons drawn with the menu's solid fills. */
    public enum Icon {
        CHECK("00", "01", "02", "44", "28", "10", "00"),
        WARNING("08", "1C", "14", "36", "22", "7F", "77"),
        LOCK("1C", "22", "22", "7F", "77", "77", "7F"),
        FOLDER("70", "4F", "41", "7F", "41", "41", "7F"),
        FILE("3C", "26", "27", "21", "2D", "21", "3F"),
        FILM("7F", "55", "7F", "41", "7F", "55", "7F"),
        PLAY("20", "30", "38", "3C", "38", "30", "20"),
        PUZZLE("0C", "0C", "7F", "61", "3F", "21", "3F"),
        INFO("1C", "08", "00", "18", "08", "08", "1C"),
        DOWNLOAD("08", "08", "2A", "1C", "08", "41", "7F"),
        GEAR("14", "3E", "63", "22", "63", "3E", "14");

        private final int[] rows;

        Icon(String... hex) {
            rows = new int[hex.length];
            for (int i = 0; i < hex.length; i++) rows[i] = Integer.parseInt(hex[i], 16);
        }
    }

    public static final int ICON_SIZE = 7;

    /** Draws {@code icon} with its top-left at (x, y), one fill per horizontal run. */
    public static void icon(PixelFont font, Icon icon, int x, int y, float[] c) {
        for (int row = 0; row < icon.rows.length; row++) {
            int bits = icon.rows[row];
            int col = 0;
            while (col < ICON_SIZE) {
                if ((bits & (1 << (ICON_SIZE - 1 - col))) == 0) { col++; continue; }
                int start = col;
                while (col < ICON_SIZE && (bits & (1 << (ICON_SIZE - 1 - col))) != 0) col++;
                fill(font, x + start, y + row, col - start, 1, c);
            }
        }
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
        return key.toUpperCase(java.util.Locale.ROOT);
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
        background(font, width, 0);
    }

    /** As {@link #background(PixelFont, int)}, with the checkerboard's 32-pixel phase starting at {@code checkerStart}. */
    public static void background(PixelFont font, int width, int checkerStart) {
        fill(font, 0, 0, width, SCREEN_H, new float[] {.012f, .04f, .15f});
        MenuStyle.checkerboard(font, checkerStart, width);
        fill(font, 0, HEADER_H, width, FOOTER_Y - HEADER_H, NAVY, .72f);
    }

    /**
     * The empty page frame (background, header band and footer band) across a wide window, for
     * pages laid out in a centred native column {@code margin} pixels in. The checkerboard keeps
     * the column's phase, so the page drawn over the centre meets it without a seam.
     */
    public static void widePageFrame(PixelFont font, int width, int margin) {
        background(font, width, Math.floorMod(margin, 32) - 32);
        fill(font, 0, 0, width, HEADER_H, HEAD);
        fill(font, 0, HEADER_H - 2, width, 1, GOLD);
        fill(font, 0, HEADER_H - 1, width, 1, new float[] {.4f, .27f, .02f});
        fill(font, 0, FOOTER_Y, width, FOOTER_H, INK);
        fill(font, 0, FOOTER_Y, width, 1, new float[] {.11f, .16f, .37f});
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

    /**
     * A key label for dense rows (Controls bindings): the cap's fill and lit top edge without the
     * outline, nine pixels tall, so neighbouring rows never merge into a stack of boxes.
     * Returns its width.
     */
    public static int keyLabel(PixelFont font, int x, int y, String key) {
        String text = capText(key);
        int w = compactWidth(text) + 6;
        fill(font, x, y, w, 9, new float[] {.1f, .17f, .37f});
        fill(font, x, y, w, 1, new float[] {.4f, .5f, .78f});
        compact(font, text, x + 3, y + 1, WHITE);
        return w;
    }

    public static int keyLabelWidth(String key) { return compactWidth(capText(key)) + 6; }

    /** Top of the three save-prompt buttons; hosts hit-test the same row. */
    public static final int PROMPT_BUTTON_Y = 176;
    public static final int PROMPT_BUTTON_H = 16;

    /**
     * A save prompt over a dimmed page: gold title, the change summary and three buttons
     * (Save, Discard, Keep editing) on {@link #PROMPT_BUTTON_Y}. The box grows upward to fit
     * at most eight summary lines. Summary lines of the form {@code before -> after} draw as
     * the old value, an arrow and the new value; other lines are setting names.
     */
    public static void changePrompt(PixelFont font, int w, String title, List<String> summary, int scroll,
                                    String moreHint, String[] options, int[] xs, int[] widths, int choice, int frame) {
        fill(font, 0, 0, w, SCREEN_H, new float[] {0, 0, 0}, .62f);
        int bw = Math.min(w - 24, 300);
        int bx = (w - bw) / 2;
        int shown = Math.min(8, Math.max(1, summary.size() - scroll));
        int bodyH = shown * 10 + (moreHint != null ? 12 : 0);
        int top = Math.max(45, PROMPT_BUTTON_Y - 10 - bodyH - 30);
        int bottom = PROMPT_BUTTON_Y + PROMPT_BUTTON_H + 6;
        fill(font, bx, top, bw, bottom - top, NAVY, .98f);
        outline(font, bx, top, bw, bottom - top, GOLD, 1f, 1);
        label(font, title, (w - labelWidth(title)) / 2, top + 10, GOLD);
        int y = top + 30;
        for (int i = 0; i < 8 && scroll + i < summary.size(); i++, y += 10) {
            String line = summary.get(scroll + i);
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) {
                compact(font, fitCompact(line, bw - 20), bx + 10, y, TEXT);
                continue;
            }
            String before = fitCompact(line.substring(0, arrow), (bw - 40) / 2);
            String after = fitCompact(line.substring(arrow + 4), (bw - 40) / 2);
            compact(font, before, bx + 18, y, DIM);
            int ax = bx + 18 + compactWidth(before) + 5;
            triangle(font, ax, y + 1, 3, 'R', GOLD, 1f);
            compact(font, after, ax + 8, y, CYAN);
        }
        if (moreHint != null) compact(font, moreHint, bx + 10, y + 2, DIM);
        for (int i = 0; i < options.length; i++) {
            if (i == choice) focus(font, xs[i], PROMPT_BUTTON_Y, widths[i], PROMPT_BUTTON_H, frame);
            else panel(font, xs[i], PROMPT_BUTTON_Y, widths[i], PROMPT_BUTTON_H);
            String text = options[i];
            compact(font, text, xs[i] + (widths[i] - compactWidth(text)) / 2, PROMPT_BUTTON_Y + 5,
                    i == 1 ? AMBER : i == choice ? WHITE : TEXT);
        }
    }

    /** Right edge of the native score/time/rings column in a 320-pixel frame (S1, S2 and S3K). */
    public static final int GAMEPLAY_HUD_RIGHT = 116;

    /**
     * Centred when that clears the native HUD column (centred in wide frames); otherwise it starts
     * just right of the column, so the score digits stay visible while Escape is held.
     */
    public static int holdToastX(int width, int toastWidth) {
        int hudClear = Math.max(0, (width - 320) / 2) + GAMEPLAY_HUD_RIGHT + 4;
        return Math.max(0, Math.min(width - 8 - toastWidth, Math.max((width - toastWidth) / 2, hudClear)));
    }

    /** Width of {@link #holdToast} for a key and label. */
    public static int holdToastWidth(String key, String label) {
        return 5 + keyCapWidth(key) + 5 + compactWidth(label) + 7;
    }

    /**
     * A centred hold-to-confirm toast for shortcuts that work over gameplay: a key cap and
     * label on a navy panel that fills from the left as {@code progress} rises. Holding
     * lights the frame with the focus gold; {@code done} keeps it solid gold while the
     * action runs. Draw it at the top edge, so it never covers the stage's playfield centre, and
     * right of the native HUD column (see {@link #holdToastX}).
     */
    public static void holdToast(PixelFont font, int width, int y, String key, String label,
                                 float progress, boolean done, int frame) {
        float p = Math.clamp(progress, 0f, 1f);
        int w = holdToastWidth(key, label);
        int h = HOLD_TOAST_H;
        int x = holdToastX(width, w);
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

    /** Line pitch of description text: the 8 px light face plus one pixel of leading. */
    public static final int DESCRIPTION_PITCH = 9;

    /** Description box with up to three compact lines; colours per line. */
    public static void descriptionBox(PixelFont font, int x, int y, int w, int h,
                                      List<String> lines, List<float[]> colors) {
        fill(font, x, y, w, h, NAVY, .96f);
        outline(font, x, y, w, h, EDGE, 1f, 1);
        fill(font, x + 1, y + 1, w - 2, 1, new float[] {.25f, .42f, .78f}, .6f);
        for (int i = 0; i < lines.size(); i++) {
            float[] c = i < colors.size() && colors.get(i) != null ? colors.get(i) : MUTED;
            compact(font, fitCompact(lines.get(i), w - 12), x + 6, y + 3 + i * DESCRIPTION_PITCH, c);
        }
    }
}
