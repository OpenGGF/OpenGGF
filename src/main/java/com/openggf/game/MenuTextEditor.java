package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.text.Graphemes;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.LineMetrics;
import com.openggf.graphics.text.ShapedLine;
import com.openggf.graphics.text.TextDirection;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;

import java.util.Objects;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/** Shared single-line editor: normal physical typing and a complete controller keyboard. */
public final class MenuTextEditor {
    public enum Result { NONE, ACCEPTED, CANCELLED }
    public enum Mode { TEXT, INTEGER, DECIMAL, ADDRESS, PATH }
    private static final String LOWER = "1234567890qwertyuiopasdfghjkl zxcvbnm.-_/:@?&=+%#~";
    private static final String SYMBOLS = symbols();
    private static final int COLUMNS = 10;
    private static final int CHARACTER_CELLS = 50;
    private static final int CELL_COUNT = 60;
    private final String title;
    private final Mode mode;
    private MenuDetailsScreen details;
    private MenuPathBrowser browser;
    private String detailsHint = "F1";
    private String acceptHint = "Enter", backHint = "Esc";
    private final String original;
    private final String defaultValue;
    private final int maxLength;
    private String value;
    private int caret;
    private boolean trailingCaret;
    private int horizontalOffset;
    private int page;
    private int cell;
    private boolean keypadFocused;
    private int ticks;
    private Result result = Result.NONE;
    private boolean finished;
    private boolean controller;
    private String error;
    private boolean deferAcceptanceFeedback;
    private boolean errorFeedback;
    private Supplier<String> clipboardReader = () -> {
        long window = glfwGetCurrentContext();
        return window == 0 ? null : glfwGetClipboardString(window);
    };

    /** Allows the address editor to paste a complete LAN invite with Ctrl/Cmd+V. */
    public void setClipboardReader(Supplier<String> clipboardReader) {
        this.clipboardReader = Objects.requireNonNull(clipboardReader, "clipboardReader");
    }

    /** Owners that validate on acceptance emit confirm only after successful validation. */
    public void deferAcceptanceFeedback() { deferAcceptanceFeedback = true; }

    public MenuTextEditor(String title, String initial, int maxLength) {
        this(title, initial, maxLength, null);
    }

    public MenuTextEditor(String title, String initial, int maxLength, String defaultValue) {
        this(title, initial, maxLength, defaultValue, Mode.TEXT);
    }

    public MenuTextEditor(String title, String initial, int maxLength, String defaultValue, Mode mode) {
        this.mode = Objects.requireNonNull(mode);
        this.title = Objects.requireNonNull(title, "title");
        if (maxLength < 1) throw new IllegalArgumentException("Text length must be positive");
        this.maxLength = maxLength;
        this.original = Objects.requireNonNull(initial, "initial");
        this.defaultValue = defaultValue;
        // Never silently shorten an existing preference just by opening its editor.
        value = original;
        caret = value.length();
        trailingCaret = caret > 0;
    }

    public String value() { return value; }
    public Result consumeResult() {
        Result pending = result;
        result = Result.NONE;
        return pending;
    }

    /** Keep the same value/caret visible when the owner rejects an accepted value. */
    public void reject(String reason) {
        error = Objects.requireNonNull(reason);
        MenuFeedback.emit(ERROR);
        result = Result.NONE;
        finished = false;
    }

    public void update(InputHandler input) {
        String beforeValue = value;
        int beforeCaret = caret, beforeCell = cell, beforePage = page;
        boolean beforeTrailingCaret = trailingCaret;
        boolean beforeKeypadFocused = keypadFocused;
        errorFeedback = false;
        try { updateInput(input); } finally {
            if (!finished && !errorFeedback && (!value.equals(beforeValue) || caret != beforeCaret
                    || trailingCaret != beforeTrailingCaret || cell != beforeCell || page != beforePage
                    || keypadFocused != beforeKeypadFocused)) MenuFeedback.emit(NAVIGATE);
        }
    }

    private void updateInput(InputHandler input) {
        if (finished || input == null) return;
        if (details != null) { if (details.update(input)) details = null; return; }
        if (browser != null) {
            browser.update(input);
            if (browser.selected() != null) { value = browser.selected(); setCaret(value.length()); error = null; browser = null; }
            else if (browser.cancelled()) browser = null;
            return;
        }
        ticks++;
        detailsHint = MenuInput.detailsLabel(input);
        acceptHint = MenuInput.confirmLabel(input); backHint = MenuInput.backLabel(input);
        if (MenuInput.textBack(input)) { finish(Result.CANCELLED); return; }
        if (MenuInput.textDetails(input)) {
            details = new MenuDetailsScreen(title, (error == null ? "" : error + "\n\n")
                    + UiMessages.text("menu.text.value_details", java.util.Map.of("value", value)));
            MenuFeedback.emit(CONFIRM);
            return;
        }
        controller = MenuInput.controller(input);
        String typed = MenuInput.consumeText(input);
        // Physical typing edits the field directly; device presentation never owns focus.
        boolean paste = mode == Mode.ADDRESS
                && (input.isPhysicalControlDown() || input.isPhysicalSuperDown())
                && MenuInput.textKeyPressed(input, GLFW_KEY_V);
        if (paste) {
            String copied = clipboardReader.get();
            if (copied != null && !copied.isEmpty()) {
                keypadFocused = false;
                insert(copied.substring(0, Math.min(copied.length(), maxLength + 1)));
            }
        } else if (!typed.isEmpty()) { keypadFocused = false; insert(typed); }
        if (MenuInput.textKeyRepeated(input, GLFW_KEY_BACKSPACE)) { keypadFocused = false; eraseBefore(); }
        if (MenuInput.textKeyRepeated(input, GLFW_KEY_DELETE)) { keypadFocused = false; eraseAfter(); }
        if (MenuInput.textKeyPressed(input, GLFW_KEY_HOME)) { keypadFocused = false; moveToEdge(-1); }
        if (MenuInput.textKeyPressed(input, GLFW_KEY_END)) { keypadFocused = false; moveToEdge(1); }

        if (!keypadFocused) {
            if (mode == Mode.PATH && MenuInput.textUp(input)) { browser = new MenuPathBrowser(value); MenuFeedback.emit(CONFIRM); return; }
            if (MenuInput.textDown(input)) { keypadFocused = true; cell %= columns(); return; }
            if (MenuInput.textLeft(input)) { moveCaret(-1); return; }
            if (MenuInput.textRight(input)) { moveCaret(1); return; }
            if (MenuInput.textAccept(input)) accept();
            return;
        }
        if (MenuInput.textLeft(input)) { cell = Math.floorMod(cell - 1, cellCount()); return; }
        if (MenuInput.textRight(input)) { cell = (cell + 1) % cellCount(); return; }
        if (MenuInput.textUp(input)) {
            if (cell < columns()) keypadFocused = false;
            else cell -= columns();
            return;
        }
        if (MenuInput.textDown(input)) { cell = (cell + columns()) % cellCount(); return; }
        if (MenuInput.textAccept(input)) chooseCell();
    }

    private boolean numeric() { return mode == Mode.INTEGER || mode == Mode.DECIMAL; }
    private int columns() { return numeric() ? 5 : COLUMNS; }
    private int characterCells() { return numeric() ? 15 : CHARACTER_CELLS; }
    private int cellCount() { return numeric() ? 20 : CELL_COUNT; }
    private int actionCell(int index) {
        if (!numeric()) return index;
        return switch (index) { case 15 -> 52; case 16 -> 53; case 17 -> 54; case 18 -> 55; case 19 -> 59; default -> index; };
    }
    private void chooseCell() {
        if (cell < characterCells()) {
            if (numeric() && characters().charAt(cell) == ' ') return;
            insert(Character.toString(characters().charAt(cell)));
            return;
        }
        switch (actionCell(cell)) {
            case 50 -> page = (page + 1) % 3;
            case 51 -> insert(" ");
            case 52 -> eraseBefore();
            case 53 -> { value = ""; setCaret(0); error = null; }
            case 54 -> { value = defaultValue == null ? original : defaultValue; setCaret(value.length()); error = null; }
            case 55 -> moveCaret(-1);
            case 56 -> moveCaret(1);
            case 57 -> moveToEdge(-1);
            case 58 -> moveToEdge(1);
            case 59 -> accept();
            default -> throw new IllegalStateException("Unknown editor cell");
        }
    }

    private void accept() {
        if (value.codePointCount(0, value.length()) > maxLength) {
            error = UiMessages.text("menu.text.limit", java.util.Map.of("count", maxLength)); errorFeedback = true; MenuFeedback.emit(ERROR);
            return;
        }
        finish(Result.ACCEPTED);
    }
    private void finish(Result next) {
        result = next; finished = true;
        if (next == Result.CANCELLED) MenuFeedback.emit(CANCEL);
        else if (!deferAcceptanceFeedback) MenuFeedback.emit(CONFIRM);
    }
    private void insert(String text) {
        error = null;
        StringBuilder accepted = new StringBuilder();
        int remaining = Math.max(0, maxLength - value.codePointCount(0, value.length()));
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) continue;
            if ((mode == Mode.INTEGER && "0123456789-+".indexOf(codePoint) < 0)
                    || (mode == Mode.DECIMAL && "0123456789-+.eE".indexOf(codePoint) < 0)) {
                error = mode == Mode.INTEGER ? UiMessages.text("menu.text.use_a_whole_number") : UiMessages.text("menu.text.use_a_number");
                errorFeedback = true; MenuFeedback.emit(ERROR); continue;
            }
            if (remaining-- <= 0) { error = UiMessages.text("menu.text.limit", java.util.Map.of("count", maxLength)); errorFeedback = true; MenuFeedback.emit(ERROR); break; }
            accepted.appendCodePoint(codePoint);
        }
        if (!accepted.isEmpty()) {
            value = value.substring(0, caret) + accepted + value.substring(caret);
            setCaret(caret + accepted.length());
        }
    }
    private TextLanguage fieldLanguage(TextLanguage language) {
        return mode == Mode.TEXT ? language : new TextLanguage(language.locale(), TextDirection.LTR);
    }
    private ShapedLine fieldLine() {
        return HostText.shared().shape(value, TextFace.LABEL, fieldLanguage(HostTextContext.current()));
    }
    private ShapedLine.Caret caretPoint() { return new ShapedLine.Caret(caret, trailingCaret); }
    private void setCaret(ShapedLine.Caret point) {
        caret = point.index();
        trailingCaret = point.trailing();
    }
    private void setCaret(int index) { setCaret(fieldLine().caretAt(index)); }
    private void moveToEdge(int direction) { setCaret(fieldLine().edgeCaret(direction)); }

    // Navigation is visual; deletion continues to remove logical grapheme clusters.
    private void moveCaret(int direction) {
        setCaret(fieldLine().moveCaret(caretPoint(), direction));
    }
    private void eraseBefore() {
        if (caret <= 0) return;
        int previous = Graphemes.previous(value, caret);
        value = value.substring(0, previous) + value.substring(caret);
        setCaret(previous);
        error = null;
    }
    private void eraseAfter() {
        if (caret >= value.length()) return;
        value = value.substring(0, caret) + value.substring(Graphemes.next(value, caret));
        setCaret(caret);
        error = null;
    }
    private String characters() {
        if (mode == Mode.INTEGER || mode == Mode.DECIMAL) {
            String keys = mode == Mode.INTEGER ? "1234567890-+" : "1234567890-+.eE";
            return keys + " ".repeat(CHARACTER_CELLS - keys.length());
        }
        if (mode == Mode.ADDRESS && page == 0) {
            String keys = "1234567890abcdefABCDEF.:[]%-_#localhost";
            return keys + " ".repeat(CHARACTER_CELLS - keys.length());
        }
        return page == 0 ? LOWER : page == 1 ? LOWER.toUpperCase(java.util.Locale.ROOT) : SYMBOLS; }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        width = Math.max(320, width);
        if (details != null) { details.render(font, width); return; }
        if (browser != null) { browser.render(font, width); return; }
        MenuStyle.page(font, width, title, null);
        MenuStyle.panel(font, 8, 37, width - 16, 31);
        if (!keypadFocused) MenuStyle.focus(font, 8, 37, width - 16, 31);
        int room = width - 30;
        ShapedLine line = HostText.shared().shape(value, TextFace.LABEL, fieldLanguage(MenuStyle.language(font)));
        int position = line.caretX(caretPoint());
        // Keep the caret inside the viewport, including at either end of a long RTL line.
        horizontalOffset = Math.max(0, Math.min(horizontalOffset, Math.max(0, line.width() - room + 1)));
        if (position < horizontalOffset) horizontalOffset = position;
        if (position >= horizontalOffset + room) horizontalOffset = position - room + 1;
        int caretX = 15 + position - horizontalOffset;
        if (font instanceof MenuPixelFont menu) {
            menu.drawTextWindow(line, 15, 45, room, horizontalOffset, 1, 1, 1, 1, 1);
            if (ticks % 60 < 40) menu.fillRect(caretX, 45 - line.above(), 1,
                    line.metrics().lineHeight(), .3f, .9f, 1, 1);
        } else {
            // Legacy/debug fonts have no shaped-glyph viewport. Retain their grapheme-safe
            // logical window so an ASCII path's edited end remains visible in those callers.
            String before = MenuDetailsScreen.readable(value.substring(0, caret));
            String display = MenuDetailsScreen.readable(value);
            int start = 0;
            while (start < before.length()
                    && MenuChrome.labelWidth(before.substring(start)) + MenuChrome.LABEL_ADVANCE > room)
                start = Graphemes.next(before, start);
            MenuStyle.label(font, display.substring(start), 15, 45, room, 1, 1, 1);
            if (ticks % 60 < 40) MenuStyle.label(font, "_", caretX, 54, 9, .3f, .9f, 1);
        }
        String hint = mode == Mode.PATH && !keypadFocused ? UiMessages.text("menu.text.up_browse_paths_down_keyboard") : keypadFocused ? UiMessages.text("menu.text.up_from_the_top_row_returns_to_text")
                : controller ? UiMessages.text("menu.text.left_right_moves_cursor_down_selects_keys") : UiMessages.text("menu.text.type_normally_down_selects_the_keypad");
        String detail = error != null ? UiMessages.text("menu.text.full_error", java.util.Map.of("key", detailsHint, "error", error)) : keypadFocused ? cellDescription() : UiMessages.text("menu.text.arrows_cursor_home_end_backspace_delete");
        String count = UiMessages.text("menu.text.character_count", java.util.Map.of("count", value.codePointCount(0, value.length()), "limit", maxLength, "key", detailsHint));
        String controls = keypadFocused ? UiMessages.text("menu.text.keypad_controls", java.util.Map.of("accept", acceptHint, "back", backHint))
                : UiMessages.text("menu.text.edit_controls", java.util.Map.of("accept", acceptHint, "back", backHint));
        TextLanguage language = MenuStyle.language(font);
        int cellWidth = (width - 20) / columns();
        boolean tall = HostText.shared().lineMetrics(TextFace.COMPACT, language).lineHeight() > 10;
        int hintY = 73, detailY = 185, footerTop = 198, countY = 200, controlsY = 212;
        int rows = cellCount() / columns();
        int[] rowY = new int[rows], rowAbove = new int[rows], rowHeight = new int[rows];
        float keyScale = tall ? MenuStyle.COMPACT : 1;
        if (tall) {
            // Joined-script fonts need more vertical room. Compact keypad labels let every
            // section keep its measured height without reducing the font's legible size.
            LineMetrics hintMetrics = compactMetrics(hint, width - 18, language);
            LineMetrics detailMetrics = compactMetrics(detail, width - 18, language);
            LineMetrics countMetrics = compactMetrics(count, width - 18, language);
            LineMetrics controlsMetrics = compactMetrics(controls, width - 18, language);
            footerTop = 224 - countMetrics.lineHeight() - controlsMetrics.lineHeight() - 4;
            countY = footerTop + 2 + countMetrics.above();
            controlsY = footerTop + 2 + countMetrics.lineHeight() + controlsMetrics.above();
            int detailTop = footerTop - 2 - detailMetrics.lineHeight();
            detailY = detailTop + detailMetrics.above();
            hintY = 70 + hintMetrics.above();
            int gridTop = 70 + hintMetrics.lineHeight() + 2;
            for (int i = 0; i < cellCount(); i++) {
                int row = i / columns();
                LineMetrics metrics = compactMetrics(cellLabel(i), cellWidth - 3, language);
                rowAbove[row] = Math.max(rowAbove[row], metrics.above());
                rowHeight[row] = Math.max(rowHeight[row], metrics.cellHeight() + metrics.below());
            }
            int used = 0;
            for (int row = 0; row < rows; row++) {
                rowHeight[row] += rowAbove[row];
                used += rowHeight[row];
            }
            int gap = Math.max(0, (detailTop - 2 - gridTop - used) / Math.max(1, rows - 1));
            for (int row = 0; row < rows; row++) {
                rowY[row] = gridTop + rowAbove[row];
                gridTop += rowHeight[row] + gap;
            }
        } else {
            for (int row = 0; row < rows; row++) rowY[row] = 91 + row * (numeric() ? 21 : 15);
        }
        MenuStyle.text(font, hint, 9, hintY, width - 18, .65f, .75f, .9f);
        for (int i = 0; i < cellCount(); i++) {
            int x = 10 + i % columns() * cellWidth;
            int row = i / columns(), y = rowY[row];
            if (keypadFocused && cell == i) {
                if (tall) MenuStyle.focus(font, x - 2, y - rowAbove[row] - 1, cellWidth - 1, rowHeight[row] + 2);
                else MenuStyle.focusLabel(font, x - 2, y, cellWidth - 1, 14);
            }
            String label = cellLabel(i);
            float r = i >= characterCells() ? 1 : .88f, g = i >= characterCells() ? .73f : .92f, b = i >= characterCells() ? .25f : 1;
            if (keyScale < 1) MenuStyle.text(font, label, x, y, cellWidth - 3, r, g, b);
            else MenuStyle.label(font, label, x, y, cellWidth - 3, r, g, b);
        }
        MenuStyle.text(font, detail, 9, detailY, width - 18, error == null ? .65f : 1, error == null ? .75f : .7f, error == null ? .9f : .2f);
        MenuStyle.fill(font, 0, footerTop, width, 224 - footerTop, .012f, .025f, .09f, 1);
        MenuStyle.text(font, count, 9, countY, width - 18, .5f, .9f, 1);
        if (tall) MenuStyle.text(font, controls, 9, controlsY, width - 18, .8f, .86f, 1);
        else MenuStyle.label(font, controls, 9, controlsY, width - 18, .8f, .86f, 1);
    }

    private static LineMetrics compactMetrics(String text, int width, TextLanguage language) {
        HostText host = HostText.shared();
        return host.shape(host.fit(text, width, TextFace.COMPACT, language), TextFace.COMPACT, language).metrics();
    }

    private String cellLabel(int index) {
        if (numeric() && index < characterCells() && characters().charAt(index) == ' ') return "";
        if (index < characterCells()) return characters().charAt(index) == ' ' ? UiMessages.text("menu.text.spc_caps") : Character.toString(characters().charAt(index));
        return switch (actionCell(index)) {
            case 50 -> page == 0 ? "ABC" : page == 1 ? "SYM" : "abc";
            case 51 -> UiMessages.text("menu.text.spc_caps");
            case 52 -> UiMessages.text("menu.text.del_caps");
            case 53 -> UiMessages.text("menu.text.clr_caps");
            case 54 -> defaultValue == null ? UiMessages.text("menu.text.rst_caps") : UiMessages.text("menu.text.def_caps");
            case 55 -> "<";
            case 56 -> ">";
            case 57 -> "|<";
            case 58 -> ">|";
            case 59 -> UiMessages.text("menu.text.ok_caps");
            default -> "";
        };
    }
    private String cellDescription() {
        return switch (actionCell(cell)) {
            case 50 -> UiMessages.text("menu.text.switch_keyboard_abc_abc_symbols");
            case 51 -> UiMessages.text("menu.text.insert_a_space");
            case 52 -> UiMessages.text("menu.text.delete_the_character_before_the_cursor");
            case 53 -> UiMessages.text("menu.text.clear_this_field");
            case 54 -> defaultValue == null ? UiMessages.text("menu.text.restore_the_value_when_this_editor_opened") : UiMessages.text("menu.text.restore_the_engine_default");
            case 55 -> UiMessages.text("menu.text.move_the_cursor_left");
            case 56 -> UiMessages.text("menu.text.move_the_cursor_right");
            case 57 -> UiMessages.text("menu.text.move_to_the_beginning");
            case 58 -> UiMessages.text("menu.text.move_to_the_end");
            case 59 -> UiMessages.text("menu.text.accept_this_value");
            default -> UiMessages.text("menu.text.insert_character", java.util.Map.of("character", String.valueOf(characters().charAt(cell))));
        };
    }
    private static String symbols() {
        StringBuilder result = new StringBuilder();
        for (char c = 32; c <= 126; c++) if (!Character.isLetterOrDigit(c)) result.append(c);
        result.append("0123456789");
        while (result.length() < CHARACTER_CELLS) result.append(' ');
        return result.toString();
    }
}
