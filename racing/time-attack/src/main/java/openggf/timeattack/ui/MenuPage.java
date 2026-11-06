package openggf.timeattack.ui;

import com.openggf.control.ButtonPrompts;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.mods.ui.TextLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Time Attack pages' shared look, drawn only through the public scene canvas and fonts: a
 * header band with the bold title and an optional scope chip, light {@link NameFont} rows and
 * prose wrapped by measured width, a gold-edged focus band, and a footer of key caps. It echoes
 * the engine's menus (navy page, gold rule, caps footer) so the bundled mode reads as part of
 * the product, while staying inside the creator surface a mod may use.
 *
 * <p>Roles: the bold menu face ({@link SceneCanvas#text}) is for page titles and the primary
 * launch button; rows, values and sentences use the light {@link NameFont}; key caps, tags and
 * card captions use the compact capitals of {@link CompactFont}. Every width comes from the face
 * that draws the text.
 */
public final class MenuPage {
    private MenuPage() { }

    public static final int INK = 0xFF02081C;
    public static final int NAVY = 0xFF05123B;
    public static final int HEAD = 0xFF0A245C;
    public static final int BUTTON = 0xFF0D2961;
    public static final int EDGE = 0xFF2B4F9E;
    public static final int FOCUS_FILL = 0xFF1C4AA8;
    public static final int GOLD = 0xFFFFD64D;
    public static final int CYAN = 0xFF80E8FF;
    public static final int AMBER = 0xFFFFB840;
    public static final int RED = 0xFFFF6B6B;
    public static final int GREEN = 0xFF73EB94;
    public static final int TEXT = 0xFFE8EDFF;
    public static final int MUTED = 0xFFB8C4E6;
    public static final int DIM = 0xFF8794BD;
    public static final int FAINT = 0xFF56628A;

    private static final int BACKGROUND = 0x030A26;
    private static final int CHECKER = 0x14000000 | 0x3A5BA8;
    private static final int BODY = 0xB805123B;
    private static final int CAP_FACE = 0xFF1A2B5E;
    private static final int CAP_EDGE = 0xFFC9D4F5;
    private static final int CAP_SHADOW = 0xFF00050F;

    public static final int HEADER_H = 29;
    public static final int FOOTER_Y = 205;
    /** First row of the page body (under the header and its subtitle). */
    public static final int BODY_TOP = 48;
    /** Height of a light row: one {@link NameFont} line box (its accent space sits above the letters). */
    public static final int ROW_H = NameFont.LINE_HEIGHT;
    public static final int MARGIN = 8;

    /** Keyboard arrow pairs drawn as triangles inside a cap. */
    public static final String ARROWS_LR = "LEFT/RIGHT";
    public static final String ARROWS_UD = "UP/DOWN";

    /**
     * One footer hint: a key cap and what it does here. A shortcut repeats an action the page also
     * offers as a row (J for Join by code), so a narrow footer leaves it out before the others.
     */
    public record Hint(String key, String label, boolean shortcut) {
        public Hint {
            // An empty key (a key the player left unbound) is a hint footers leave out.
            key = key == null ? "" : key;
            label = label == null ? "" : label;
        }

        public Hint(String key, String label) { this(key, label, false); }
    }

    public static Hint hint(String key, String label) { return new Hint(key, label); }

    /** A hint for a key that repeats one of the page's rows; see {@link Hint}. */
    public static Hint shortcut(String key, String label) { return new Hint(key, label, true); }

    /**
     * Which device names the keys: true once the last button or key press this view saw came from a
     * controller, false after a keyboard press; unchanged on a quiet tick.
     */
    public static boolean controller(ViewInput input, boolean previous) {
        boolean result = previous;
        var physical = input.physicalInput();
        if (physical == null) return result;
        for (PhysicalInputEvent event : physical.events()) {
            if (!event.pressed()) continue;
            if (event.kind() == PhysicalInputEvent.Kind.KEY) result = false;
            else if (event.kind() == PhysicalInputEvent.Kind.BUTTON) result = true;
        }
        return result;
    }

    /**
     * The keys a page names this tick, for the device pressed last ({@link #controller}). On a
     * keyboard confirm is Enter and back is Escape, the keys the views read beside the Genesis
     * buttons, unless the player bound Enter to B or Escape to a confirming button: then the bound
     * keys say what they do ({@link ViewInput#of}). On a controller each label is the physical
     * button that produces Genesis A, B and Start on the player's pad family, from the host's live
     * bindings ({@link ViewInput#buttonLabel}); without live input the Genesis names stand in.
     * {@code select} names the row confirm when Enter belongs to Play (the keyboard's bound A or C,
     * empty when neither is bound). An empty key is left out of a footer.
     */
    public record Keys(boolean pad, String confirm, String back, String start, String select) {
        /** Before any input: the keyboard's own keys. */
        public static Keys keyboard() { return new Keys(false, "ENTER", "ESC", "ENTER", ""); }

        /** The details key: F1 on a keyboard, R3 (the right stick press) on any controller. */
        public String details() { return pad ? "R3" : "F1"; }

        public static Keys of(ViewInput input, Keys previous) {
            boolean pad = controller(input, previous == null ? false : previous.pad());
            ButtonPrompts.Button a = ButtonPrompts.Button.A;
            ButtonPrompts.Button c = ButtonPrompts.Button.C;
            if (pad) {
                String confirm = label(input, a, true, "A");
                return new Keys(true, confirm, label(input, ButtonPrompts.Button.B, true, "B"),
                        label(input, ButtonPrompts.Button.START, true, "START"), confirm);
            }
            String select = label(input, a, false, label(input, c, false, ""));
            boolean enterBack = input.enterGoesBack(SceneKeys.ENTER);
            String confirm = enterBack ? (select.isEmpty() ? label(input, ButtonPrompts.Button.START, false, "")
                    : select) : "ENTER";
            String back = input.escapeConfirms() ? label(input, ButtonPrompts.Button.B, false, "") : "ESC";
            String start = enterBack ? label(input, ButtonPrompts.Button.START, false, "") : "ENTER";
            return new Keys(false, confirm, back, start, select);
        }

        private static String label(ViewInput input, ButtonPrompts.Button button, boolean controller, String fallback) {
            return input.buttonLabel(button, controller).orElse(fallback);
        }
    }

    // ------------------------------------------------------------------ page

    /** The page background, header band with {@code title}, optional scope chip and subtitle. */
    public static void page(SceneCanvas canvas, String title, String scope, String subtitle) {
        canvas.clear(BACKGROUND);
        int width = canvas.width();
        for (int y = 0; y < 224; y += 32) {
            for (int x = ((y / 32) % 2) * 32; x < width; x += 64) {
                canvas.fill(x, y, Math.min(32, width - x), Math.min(32, 224 - y), CHECKER);
            }
        }
        canvas.fill(0, HEADER_H, width, FOOTER_Y - HEADER_H, BODY);
        header(canvas, title, scope);
        subtitle(canvas, subtitle);
    }

    /** As {@link #page}, without the opaque background: the page is drawn over a running level. */
    public static void overlay(SceneCanvas canvas, String title, String scope, String subtitle) {
        canvas.clear(BACKGROUND);
        header(canvas, title, scope);
        subtitle(canvas, subtitle);
    }

    private static void header(SceneCanvas canvas, String title, String scope) {
        int width = canvas.width();
        canvas.fill(0, 0, width, HEADER_H, HEAD);
        canvas.fill(0, HEADER_H - 2, width, 1, GOLD);
        canvas.fill(0, HEADER_H - 1, width, 1, 0xFF664405);
        int scopeWidth = scope == null || scope.isEmpty() ? 0 : tagWidth(scope) + 8;
        String shown = TextLayout.ellipsis(title, width - 2 * MARGIN - scopeWidth, canvas::textWidth);
        canvas.text(shown, MARGIN + 1, 10, GOLD);
        if (scopeWidth > 0) tag(canvas, width - MARGIN - tagWidth(scope), 9, scope, CYAN);
    }

    private static void subtitle(SceneCanvas canvas, String subtitle) {
        if (subtitle == null || subtitle.isBlank()) return;
        NameFont.draw(canvas, NameFont.fit(subtitle, canvas.width() - 2 * MARGIN), MARGIN + 1, HEADER_H + 3, MUTED);
    }

    // ------------------------------------------------------------------ rows

    /** The focus band: a blue fill with a one-pixel gold edge whose corners stay open. */
    public static void focus(PixelCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, FOCUS_FILL);
        canvas.fill(x + 1, y - 1, w - 2, 1, GOLD);
        canvas.fill(x + 1, y + h, w - 2, 1, GOLD);
        canvas.fill(x - 1, y + 1, 1, h - 2, GOLD);
        canvas.fill(x + w, y + 1, 1, h - 2, GOLD);
    }

    /** Width of the label column for {@code labels}: the widest label and a gap. */
    public static int labelColumn(List<String> labels) {
        int widest = 0;
        for (String label : labels) widest = Math.max(widest, NameFont.width(label));
        return widest + 12;
    }

    /**
     * A light field row at {@code y}: the label, then from {@code valueX} the value. A stepper
     * shows triangles either side of the value (gold while focused). Returns the value's x.
     */
    public static int field(SceneCanvas canvas, int y, int valueX, String label, String value, int valueColour,
                            boolean focused, boolean stepper) {
        int width = canvas.width();
        if (focused) focus(canvas, MARGIN, y, width - 2 * MARGIN, ROW_H);
        NameFont.draw(canvas, label, MARGIN + 6, y, focused ? TEXT : MUTED);
        int room = width - MARGIN - 6 - valueX - (stepper ? 18 : 0);
        String shown = NameFont.fit(value, Math.max(0, room));
        int x = valueX;
        if (stepper) {
            triangle(canvas, x, y + 4, 'L', focused ? GOLD : DIM);
            x += 9;
        }
        NameFont.draw(canvas, shown, x, y, valueColour);
        if (stepper) triangle(canvas, x + NameFont.width(shown) + 5, y + 4, 'R', focused ? GOLD : DIM);
        return x;
    }

    /** A light action row: a chevron and the label; disabled rows are faint. */
    public static void action(SceneCanvas canvas, int y, String label, boolean focused, boolean enabled) {
        int width = canvas.width();
        if (focused) focus(canvas, MARGIN, y, width - 2 * MARGIN, ROW_H);
        chevron(canvas, MARGIN + 6, y + 4, focused ? GOLD : enabled ? DIM : FAINT);
        NameFont.draw(canvas, NameFont.fit(label, width - 2 * MARGIN - 20), MARGIN + 14, y,
                enabled ? (focused ? TEXT : MUTED) : FAINT);
    }

    /** Height of {@link #button}. */
    public static final int BUTTON_H = 16;

    /**
     * A framed button with a centred label: the focus band when selected, otherwise a quiet panel.
     * A primary (launch) button draws its label in the bold menu face and keeps a gold edge, so the
     * action shows before focus reaches it; a secondary button (Back, Cancel) uses the light row face
     * in sentence case, as its rows do.
     */
    public static void button(SceneCanvas canvas, int x, int y, int w, String label, boolean focused,
                              boolean primary) {
        if (focused) focus(canvas, x, y, w, BUTTON_H);
        else {
            canvas.fill(x, y, w, BUTTON_H, BUTTON);
            outline(canvas, x, y, w, BUTTON_H, primary ? 0xFFB59A3F : EDGE);
        }
        if (primary) {
            String shown = TextLayout.ellipsis(label, w - 8, canvas::textWidth);
            canvas.text(shown, x + (w - canvas.textWidth(shown)) / 2, y + 3, GOLD);
        } else {
            String text = sentence(label);
            NameFont.draw(canvas, text, x + (w - NameFont.width(text)) / 2, y + 2, focused ? TEXT : MUTED);
        }
    }

    /** A two-way switch at {@code right}: compact capitals, the chosen side lit. Returns its left x. */
    public static int segments(PixelCanvas canvas, int right, int y, String first, String second,
                               boolean secondChosen, boolean focused) {
        int secondWidth = CompactFont.width(second, 1) + 10;
        int firstWidth = CompactFont.width(first, 1) + 10;
        int secondX = right - secondWidth;
        int firstX = secondX - 1 - firstWidth;
        int lit = focused ? GOLD : CYAN;
        canvas.fill(firstX, y, firstWidth, 11, secondChosen ? INK : 0xFF24418A);
        canvas.fill(secondX, y, secondWidth, 11, secondChosen ? 0xFF24418A : INK);
        outline(canvas, firstX, y, firstWidth + 1 + secondWidth, 11, EDGE);
        CompactFont.draw(canvas, first, firstX + 5, y + 2, 1, secondChosen ? DIM : lit);
        CompactFont.draw(canvas, second, secondX + 5, y + 2, 1, secondChosen ? lit : DIM);
        return firstX;
    }

    /** A thin scroll bar from {@code top} spanning {@code height}. */
    public static void scrollbar(PixelCanvas canvas, int x, int top, int height, int first, int visible, int total) {
        if (total <= visible) return;
        canvas.fill(x, top, 2, height, 0xFF1A2B5E);
        int thumb = Math.max(6, height * visible / total);
        int y = top + (height - thumb) * first / Math.max(1, total - visible);
        canvas.fill(x, y, 2, thumb, CYAN);
    }

    // ------------------------------------------------------------------ text

    /** {@link NameFont} lines no wider than {@code width}; a word too wide is split between graphemes. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String line : TextLayout.wrap(text, width, NameFont::width)) {
            if (NameFont.width(line) <= width) lines.add(line);
            else lines.addAll(TextEntry.wrapPixels(line, width));
        }
        return lines;
    }

    /** Draws wrapped {@link NameFont} text from {@code y}, at most {@code maxLines}; returns the next y. */
    public static int paragraph(PixelCanvas canvas, String text, int x, int y, int width, int maxLines, int argb) {
        List<String> lines = wrap(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) line = NameFont.fit(line + " " + lines.get(i + 1), width);
            NameFont.draw(canvas, line, x, y, argb);
            y += NameFont.LINE_HEIGHT;
        }
        return y;
    }

    /** What a {@link #notice} reports, which picks its icon and colour. */
    public enum Tone { INFO, GOOD, WARNING, ERROR }

    /** An icon and wrapped sentence (at most two lines) from {@code y}; returns the next y. */
    public static int notice(PixelCanvas canvas, Tone tone, String text, int x, int y, int width) {
        if (text == null || text.isBlank()) return y;
        int colour = colour(tone);
        icon(canvas, tone, x, y + 3);
        return paragraph(canvas, text, x + 10, y, width - 10, 2, tone == Tone.INFO ? MUTED : colour);
    }

    public static int colour(Tone tone) {
        return switch (tone) {
            case INFO -> CYAN;
            case GOOD -> GREEN;
            case WARNING -> AMBER;
            case ERROR -> RED;
        };
    }

    /** Lines a notice of {@code text} takes at {@code width}. */
    public static int noticeLines(String text, int width) {
        return text == null || text.isBlank() ? 0 : Math.min(2, wrap(text, width - 10).size());
    }

    /** A section caption: compact cyan capitals and a rule to the right edge of {@code width}. */
    public static void section(PixelCanvas canvas, String text, int x, int y, int width) {
        String caps = text.toUpperCase(Locale.ROOT);
        CompactFont.draw(canvas, caps, x, y, 1, CYAN);
        int start = x + CompactFont.width(caps, 1) + 5;
        if (start < x + width) canvas.fill(start, y + 3, x + width - start, 1, 0xFF24418A);
    }

    /** A compact-capital tag in an outlined box, 11 pixels tall. Returns its width. */
    public static int tag(PixelCanvas canvas, int x, int y, String text, int argb) {
        String caps = text.toUpperCase(Locale.ROOT);
        int w = tagWidth(caps);
        canvas.fill(x, y, w, 11, INK);
        outline(canvas, x, y, w, 11, argb);
        CompactFont.draw(canvas, caps, x + 4, y + 2, 1, argb);
        return w;
    }

    public static int tagWidth(String text) { return CompactFont.width(text.toUpperCase(Locale.ROOT), 1) + 8; }

    /** A label as a sentence-case value: "Full Chat" or "FULL CHAT" to "Full chat". */
    public static String sentence(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String lower = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Catalogue capitals as words ({@code GREEN HILL 1} reads Green Hill 1); other text unchanged. */
    public static String words(String text) {
        if (text == null || text.isEmpty() || !text.equals(text.toUpperCase(Locale.ROOT))) return text;
        StringBuilder out = new StringBuilder(text.length());
        boolean start = true;
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ' || c == '-' || c == '/';
        }
        return out.toString();
    }

    /** A game's display name for its id ({@code s1}, {@code s2}, {@code s3k}). */
    public static String gameName(String gameId) {
        return switch (gameId == null ? "" : gameId.toLowerCase(Locale.ROOT)) {
            case "s1" -> "Sonic 1";
            case "s2" -> "Sonic 2";
            case "s3k" -> "Sonic 3 & Knuckles";
            default -> gameId == null ? "" : gameId.toUpperCase(Locale.ROOT);
        };
    }

    // ------------------------------------------------------------------ footer

    /** Width {@link #hints} needs. */
    public static int hintsWidth(List<Hint> hints) {
        int width = 0;
        for (Hint hint : hints) {
            if (hint.key().isEmpty()) continue;
            if (width > 0) width += 10;
            width += capWidth(hint.key()) + (hint.label().isEmpty() ? 0 : 4 + labelWidth(hint.label()));
        }
        return width;
    }

    private static int labelWidth(String label) { return CompactFont.width(label.toUpperCase(Locale.ROOT), 1); }

    /** Draws hints left to right from {@code x}. */
    public static void hints(PixelCanvas canvas, int x, int y, List<Hint> hints, int labelColour) {
        int cursor = x;
        for (Hint hint : hints) {
            if (hint.key().isEmpty()) continue;
            cursor += cap(canvas, cursor, y, hint.key());
            if (!hint.label().isEmpty()) {
                CompactFont.draw(canvas, hint.label().toUpperCase(Locale.ROOT), cursor + 4, y + 2, 1, labelColour);
                cursor += 4 + labelWidth(hint.label());
            }
            cursor += 10;
        }
    }

    /**
     * The footer band: an optional status on the left in {@code statusColour} and the hints at the
     * right. When the hints would meet the status, {@link #fitHints} leaves out the arrow hints
     * first (the rows' own triangles and the focus band already show them); the status then takes
     * the remaining width.
     */
    public static void footer(PixelCanvas canvas, String status, int statusColour, List<Hint> hints) {
        int width = canvas.width();
        canvas.fill(0, FOOTER_Y, width, 224 - FOOTER_Y, INK);
        canvas.fill(0, FOOTER_Y, width, 1, 0xFF1C2960);
        int statusRoom = status == null || status.isEmpty() ? 0 : Math.min(NameFont.width(status) + 12, width / 2);
        List<Hint> shown = fitHints(hints, width - 2 * MARGIN - statusRoom);
        int hintsX = width - MARGIN - hintsWidth(shown);
        hints(canvas, hintsX, FOOTER_Y + 4, shown, TEXT);
        if (statusRoom > 0) {
            NameFont.draw(canvas, NameFont.fit(status, hintsX - MARGIN - 8), MARGIN, FOOTER_Y + 3, statusColour);
        }
    }

    /**
     * The hints that fit in {@code room}, in their order. Up/Down goes first, then shortcuts that
     * repeat a row, then Left/Right (the focus band, the rows and their triangles already offer
     * them); the action, Details and Back hints stay. Only a footer too narrow even for those loses
     * hints from the left.
     */
    public static List<Hint> fitHints(List<Hint> hints, int room) {
        List<Hint> shown = new ArrayList<>(hints);
        shown.removeIf(hint -> hint.key().isEmpty());
        List<java.util.function.Predicate<Hint>> optional = List.of(hint -> hint.key().equals(ARROWS_UD),
                Hint::shortcut, hint -> hint.key().equals(ARROWS_LR));
        for (var drop : optional) {
            if (hintsWidth(shown) <= room) return shown;
            shown.removeIf(drop);
        }
        while (!shown.isEmpty() && hintsWidth(shown) > room) shown.remove(0);
        return shown;
    }

    /** Adds the details action beside Back, where narrow footers keep it visible. */
    public static void footerWithDetails(PixelCanvas canvas, Keys keys, String status, int statusColour,
                                         List<Hint> hints) {
        footer(canvas, status, statusColour, withDetails(keys, hints));
    }

    /** {@code hints} with the details action before the last (Back) hint. */
    public static List<Hint> withDetails(Keys keys, List<Hint> hints) {
        List<Hint> complete = new ArrayList<>(hints);
        complete.add(Math.max(0, complete.size() - 1), hint(keys.details(), "Details"));
        return complete;
    }

    /** Width of a key cap. */
    public static int capWidth(String key) {
        if (ARROWS_LR.equals(key) || ARROWS_UD.equals(key)) return 19;
        return Math.max(11, CompactFont.width(key.toUpperCase(Locale.ROOT), 1) + 6);
    }

    /** Draws a key cap, 11 pixels tall, with its top-left at (x, y); returns its width. */
    public static int cap(PixelCanvas canvas, int x, int y, String key) {
        int w = capWidth(key);
        canvas.fill(x, y + 1, w, 10, CAP_SHADOW);
        canvas.fill(x, y, w, 10, CAP_FACE);
        outline(canvas, x, y, w, 10, CAP_EDGE);
        if (ARROWS_LR.equals(key)) {
            triangle(canvas, x + 3, y + 2, 'L', 0xFFFFFFFF);
            triangle(canvas, x + w - 7, y + 2, 'R', 0xFFFFFFFF);
        } else if (ARROWS_UD.equals(key)) {
            triangle(canvas, x + 3, y + 2, 'U', 0xFFFFFFFF);
            triangle(canvas, x + w - 9, y + 3, 'D', 0xFFFFFFFF);
        } else {
            String text = key.toUpperCase(Locale.ROOT);
            CompactFont.draw(canvas, text, x + (w - CompactFont.width(text, 1)) / 2, y + 2, 1, 0xFFFFFFFF);
        }
        return w;
    }

    // ------------------------------------------------------------------ icons

    public static void outline(PixelCanvas canvas, int x, int y, int w, int h, int argb) {
        canvas.fill(x, y, w, 1, argb);
        canvas.fill(x, y + h - 1, w, 1, argb);
        canvas.fill(x, y + 1, 1, h - 2, argb);
        canvas.fill(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** A solid triangle in a 4x7 (left/right) or 7x4 (up/down) box. */
    public static void triangle(PixelCanvas canvas, int x, int y, char direction, int argb) {
        for (int i = 0; i < 4; i++) {
            int span = 7 - 2 * i;
            switch (direction) {
                case 'L' -> canvas.fill(x + 3 - i, y + i, 1, span, argb);
                case 'R' -> canvas.fill(x + i, y + i, 1, span, argb);
                case 'U' -> canvas.fill(x + i, y + 3 - i, span, 1, argb);
                default -> canvas.fill(x + i, y + i, span, 1, argb);
            }
        }
    }

    /** A three-pixel chevron pointing right, 4x7. */
    public static void chevron(PixelCanvas canvas, int x, int y, int argb) {
        for (int i = 0; i < 4; i++) {
            canvas.fill(x + i, y + i, 1, 1, argb);
            canvas.fill(x + i, y + 6 - i, 1, 1, argb);
        }
    }

    /** A 7x7 status icon: i (info), a check (good), ! (warning) or a cross (error). */
    public static void icon(PixelCanvas canvas, Tone tone, int x, int y) {
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

    /** A 7x8 padlock. */
    public static void lock(PixelCanvas canvas, int x, int y, int argb) {
        canvas.fill(x + 2, y, 3, 1, argb);
        canvas.fill(x + 1, y + 1, 1, 3, argb);
        canvas.fill(x + 5, y + 1, 1, 3, argb);
        canvas.fill(x, y + 3, 7, 5, argb);
        canvas.fill(x + 3, y + 5, 1, 2, INK);
    }

    /** An 8x8 check box, filled when {@code on}. */
    public static void checkbox(PixelCanvas canvas, int x, int y, boolean on, int argb) {
        outline(canvas, x, y, 8, 8, argb);
        if (on) canvas.fill(x + 2, y + 2, 4, 4, argb);
    }
}
