package com.openggf.game;

import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;

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
        boolean beforeKeypadFocused = keypadFocused;
        errorFeedback = false;
        try { updateInput(input); } finally {
            if (!finished && !errorFeedback && (!value.equals(beforeValue) || caret != beforeCaret
                    || cell != beforeCell || page != beforePage || keypadFocused != beforeKeypadFocused)) MenuFeedback.emit(NAVIGATE);
        }
    }

    private void updateInput(InputHandler input) {
        if (finished || input == null) return;
        if (details != null) { if (details.update(input)) details = null; return; }
        if (browser != null) {
            browser.update(input);
            if (browser.selected() != null) { value = browser.selected(); caret = value.length(); error = null; browser = null; }
            else if (browser.cancelled()) browser = null;
            return;
        }
        ticks++;
        detailsHint = MenuInput.detailsLabel(input);
        acceptHint = MenuInput.confirmLabel(input); backHint = MenuInput.backLabel(input);
        if (MenuInput.textBack(input)) { finish(Result.CANCELLED); return; }
        if (MenuInput.textDetails(input)) {
            details = new MenuDetailsScreen(title, (error == null ? "" : error + "\n\n")
                    + "Value (Unicode shown as U+ codes):\n" + value);
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
        if (MenuInput.textKeyPressed(input, GLFW_KEY_HOME)) { keypadFocused = false; caret = 0; }
        if (MenuInput.textKeyPressed(input, GLFW_KEY_END)) { keypadFocused = false; caret = value.length(); }

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
            case 53 -> { value = ""; caret = 0; error = null; }
            case 54 -> { value = defaultValue == null ? original : defaultValue; caret = value.length(); error = null; }
            case 55 -> moveCaret(-1);
            case 56 -> moveCaret(1);
            case 57 -> caret = 0;
            case 58 -> caret = value.length();
            case 59 -> accept();
            default -> throw new IllegalStateException("Unknown editor cell");
        }
    }

    private void accept() {
        if (value.codePointCount(0, value.length()) > maxLength) {
            error = "Limit: " + maxLength + " characters"; errorFeedback = true; MenuFeedback.emit(ERROR);
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
                error = mode == Mode.INTEGER ? "Use a whole number" : "Use a number";
                errorFeedback = true; MenuFeedback.emit(ERROR); continue;
            }
            if (remaining-- <= 0) { error = "Limit: " + maxLength + " characters"; errorFeedback = true; MenuFeedback.emit(ERROR); break; }
            accepted.appendCodePoint(codePoint);
        }
        if (!accepted.isEmpty()) {
            value = value.substring(0, caret) + accepted + value.substring(caret);
            caret += accepted.length();
        }
    }
    private void moveCaret(int direction) {
        if (direction < 0 && caret > 0) caret = value.offsetByCodePoints(caret, -1);
        if (direction > 0 && caret < value.length()) caret = value.offsetByCodePoints(caret, 1);
    }
    private void eraseBefore() {
        if (caret <= 0) return;
        int previous = value.offsetByCodePoints(caret, -1);
        value = value.substring(0, previous) + value.substring(caret);
        caret = previous;
        error = null;
    }
    private void eraseAfter() {
        if (caret >= value.length()) return;
        value = value.substring(0, caret) + value.substring(value.offsetByCodePoints(caret, 1));
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
        MenuChrome.page(font, width, title.toUpperCase(java.util.Locale.ROOT), null, null);
        if (keypadFocused) MenuChrome.panel(font, 8, 37, width - 16, 31);
        else MenuChrome.focus(font, 8, 37, width - 16, 31, ticks);
        int columns = Math.max(1, (width - 32) / 9 - 1);
        int caretColumn = MenuDetailsScreen.readable(value.substring(0, caret)).length();
        int startColumn = Math.max(0, caretColumn - columns + 1);
        String display = MenuDetailsScreen.readable(value);
        int start = Math.min(startColumn, display.length());
        String visible = display.substring(start, Math.min(display.length(), start + columns));
        MenuChrome.label(font, visible.isEmpty() ? " " : visible, 15, 43, MenuChrome.WHITE);
        if (ticks % 60 < 40) MenuChrome.fill(font, 15 + (caretColumn - startColumn) * 9, 42, 1, 12, MenuChrome.GOLD);
        String count = value.codePointCount(0, value.length()) + " / " + maxLength;
        MenuChrome.compact(font, count, width - 14 - MenuChrome.compactWidth(count), 58, MenuChrome.DIM);
        String hint = mode == Mode.PATH && !keypadFocused ? "Up browses folders; Down opens the keypad" : keypadFocused ? "Up from the top row returns to the text"
                : controller ? "Left/Right moves the cursor; Down opens the keypad" : "Type normally; Down opens the keypad";
        MenuChrome.compact(font, MenuChrome.fitCompact(hint, width - 18), 9, 73, MenuChrome.MUTED);
        int cellWidth = (width - 20) / columns();
        int pitch = numeric() ? 21 : 15;
        for (int i = 0; i < cellCount(); i++) {
            int x = 10 + i % columns() * cellWidth;
            int y = 87 + i / columns() * pitch;
            boolean action = i >= characterCells();
            String label = cellLabel(i);
            boolean ok = action && actionCell(i) == 59;
            if (keypadFocused && cell == i) MenuChrome.focus(font, x, y, cellWidth - 3, pitch - 3, ticks);
            else if (!label.isEmpty()) {
                MenuChrome.fill(font, x, y, cellWidth - 3, pitch - 3, ok ? MenuChrome.GOLD : action ? MenuChrome.HEAD : MenuChrome.BUTTON, .9f);
                MenuChrome.fill(font, x, y + pitch - 4, cellWidth - 3, 1, MenuChrome.INK, .8f);
            }
            int cx = x + (cellWidth - 3) / 2;
            int cy = y + (pitch - 3) / 2;
            float[] ink = keypadFocused && cell == i ? MenuChrome.WHITE : ok ? MenuChrome.GOLD_INK : action ? MenuChrome.CYAN : MenuChrome.TEXT;
            switch (action ? actionCell(i) : -1) {
                case 55 -> MenuChrome.triangle(font, cx - 2, cy - 2, 3, 'L', ink, 1f);
                case 56 -> MenuChrome.triangle(font, cx - 1, cy - 2, 3, 'R', ink, 1f);
                case 57 -> { MenuChrome.fill(font, cx - 3, cy - 2, 1, 5, ink); MenuChrome.triangle(font, cx - 1, cy - 2, 3, 'L', ink, 1f); }
                case 58 -> { MenuChrome.triangle(font, cx - 2, cy - 2, 3, 'R', ink, 1f); MenuChrome.fill(font, cx + 2, cy - 2, 1, 5, ink); }
                default -> {
                    if (label.equals("SPC")) {
                        // A space bar: a floor with raised ends.
                        MenuChrome.fill(font, cx - 6, cy + 1, 13, 1, ink);
                        MenuChrome.fill(font, cx - 6, cy - 1, 1, 2, ink);
                        MenuChrome.fill(font, cx + 6, cy - 1, 1, 2, ink);
                    } else if (action || label.length() > 1) MenuChrome.compact(font, label, cx - MenuChrome.compactWidth(label) / 2, cy - 3, ink);
                    else MenuChrome.label(font, label, cx - 4, cy - 5, ink);
                }
            }
        }
        String detail = error != null ? "Full error: " + error : keypadFocused ? cellDescription() : "Arrows move the cursor  Home/End  Backspace/Delete";
        MenuChrome.compact(font, MenuChrome.fitCompact(detail, width - 18), 9, 192, error == null ? MenuChrome.MUTED : MenuChrome.RED);
        java.util.List<MenuChrome.Hint> hints = new java.util.ArrayList<>();
        hints.add(MenuChrome.hint(detailsHint, "DETAILS"));
        hints.add(MenuChrome.hint(acceptHint, keypadFocused ? "PRESS KEY" : "OK"));
        hints.add(MenuChrome.hint(backHint, "BACK"));
        MenuChrome.footer(font, width, null, hints, controller);
    }

    private String cellLabel(int index) {
        if (numeric() && index < characterCells() && characters().charAt(index) == ' ') return "";
        if (index < characterCells()) return characters().charAt(index) == ' ' ? "SPC" : Character.toString(characters().charAt(index));
        return switch (actionCell(index)) {
            case 50 -> page == 0 ? "ABC" : page == 1 ? "SYM" : "abc";
            case 51 -> "SPC";
            case 52 -> "DEL";
            case 53 -> "CLR";
            case 54 -> defaultValue == null ? "RST" : "DEF";
            case 55 -> "<";
            case 56 -> ">";
            case 57 -> "|<";
            case 58 -> ">|";
            case 59 -> "OK";
            default -> "";
        };
    }
    private String cellDescription() {
        return switch (actionCell(cell)) {
            case 50 -> "Switch keyboard: abc / ABC / symbols";
            case 51 -> "Insert a space";
            case 52 -> "Delete the character before the cursor";
            case 53 -> "Clear this field";
            case 54 -> defaultValue == null ? "Restore the value when this editor opened" : "Restore the engine default";
            case 55 -> "Move the cursor left";
            case 56 -> "Move the cursor right";
            case 57 -> "Move to the beginning";
            case 58 -> "Move to the end";
            case 59 -> "Accept this value";
            default -> "Insert " + (characters().charAt(cell) == ' ' ? "a space" : characters().charAt(cell));
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
