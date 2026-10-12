package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.TextLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A modal, keyboard-driven text field (LAN invites, chat, display name, master URL, port).
 * It reads physical keys only, never controller buttons: Space and Backspace are mapped to pad
 * buttons by default, so a button-driven field would confirm while the player types. Enter
 * accepts, Escape cancels, Backspace deletes; letters follow Shift, and the US-layout keys for
 * {@code - _ . , ' ; : / ? + ! # [ ]} type their characters when the field allows them.
 */
public final class TextEntry {
    /** What the last {@link #update} did. */
    public enum Result { NONE, ACCEPTED, CANCELLED }

    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFF8090B0;
    private static final int BOX = 0x803060C0;

    private final String title;
    private final int maxLength;
    private final String allowedExtraChars;
    private final StringBuilder text = new StringBuilder();

    public TextEntry(String title, String initial, int maxLength, String allowedExtraChars) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be non-negative");
        }
        this.title = Objects.requireNonNull(title, "title");
        this.maxLength = maxLength;
        this.allowedExtraChars = Objects.requireNonNull(allowedExtraChars, "allowedExtraChars");
        setText(initial == null ? "" : initial);
    }

    /** Appends {@code value} when it is a letter, digit or allowed extra and the field has room. */
    public void feedChar(char value) {
        if (text.length() >= maxLength) {
            return;
        }
        if ((value < 128 && Character.isLetterOrDigit(value)) || allowedExtraChars.indexOf(value) >= 0) {
            text.append(value);
        }
    }

    public void backspace() {
        if (!text.isEmpty()) {
            text.setLength(text.length() - 1);
        }
    }

    public String text() {
        return text.toString();
    }

    /** Replaces the text, filtering and clamping it as typing would. */
    public void setText(String value) {
        Objects.requireNonNull(value, "value");
        text.setLength(0);
        for (int i = 0; i < value.length(); i++) {
            feedChar(value.charAt(i));
        }
    }

    public String title() {
        return title;
    }

    /** One tick of key input. */
    public Result update(ViewInput input) {
        Objects.requireNonNull(input, "input");
        if (input.keyPressed(SceneKeys.ESCAPE)) {
            return Result.CANCELLED;
        }
        if (input.keyPressed(SceneKeys.ENTER) || input.keyPressed(SceneKeys.KP_ENTER)) {
            return Result.ACCEPTED;
        }
        if (input.keyPressed(SceneKeys.BACKSPACE)) {
            backspace();
            return Result.NONE;
        }
        int typed = typedCharacter(input);
        if (typed >= 0) {
            feedChar((char) typed);
        }
        return Result.NONE;
    }

    private static int typedCharacter(ViewInput input) {
        boolean shift = input.keyDown(SceneKeys.LEFT_SHIFT) || input.keyDown(SceneKeys.RIGHT_SHIFT);
        for (int key = SceneKeys.A; key <= SceneKeys.Z; key++) {
            if (input.keyPressed(key)) {
                char base = (char) ('a' + key - SceneKeys.A);
                return shift ? Character.toUpperCase(base) : base;
            }
        }
        if (shift && input.keyPressed(SceneKeys.DIGIT_1)) return '!';
        if (shift && input.keyPressed(SceneKeys.DIGIT_3)) return '#';
        for (int key = SceneKeys.DIGIT_0; key <= SceneKeys.DIGIT_9; key++) {
            if (input.keyPressed(key)) {
                return '0' + key - SceneKeys.DIGIT_0;
            }
        }
        if (input.keyPressed(SceneKeys.SPACE)) return ' ';
        if (input.keyPressed(SceneKeys.MINUS)) return shift ? '_' : '-';
        if (input.keyPressed(SceneKeys.PERIOD)) return '.';
        if (input.keyPressed(SceneKeys.COMMA)) return ',';
        if (input.keyPressed(SceneKeys.APOSTROPHE)) return '\'';
        if (input.keyPressed(SceneKeys.SEMICOLON)) return shift ? ':' : ';';
        if (input.keyPressed(SceneKeys.SLASH)) return shift ? '?' : '/';
        if (input.keyPressed(SceneKeys.EQUAL) && shift) return '+';
        if (input.keyPressed(SceneKeys.LEFT_BRACKET) && !shift) return '[';
        if (input.keyPressed(SceneKeys.RIGHT_BRACKET) && !shift) return ']';
        return -1;
    }

    /** Draws the editor over the whole screen; long values wrap so every character stays visible. */
    public void draw(SceneCanvas canvas) {
        canvas.clear(BACKGROUND);
        canvas.text(title, 10, 10, TITLE);
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
        canvas.text(text.length() + "/" + maxLength, 10, 192, HINT);
        canvas.text(TextLayout.ellipsis("Enter OK  Esc cancel  Bksp delete", canvas.width() - 20,
                canvas::textWidth), 10, 206, HINT);
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
}
