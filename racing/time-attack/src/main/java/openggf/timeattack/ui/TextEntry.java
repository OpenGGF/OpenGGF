package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.net.identity.DisplayNames;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A modal text field (LAN invites, chat, display name, master URL, port). Characters come from
 * the scene's {@linkplain ViewInput#typedText typed text}, so the player's keyboard layout and
 * input method decide what a key types; keys only edit: Enter accepts, Escape cancels and
 * Backspace deletes the last grapheme (a letter with its accents, or a whole emoji). It reads
 * keys, never controller buttons: Space and Backspace are mapped to pad buttons by default, so a
 * button-driven field would confirm while the player types.
 *
 * <p>Three kinds of field:
 * <ul>
 *   <li>{@link Kind#ASCII}: ASCII letters, digits and listed extras, for addresses, ports and URLs
 *       whose parsers expect exactly those characters.</li>
 *   <li>{@link Kind#TEXT}: visible text in any script (chat).</li>
 *   <li>{@link Kind#NAME}: a display name; it is accepted only when {@link DisplayNames} leaves a
 *       non-empty canonical name, which {@link #acceptedName()} returns.</li>
 * </ul>
 * Lengths are counted in code points. Unicode fields draw with {@link NameFont}, so every script
 * the bundled fonts cover is visible while typing.
 */
public final class TextEntry {
    /** What the last {@link #update} did. */
    public enum Result { NONE, ACCEPTED, CANCELLED }

    /** Which characters the field takes. */
    public enum Kind { ASCII, TEXT, NAME, PASSWORD }

    private static final int TEXT = 0xFFFFFFFF;

    private final String title;
    private final Kind kind;
    private final int maxLength;
    private final String allowedExtraChars;
    private final StringBuilder text = new StringBuilder();
    private int codePoints;
    private String prompt = "";
    private String error = "";
    private boolean masked;
    private boolean rejectedPasswordInput;
    private final FullTextReader reader = new FullTextReader();
    private boolean pad;

    /** An {@link Kind#ASCII} field: letters, digits and {@code allowedExtraChars}, at most {@code maxLength}. */
    public TextEntry(String title, String initial, int maxLength, String allowedExtraChars) {
        this(title, initial, Kind.ASCII, maxLength, allowedExtraChars);
    }

    private TextEntry(String title, String initial, Kind kind, int maxLength, String allowedExtraChars) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be non-negative");
        }
        this.title = Objects.requireNonNull(title, "title");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.maxLength = maxLength;
        this.allowedExtraChars = Objects.requireNonNull(allowedExtraChars, "allowedExtraChars");
        setText(initial == null ? "" : initial);
    }

    /**
     * A {@link Kind#TEXT} field of at most {@code maxLength} code points. The UTF-16 length is
     * held to the same limit, because hosts cut chat at that many Java characters and a cut
     * through a surrogate pair would corrupt the last character.
     */
    public static TextEntry text(String title, String initial, int maxLength) {
        return new TextEntry(title, initial, Kind.TEXT, maxLength, "");
    }

    /** A {@link Kind#NAME} field, limited as {@link DisplayNames} limits names. */
    public static TextEntry name(String title, String initial) {
        return new TextEntry(title, initial, Kind.NAME, DisplayNames.MAX_CODE_POINTS, "");
    }

    public static TextEntry password(String title) {
        return new TextEntry(title, "", Kind.PASSWORD, 128, "").masked();
    }

    public TextEntry masked() {
        masked = true;
        return this;
    }

    public void clear() {
        for (int i = 0; i < text.length(); i++) text.setCharAt(i, '\0');
        text.setLength(0);
        codePoints = 0;
        error = "";
        rejectedPasswordInput = false;
    }

    /** A line drawn under the title, explaining what to enter. */
    public TextEntry withPrompt(String value) {
        prompt = Objects.requireNonNullElse(value, "");
        return this;
    }

    public Kind kind() {
        return kind;
    }

    /** Appends {@code value} when the field allows it and has room. */
    public void feedChar(char value) {
        feedCodePoint(value);
    }

    /** Appends one code point when the field allows it and has room; true when it was added. */
    public boolean feedCodePoint(int codePoint) {
        if (kind == Kind.PASSWORD) {
            if (rejectedPasswordInput) return false;
            if (!allowed(codePoint) || codePoints >= maxLength) {
                rejectedPasswordInput = true;
                error = "Password input refused. Backspace clears it.";
                return false;
            }
        }
        int value = codePoint;
        if ((kind == Kind.TEXT || kind == Kind.NAME) && value != ' '
                && (Character.isWhitespace(value) || Character.isSpaceChar(value))) {
            value = ' '; // an IME's ideographic space types an ordinary one
        }
        if (!allowed(value) || codePoints >= maxLength) {
            return false;
        }
        int units = Character.charCount(value);
        if (kind == Kind.TEXT && text.length() + units > maxLength) {
            return false;
        }
        if (kind == Kind.NAME && utf8Length() + utf8Length(value) > DisplayNames.MAX_UTF8_BYTES) {
            return false;
        }
        text.appendCodePoint(value);
        codePoints++;
        return true;
    }

    private boolean allowed(int value) {
        if (kind == Kind.PASSWORD) {
            return Character.isValidCodePoint(value) && (value < 0xD800 || value > 0xDFFF);
        }
        if (kind == Kind.ASCII) {
            return (value < 128 && Character.isLetterOrDigit(value)) || allowedExtraChars.indexOf(value) >= 0;
        }
        if (kind == Kind.NAME && (value == '"' || value == '\\')) {
            return false;
        }
        return switch (Character.getType(value)) {
            case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                 Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }

    /** Removes the last grapheme: a base character with its marks, or a whole emoji sequence. */
    public void backspace() {
        if (text.isEmpty()) {
            return;
        }
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        String value = text.toString();
        graphemes.setText(value);
        int start = graphemes.preceding(value.length());
        if (start == BreakIterator.DONE) {
            start = 0;
        }
        codePoints -= value.codePointCount(start, value.length());
        text.setLength(start);
    }

    public String text() {
        return text.toString();
    }

    /** Length in code points. */
    public int length() {
        return codePoints;
    }

    public int maxLength() {
        return maxLength;
    }

    /** The canonical name for a {@link Kind#NAME} field; empty when nothing displayable was typed. */
    public String acceptedName() {
        return DisplayNames.sanitize(text.toString());
    }

    /** Replaces the text, filtering and clamping it as typing would. */
    public void setText(String value) {
        Objects.requireNonNull(value, "value");
        clear();
        value.codePoints().forEach(this::feedCodePoint);
    }

    public String title() {
        return title;
    }

    /** The validation message shown after a refused Enter, or empty. */
    public String error() {
        return error;
    }

    /** One tick of input. */
    public Result update(ViewInput input) {
        Objects.requireNonNull(input, "input");
        pad = MenuPage.controller(input, pad);
        if (reader.handle(input, this::detailsText)) return Result.NONE;
        if (input.keyPressed(SceneKeys.ESCAPE)) {
            return Result.CANCELLED;
        }
        if (input.keyPressed(SceneKeys.ENTER) || input.keyPressed(SceneKeys.KP_ENTER)) {
            if (rejectedPasswordInput) return Result.NONE;
            if (kind == Kind.NAME && !DisplayNames.isCanonical(acceptedName())) {
                error = "Type at least one visible character";
                return Result.NONE;
            }
            return Result.ACCEPTED;
        }
        if (input.keyPressed(SceneKeys.BACKSPACE)) {
            if (rejectedPasswordInput) clear();
            else backspace();
            error = "";
        }
        String typed = input.typedText();
        if (!typed.isEmpty()) {
            typed.codePoints().forEach(this::feedCodePoint);
            if (!rejectedPasswordInput) error = "";
        }
        return Result.NONE;
    }

    /**
     * Draws the editor over the whole screen. Every value wraps by measured {@link NameFont} width so
     * each character stays visible; masked fields show a fixed row of dots, never the length.
     */
    public void draw(SceneCanvas canvas) {
        if (reader.draw(canvas)) return;
        int width = canvas.width();
        MenuPage.page(canvas, title, null, null);
        int y = MenuPage.HEADER_H + 5;
        if (!prompt.isEmpty()) y = MenuPage.paragraph(canvas, prompt, MenuPage.MARGIN + 1, y, width - 18, 2,
                MenuPage.MUTED) + 4;
        else y += 4;
        int boxWidth = width - 2 * MenuPage.MARGIN;
        int inner = boxWidth - 12;
        int bottom = error.isEmpty() ? 182 : 164;
        int boxHeight;
        if (masked) {
            boxHeight = 21;
            box(canvas, y, boxHeight);
            if (text.isEmpty()) {
                caret(canvas, MenuPage.MARGIN + 6, y + 5);
                NameFont.draw(canvas, "Nothing entered yet", MenuPage.MARGIN + 10, y + 4, MenuPage.FAINT);
            } else {
                // A fixed row of dots: the length is never shown.
                for (int i = 0; i < 8; i++) canvas.fill(MenuPage.MARGIN + 8 + i * 8, y + 9, 4, 4, TEXT);
                caret(canvas, MenuPage.MARGIN + 8 + 8 * 8, y + 5);
            }
        } else if (kind == Kind.NAME && NameFont.width(text() + "_") * 2 <= inner) {
            // A name is short: show it at double size when it fits, as it will appear to others.
            boxHeight = NameFont.LINE_HEIGHT * 2 + 8;
            box(canvas, y, boxHeight);
            NameText.draw(canvas, text(), MenuPage.MARGIN + 6, y + 4, 2, TEXT);
            caret(canvas, MenuPage.MARGIN + 6 + NameFont.width(text()) * 2 + 1, y + 8, 18);
            if (text.isEmpty()) NameFont.draw(canvas, "Nothing entered yet", MenuPage.MARGIN + 10, y + 11, MenuPage.FAINT);
        } else {
            // Words wrap at spaces and addresses after a separator, as the details page reads them.
            List<String> lines = FullTextReader.wrap(text(), inner - 3);
            int fit = Math.max(1, (bottom - y - 8) / NameFont.LINE_HEIGHT);
            List<String> shown = lines.subList(Math.max(0, lines.size() - fit), lines.size());
            boxHeight = shown.size() * NameFont.LINE_HEIGHT + 8;
            box(canvas, y, boxHeight);
            int lineY = y + 4;
            for (String line : shown) {
                NameFont.draw(canvas, line, MenuPage.MARGIN + 6, lineY, TEXT);
                lineY += NameFont.LINE_HEIGHT;
            }
            caret(canvas, MenuPage.MARGIN + 6 + NameFont.width(shown.getLast()) + 1, lineY - NameFont.LINE_HEIGHT + 2);
            if (text.isEmpty()) NameFont.draw(canvas, "Nothing entered yet", MenuPage.MARGIN + 10, y + 4, MenuPage.FAINT);
        }
        int below = y + boxHeight + 3;
        String count = masked ? "HIDDEN" : codePoints + "/" + maxLength;
        int countWidth = CompactFont.width(count, 1);
        CompactFont.draw(canvas, count, width - MenuPage.MARGIN - countWidth, below + 2, 1, MenuPage.DIM);
        // Notes under the field, in reading order: what it takes (whole, wrapped), then a refusal.
        int noteY = below + 11;
        String allowed = allowedExtraChars.isEmpty() ? "" : allowedText();
        if (!allowed.isEmpty()) {
            noteY = MenuPage.paragraph(canvas, allowed, MenuPage.MARGIN + 1, below - 1,
                    width - 2 * MenuPage.MARGIN - countWidth - 10, 3, MenuPage.DIM) + 2;
        }
        if (!error.isEmpty()) {
            MenuPage.notice(canvas, MenuPage.Tone.ERROR, error, MenuPage.MARGIN + 1, noteY, width - 18);
        }
        MenuPage.footerWithDetails(canvas, pad, "", MenuPage.TEXT, List.of(MenuPage.hint("BKSP", "Delete"),
                MenuPage.hint("ENTER", "Done"), MenuPage.hint("ESC", "Cancel")));
    }

    private static void box(SceneCanvas canvas, int y, int height) {
        int width = canvas.width() - 2 * MenuPage.MARGIN;
        canvas.fill(MenuPage.MARGIN, y, width, height, MenuPage.INK);
        MenuPage.outline(canvas, MenuPage.MARGIN, y, width, height, MenuPage.GOLD);
    }

    private static void caret(SceneCanvas canvas, int x, int y) {
        caret(canvas, x, y, 10);
    }

    private static void caret(SceneCanvas canvas, int x, int y, int height) {
        canvas.fill(x, y, 1, height, MenuPage.GOLD);
    }

    /** Splits {@code value} into rows of at most {@code perLine} characters. */
    public static List<String> wrap(String value, int perLine) {
        List<String> lines = new ArrayList<>();
        for (int start = 0; start < value.length(); start += perLine) {
            lines.add(value.substring(start, Math.min(value.length(), start + perLine)));
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /** Splits {@code value} into {@link NameFont} rows no wider than {@code pixels}, between graphemes. */
    public static List<String> wrapPixels(String value, int pixels) {
        List<String> lines = new ArrayList<>();
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(value);
        int lineStart = 0;
        int width = 0;
        int start = 0;
        for (int end = graphemes.next(); end != BreakIterator.DONE; start = end, end = graphemes.next()) {
            int cluster = NameFont.width(value.substring(start, end));
            if (width + cluster > pixels && start > lineStart) {
                lines.add(value.substring(lineStart, start));
                lineStart = start;
                width = 0;
            }
            width += cluster;
        }
        lines.add(value.substring(lineStart));
        return lines;
    }

    /** The field's complete text; a masked field's value never reaches the page. */
    private FullTextReader.Text detailsText() {
        return FullTextReader.Text.of(title).line(prompt)
                .field("Text", masked ? (text.isEmpty() ? "Nothing entered yet" : "Hidden") : text())
                .field("Length", masked ? "" : codePoints + " of " + maxLength)
                .field("Allowed", allowedText())
                .field("Problem", error);
    }

    /** What an ASCII field takes, in words; empty for fields that take any visible text. */
    private String allowedText() {
        if (kind != Kind.ASCII || masked) return "";
        if (allowedExtraChars.isEmpty()) return "Letters and digits";
        return "Letters, digits and " + String.join(" ", allowedExtraChars.split(""));
    }

    List<String> detailsVisibleLines() { return reader.visibleLines(); }

    int detailsFirstLine() { return reader.firstVisibleLine(); }

    private int utf8Length() {
        return text.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static int utf8Length(int codePoint) {
        return codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
    }
}
