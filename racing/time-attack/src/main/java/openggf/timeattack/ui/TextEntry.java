package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
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

    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFF8090B0;
    private static final int WARNING = 0xFFFF8060;
    private static final int BOX = 0x803060C0;

    private final String title;
    private final Kind kind;
    private final int maxLength;
    private final String allowedExtraChars;
    private final StringBuilder text = new StringBuilder();
    private int codePoints;
    private String prompt = "";
    private String error = "";
    private boolean masked;

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
        text.setLength(0);
        codePoints = 0;
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
        if (input.keyPressed(SceneKeys.ESCAPE)) {
            return Result.CANCELLED;
        }
        if (input.keyPressed(SceneKeys.ENTER) || input.keyPressed(SceneKeys.KP_ENTER)) {
            if (kind == Kind.NAME && !DisplayNames.isCanonical(acceptedName())) {
                error = "Type at least one visible character";
                return Result.NONE;
            }
            return Result.ACCEPTED;
        }
        if (input.keyPressed(SceneKeys.BACKSPACE)) {
            backspace();
            error = "";
        }
        String typed = input.typedText();
        if (!typed.isEmpty()) {
            typed.codePoints().forEach(this::feedCodePoint);
            error = "";
        }
        return Result.NONE;
    }

    /** Draws the editor over the whole screen; long values wrap so every character stays visible. */
    public void draw(SceneCanvas canvas) {
        canvas.clear(BACKGROUND);
        canvas.text(title, 10, 10, TITLE);
        if (!prompt.isEmpty()) {
            canvas.text(TextLayout.ellipsis(prompt, canvas.width() - 20, canvas::textWidth), 10, 24, HINT);
        }
        if (masked) {
            canvas.fill(8, 40, canvas.width() - 16, 24, BOX);
            canvas.text("********", 16, 48, TEXT);
        } else if (kind == Kind.ASCII) {
            drawAscii(canvas);
        } else {
            drawUnicode(canvas);
        }
        if (!error.isEmpty()) {
            canvas.text(TextLayout.ellipsis(error, canvas.width() - 20, canvas::textWidth), 10, 178, WARNING);
        }
        canvas.text(masked ? "Hidden" : codePoints + "/" + maxLength, 10, 192, HINT);
        canvas.text(TextLayout.ellipsis("Enter OK  Esc cancel  Bksp delete", canvas.width() - 20,
                canvas::textWidth), 10, 206, HINT);
    }

    private void drawAscii(SceneCanvas canvas) {
        int perLine = Math.max(1, (canvas.width() - 24) / 10);
        List<String> lines = wrap(text() + "_", perLine);
        int visible = Math.min(lines.size(), 12);
        List<String> shown = lines.subList(lines.size() - visible, lines.size());
        canvas.fill(8, 34, canvas.width() - 16, visible * 12 + 8, BOX);
        int y = 38;
        for (String line : shown) {
            canvas.text(line, 12, y, TEXT);
            y += 12;
        }
    }

    private void drawUnicode(SceneCanvas canvas) {
        String shownText = text() + "_";
        int inner = canvas.width() - 24;
        int top = prompt.isEmpty() ? 34 : 40;
        // A name is short: show it at double size when it fits, as it will appear to others.
        if (kind == Kind.NAME && NameFont.width(shownText) * 2 <= inner) {
            canvas.fill(8, top, canvas.width() - 16, NameFont.LINE_HEIGHT * 2 + 8, BOX);
            NameText.draw(canvas, shownText, 12, top + 4, 2, TEXT);
            return;
        }
        List<String> lines = wrapPixels(shownText, inner);
        int visible = Math.min(lines.size(), 9);
        List<String> shown = lines.subList(lines.size() - visible, lines.size());
        canvas.fill(8, top, canvas.width() - 16, visible * NameFont.LINE_HEIGHT + 8, BOX);
        int y = top + 4;
        for (String line : shown) {
            NameFont.draw(canvas, line, 12, y, TEXT);
            y += NameFont.LINE_HEIGHT;
        }
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

    private int utf8Length() {
        return text.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static int utf8Length(int codePoint) {
        return codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
    }
}
