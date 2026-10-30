package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.TextInputSession;
import com.openggf.control.Composition;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.util.Graphemes;
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
    private TextInputSession.Lease textLease;
    private InputHandler textInput;
    private Composition composition = Composition.EMPTY;
    private int caret;
    private boolean trailingCaret;
    private int horizontalOffset;
    private int page;
    private int cell;
    private boolean keypadFocused;
    private int ticks;
    private MenuContentFrame frame = new MenuContentFrame(320, 320, 0);
    private Result result = Result.NONE;
    private boolean finished;
    private boolean controller;
    private String error;
    private boolean deferAcceptanceFeedback;
    private boolean errorFeedback;
    private record KeyHit(int index, int x, int y, int width, int height) {
        boolean contains(MasterTitlePointer p) { return p != null && p.over(x, y, width, height); }
    }
    private final java.util.List<KeyHit> keyHits = new java.util.ArrayList<>();
    private java.util.List<MenuChrome.FooterTarget> footerTargets = java.util.List.of();
    private int pointerX = Integer.MIN_VALUE, pointerY = Integer.MIN_VALUE;
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
        if (input == null) return;
        MenuInput.Pointer p = frame.local(MenuInput.pointer(input, frame.surfaceWidth(), 224));
        int wheel = com.openggf.control.MouseWheel.of(input).takeNotches();
        MasterTitlePointer mouse = p.inside() ? new MasterTitlePointer(p.x(), p.y(),
                p.x() != pointerX || p.y() != pointerY, p.leftPressed(), p.rightPressed(), wheel) : null;
        if (mouse != null) { pointerX = mouse.x(); pointerY = mouse.y(); }
        update(input, mouse);
    }

    /** The owner may supply coordinates already mapped into this editor's content frame. */
    public void update(InputHandler input, MasterTitlePointer pointer) {
        MasterTitlePointer mouse = pointer != null && input != null && !input.hasLogicalOverride()
                && pointer.over(0, 0, frame.width(), 224) ? pointer : null;
        String beforeValue = value;
        int beforeCaret = caret, beforeCell = cell, beforePage = page;
        boolean beforeTrailingCaret = trailingCaret;
        boolean beforeKeypadFocused = keypadFocused;
        errorFeedback = false;
        try { updateInput(input, mouse); } finally {
            if (!finished && !errorFeedback && (!value.equals(beforeValue) || caret != beforeCaret
                    || trailingCaret != beforeTrailingCaret || cell != beforeCell || page != beforePage
                    || keypadFocused != beforeKeypadFocused)) MenuFeedback.emit(NAVIGATE);
        }
    }

    private void updateInput(InputHandler input, MasterTitlePointer mouse) {
        if (finished || input == null) return;
        if (textInput != input) { withdrawText(); textInput = input; }
        MenuInput.Pointer local = mouse == null ? MenuInput.Pointer.none()
                : new MenuInput.Pointer(mouse.x(), mouse.y(), true, mouse.click(), mouse.backClick());
        if (details != null) { if (details.update(input, local, mouse == null ? 0 : mouse.wheel())) details = null; return; }
        if (browser != null) {
            if (mouse != null) browser.pointer(mouse.x(), mouse.y(), mouse.moved(), mouse.click(), mouse.backClick(), mouse.wheel());
            browser.update(input, local);
            if (browser.selected() != null) { value = browser.selected(); setCaret(value.length()); error = null; browser = null; }
            else if (browser.cancelled()) browser = null;
            return;
        }
        textLease = InputHandlerInternalAccess.textInput(input).claim(this);
        composition = textLease.composition();
        ticks++;
        detailsHint = MenuInput.detailsLabel(input);
        acceptHint = MenuInput.confirmLabel(input); backHint = MenuInput.backLabel(input);
        controller = MenuInput.controller(input);
        String typed = MenuInput.consumeText(input);
        if (!typed.isEmpty()) { keypadFocused = false; insert(typed); }
        if (textLease.suppressCommands()) {
            if (MenuInput.textBack(input) || mouse != null && mouse.backClick()) {
                if (textLease.draining()) { finish(Result.CANCELLED); return; }
                textLease.cancelComposition();
            }
            composition = textLease.composition();
            return;
        }
        int footer = -1;
        if (mouse != null && mouse.click())
            for (MenuChrome.FooterTarget target : footerTargets) if (target.contains(mouse)) { footer = target.index(); break; }
        if (MenuInput.textBack(input) || mouse != null && mouse.backClick() || footer == 2) { finish(Result.CANCELLED); return; }
        if (MenuInput.textDetails(input) || footer == 0) {
            withdrawText();
            details = new MenuDetailsScreen(title, (error == null ? "" : error + "\n\n")
                    + UiMessages.text("menu.text.value_details", java.util.Map.of("value", value)));
            MenuFeedback.emit(CONFIRM);
            return;
        }
        if (footer == 1) { if (keypadFocused) chooseCell(); else accept(); return; }
        if (mouse != null && mouse.click() && mouse.over(8, 37, frame.width() - 16, 31)) {
            keypadFocused = false;
            return;
        }
        if (mouse != null && (mouse.moved() || mouse.click())) {
            for (KeyHit hit : keyHits) {
                if (!hit.contains(mouse)) continue;
                keypadFocused = true;
                cell = hit.index();
                if (mouse.click()) { chooseCell(); return; }
                break;
            }
        }
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
        }
        if (MenuInput.textKeyRepeated(input, GLFW_KEY_BACKSPACE)) { keypadFocused = false; eraseBefore(); }
        if (MenuInput.textKeyRepeated(input, GLFW_KEY_DELETE)) { keypadFocused = false; eraseAfter(); }
        if (MenuInput.textKeyPressed(input, GLFW_KEY_HOME)) { keypadFocused = false; moveToEdge(-1); }
        if (MenuInput.textKeyPressed(input, GLFW_KEY_END)) { keypadFocused = false; moveToEdge(1); }

        if (!keypadFocused) {
            if (mode == Mode.PATH && MenuInput.textUp(input)) { withdrawText(); browser = new MenuPathBrowser(value); MenuFeedback.emit(CONFIRM); return; }
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
    private int characterCells() { return numeric() ? characters().length() : CHARACTER_CELLS; }
    private int cellCount() { return numeric() ? 20 : CELL_COUNT; }
    /**
     * The numeric pads fill their 5 x 4 grid with real keys only, so every focusable cell does
     * something: digits and signs, then Delete at the end of the sign row, and one bottom row
     * shared by both pads (cursor left/right as a pair, Clear, Default or Undo, Set). The whole
     * number pad has no decimal keys, so its two spare cells take Start/End of the field.
     */
    private int actionCell(int index) {
        if (!numeric() || index < characterCells()) return index;
        return switch (index) {
            case 12 -> 57; case 13 -> 58; case 14 -> 52;
            case 15 -> 55; case 16 -> 56; case 17 -> 53; case 18 -> 54; case 19 -> 59;
            default -> index;
        };
    }
    private void chooseCell() {
        if (cell < characterCells()) {
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
        withdrawText();
        result = next; finished = true;
        if (next == Result.CANCELLED) MenuFeedback.emit(CANCEL);
        else if (!deferAcceptanceFeedback) MenuFeedback.emit(CONFIRM);
    }
    private void withdrawText() {
        if (textLease != null) textLease.close();
        textLease = null;
        composition = Composition.EMPTY;
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
            // Typing still accepts E; the pad offers the exponent once, as e.
            return mode == Mode.INTEGER ? "1234567890-+" : "1234567890-+.e";
        }
        if (mode == Mode.ADDRESS && page == 0) {
            String keys = "1234567890abcdefABCDEF.:[]%-_#localhost";
            return keys + " ".repeat(CHARACTER_CELLS - keys.length());
        }
        return page == 0 ? LOWER : page == 1 ? LOWER.toUpperCase(java.util.Locale.ROOT) : SYMBOLS; }

    public void render(PixelFont font, int width) {
        int surface = Math.max(320, width);
        render(font, surface, new MenuContentFrame(surface, surface, 0));
    }

    void render(PixelFont font, int width, MenuContentFrame frame) {
        if (font == null) return;
        this.frame = frame;
        keyHits.clear();
        footerTargets = java.util.List.of();
        width = Math.max(320, width);
        if (details != null) { details.render(font, width); return; }
        if (browser != null) { browser.render(font, width); return; }
        if (HostText.shared().lineMetrics(TextFace.COMPACT, MenuStyle.language(font)).lineHeight() > 10) {
            renderTall(font, width);
            return;
        }
        MenuChrome.page(font, width, paged(title.toUpperCase(UiMessages.locale()), width - 18, 1, MenuStyle.language(font)), null, null);
        if (keypadFocused) MenuChrome.panel(font, 8, 37, width - 16, 31);
        else MenuChrome.focus(font, 8, 37, width - 16, 31, ticks);
        int room = width - 41;
        composition = textLease == null ? Composition.EMPTY : textLease.composition();
        String presented = value.substring(0, caret) + composition.text() + value.substring(caret);
        ShapedLine line = HostText.shared().shape(presented, TextFace.LABEL, fieldLanguage(MenuStyle.language(font)));
        ShapedLine.Caret presentedCaret = composition.isEmpty() ? caretPoint()
                : new ShapedLine.Caret(caret + composition.caret(), composition.caret() == composition.text().length());
        int position = line.caretX(presentedCaret);
        // Keep the caret inside the viewport, including at either end of a long RTL line.
        horizontalOffset = Math.max(0, Math.min(horizontalOffset, Math.max(0, line.width() - room + 1)));
        if (position < horizontalOffset) horizontalOffset = position;
        if (position >= horizontalOffset + room) horizontalOffset = position - room + 1;
        int caretX = 15 + position - horizontalOffset;
        if (font instanceof MenuPixelFont menu) {
            menu.drawTextWindow(line, 15, 43, room, horizontalOffset, 1, 1, 1, 1, 1);
            CompositionPresentation.underline(menu, line, composition, caret, 15, 43, room, horizontalOffset);
            if (!composition.isEmpty() || ticks % 60 < 40) menu.fillRect(caretX, 42 - line.above(), 1,
                    Math.max(12, line.metrics().lineHeight()), .3f, .9f, 1, 1);
        } else {
            // Legacy/debug fonts have no shaped-glyph viewport. Retain their grapheme-safe
            // logical window so an ASCII path's edited end remains visible in those callers.
            String before = MenuDetailsScreen.readable(presented.substring(0, caret + composition.caret()));
            String display = MenuDetailsScreen.readable(presented);
            int start = 0;
            while (start < before.length()
                    && MenuChrome.labelWidth(before.substring(start)) + MenuChrome.LABEL_ADVANCE > room)
                start = Graphemes.next(before, start);
            MenuStyle.label(font, display.substring(start), 15, 43, room, 1, 1, 1);
            if (ticks % 60 < 40) MenuStyle.label(font, "_", caretX, 54, 9, .3f, .9f, 1);
        }
        if (textLease != null) textLease.caretRectangle(caretX + frame.left(), 42 - line.above(), 1,
                Math.max(12, line.metrics().lineHeight()));
        String count = UiMessages.text("menu.text.count", java.util.Map.of("count", value.codePointCount(0, value.length()), "limit", maxLength));
        MenuChrome.compact(font, count, width - 14 - MenuChrome.compactWidth(count), 58, MenuChrome.DIM);
        String hint = mode == Mode.PATH && !keypadFocused ? UiMessages.text("menu.text.up_browse_paths_down_keyboard") : keypadFocused ? UiMessages.text("menu.text.up_from_the_top_row_returns_to_text")
                : controller ? UiMessages.text("menu.text.left_right_moves_cursor_down_selects_keys") : UiMessages.text("menu.text.type_normally_down_selects_the_keypad");
        MenuChrome.compact(font, paged(hint, width - 18, MenuStyle.COMPACT, MenuStyle.language(font)), 9, 73, MenuChrome.MUTED);
        int cellWidth = (width - 20) / columns();
        int pitch = numeric() ? 21 : 15;
        for (int i = 0; i < cellCount(); i++) {
            int x = 10 + i % columns() * cellWidth;
            int y = 87 + i / columns() * pitch;
            keyHits.add(new KeyHit(i, x, y, cellWidth - 3, pitch - 3));
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
                    if (i < characterCells() && characters().charAt(i) == ' ' || action && actionCell(i) == 51) {
                        // A space bar: a floor with raised ends.
                        MenuChrome.fill(font, cx - 6, cy + 1, 13, 1, ink);
                        MenuChrome.fill(font, cx - 6, cy - 1, 1, 2, ink);
                        MenuChrome.fill(font, cx + 6, cy - 1, 1, 2, ink);
                    } else if (action || label.length() > 1) MenuChrome.compact(font, label, cx - MenuChrome.compactWidth(label) / 2, cy - 3, ink);
                    else MenuChrome.label(font, label, cx - (MenuChrome.labelWidth(label) - 1) / 2, cy - 5, ink);
                }
            }
        }
        if (error != null || keypadFocused) {
            String detail = error != null ? UiMessages.text("menu.text.error", java.util.Map.of("error", error)) : cellDescription();
            if (error != null) MenuChrome.icon(font, MenuChrome.Icon.WARNING, 9, 192, MenuChrome.RED);
            MenuChrome.compact(font, paged(detail, width - (error != null ? 29 : 18), MenuStyle.COMPACT, MenuStyle.language(font)), error != null ? 20 : 9, 192,
                    error == null ? MenuChrome.MUTED : MenuChrome.RED);
        } else if (!controller) {
            // Typing keys read as key caps, like every other shortcut in the menus.
            MenuChrome.hints(font, 9, 191, java.util.List.of(MenuChrome.leftRight(UiMessages.text("menu.text.cursor_caps")), MenuChrome.hint("Home", UiMessages.text("menu.text.start_caps")),
                    MenuChrome.hint("End", UiMessages.text("menu.text.end_caps")), MenuChrome.hint("Bksp", UiMessages.text("menu.text.delete_caps"))), false, MenuChrome.MUTED);
        }
        java.util.List<MenuChrome.Hint> hints = new java.util.ArrayList<>();
        hints.add(MenuChrome.hint(detailsHint, UiMessages.text("menu.text.details_caps")));
        // Prompts name the consequence: Enter sets the value and closes the editor.
        hints.add(MenuChrome.hint(acceptHint, keypadFocused ? UiMessages.text("menu.text.press_key_caps") : UiMessages.text("menu.text.set_caps")));
        hints.add(MenuChrome.hint(backHint, UiMessages.text("menu.text.back_caps")));
        footerTargets = MenuChrome.footerLayout(width, null, hints, ticks).targets();
        MenuChrome.footer(font, width, null, hints, controller, ticks);
    }

    private void renderTall(PixelFont font, int width) {
        if (font == null) return;
        width = Math.max(320, width);
        if (details != null) { details.render(font, width); return; }
        if (browser != null) { browser.render(font, width); return; }
        TextLanguage language = MenuStyle.language(font);
        MenuStyle.page(font, width, paged(title.toUpperCase(UiMessages.locale()), width - 18, 1, language), null);
        MenuStyle.panel(font, 8, 37, width - 16, 31);
        if (!keypadFocused) MenuStyle.focus(font, 8, 37, width - 16, 31);
        int room = width - 30;
        composition = textLease == null ? Composition.EMPTY : textLease.composition();
        String presented = value.substring(0, caret) + composition.text() + value.substring(caret);
        ShapedLine line = HostText.shared().shape(presented, TextFace.LABEL, fieldLanguage(MenuStyle.language(font)));
        ShapedLine.Caret presentedCaret = composition.isEmpty() ? caretPoint()
                : new ShapedLine.Caret(caret + composition.caret(), composition.caret() == composition.text().length());
        int position = line.caretX(presentedCaret);
        // Keep the caret inside the viewport, including at either end of a long RTL line.
        horizontalOffset = Math.max(0, Math.min(horizontalOffset, Math.max(0, line.width() - room + 1)));
        if (position < horizontalOffset) horizontalOffset = position;
        if (position >= horizontalOffset + room) horizontalOffset = position - room + 1;
        int caretX = 15 + position - horizontalOffset;
        if (font instanceof MenuPixelFont menu) {
            menu.drawTextWindow(line, 15, 45, room, horizontalOffset, 1, 1, 1, 1, 1);
            CompositionPresentation.underline(menu, line, composition, caret, 15, 45, room, horizontalOffset);
            if (!composition.isEmpty() || ticks % 60 < 40) menu.fillRect(caretX, 45 - line.above(), 1,
                    line.metrics().lineHeight(), .3f, .9f, 1, 1);
        } else {
            // Legacy/debug fonts have no shaped-glyph viewport. Retain their grapheme-safe
            // logical window so an ASCII path's edited end remains visible in those callers.
            String before = MenuDetailsScreen.readable(presented.substring(0, caret + composition.caret()));
            String display = MenuDetailsScreen.readable(presented);
            int start = 0;
            while (start < before.length()
                    && MenuChrome.labelWidth(before.substring(start)) + MenuChrome.LABEL_ADVANCE > room)
                start = Graphemes.next(before, start);
            MenuStyle.label(font, display.substring(start), 15, 45, room, 1, 1, 1);
            if (ticks % 60 < 40) MenuStyle.label(font, "_", caretX, 54, 9, .3f, .9f, 1);
        }
        if (textLease != null) textLease.caretRectangle(caretX + frame.left(), 45 - line.above(), 1, line.metrics().lineHeight());
        String hint = mode == Mode.PATH && !keypadFocused ? UiMessages.text("menu.text.up_browse_paths_down_keyboard") : keypadFocused ? UiMessages.text("menu.text.up_from_the_top_row_returns_to_text")
                : controller ? UiMessages.text("menu.text.left_right_moves_cursor_down_selects_keys") : UiMessages.text("menu.text.type_normally_down_selects_the_keypad");
        String detail = error != null ? UiMessages.text("menu.text.full_error", java.util.Map.of("key", detailsHint, "error", error)) : keypadFocused ? cellDescription() : UiMessages.text("menu.text.arrows_cursor_home_end_backspace_delete");
        String count = UiMessages.text("menu.text.character_count", java.util.Map.of("count", value.codePointCount(0, value.length()), "limit", maxLength, "key", detailsHint));
        String controls = keypadFocused ? UiMessages.text("menu.text.keypad_controls", java.util.Map.of("accept", acceptHint, "back", backHint))
                : UiMessages.text("menu.text.edit_controls", java.util.Map.of("accept", acceptHint, "back", backHint));
        int cellWidth = (width - 20) / columns();
        boolean tall = HostText.shared().lineMetrics(TextFace.COMPACT, language).lineHeight() > 10;
        int hintY = 73, detailY = 185, footerTop = 198, countY = 200, controlsY = 212;
        int rows = cellCount() / columns();
        int[] rowY = new int[rows], rowAbove = new int[rows], rowHeight = new int[rows];
        float keyScale = tall ? MenuStyle.COMPACT : 1;
        if (tall) {
            // Joined-script fonts need more vertical room. Compact keypad labels let every
            // section keep its measured height without reducing the font's legible size.
            LineMetrics hintMetrics = compactMetrics(hint, language);
            LineMetrics detailMetrics = compactMetrics(detail, language);
            LineMetrics countMetrics = compactMetrics(count, language);
            LineMetrics controlsMetrics = compactMetrics(controls, language);
            footerTop = 224 - countMetrics.lineHeight() - controlsMetrics.lineHeight() - 4;
            countY = footerTop + 2 + countMetrics.above();
            controlsY = footerTop + 2 + countMetrics.lineHeight() + controlsMetrics.above();
            int detailTop = footerTop - 2 - detailMetrics.lineHeight();
            detailY = detailTop + detailMetrics.above();
            hintY = 70 + hintMetrics.above();
            int gridTop = 70 + hintMetrics.lineHeight() + 2;
            for (int i = 0; i < cellCount(); i++) {
                int row = i / columns();
                LineMetrics metrics = compactMetrics(cellLabel(i), language);
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
        MenuStyle.text(font, paged(hint, width - 18, MenuStyle.COMPACT, language), 9, hintY, width - 18, .65f, .75f, .9f);
        for (int i = 0; i < cellCount(); i++) {
            int x = 10 + i % columns() * cellWidth;
            int row = i / columns(), y = rowY[row];
            keyHits.add(new KeyHit(i, x - 2, tall ? y - rowAbove[row] - 1 : y,
                    cellWidth - 1, tall ? rowHeight[row] + 2 : 14));
            if (keypadFocused && cell == i) {
                if (tall) MenuStyle.focus(font, x - 2, y - rowAbove[row] - 1, cellWidth - 1, rowHeight[row] + 2);
                else MenuStyle.focusLabel(font, x - 2, y, cellWidth - 1, 14);
            }
            String label = paged(cellLabel(i), cellWidth - 3, keyScale, language);
            float r = i >= characterCells() ? 1 : .88f, g = i >= characterCells() ? .73f : .92f, b = i >= characterCells() ? .25f : 1;
            if (keyScale < 1) MenuStyle.text(font, label, x, y, cellWidth - 3, r, g, b);
            else MenuStyle.label(font, label, x, y, cellWidth - 3, r, g, b);
        }
        MenuStyle.text(font, paged(detail, width - 18, MenuStyle.COMPACT, language), 9, detailY, width - 18, error == null ? .65f : 1, error == null ? .75f : .7f, error == null ? .9f : .2f);
        MenuStyle.fill(font, 0, footerTop, width, 224 - footerTop, .012f, .025f, .09f, 1);
        MenuStyle.text(font, paged(count, width - 18, MenuStyle.COMPACT, language), 9, countY, width - 18, .5f, .9f, 1);
        if (tall) MenuStyle.text(font, paged(controls, width - 18, MenuStyle.COMPACT, language), 9, controlsY, width - 18, .8f, .86f, 1);
        else MenuStyle.label(font, paged(controls, width - 18, 1, language), 9, controlsY, width - 18, .8f, .86f, 1);
    }

    private String paged(String text, int width, float scale, TextLanguage language) {
        return MenuStyle.pageText(language, text, width, scale, ticks);
    }

    private static LineMetrics compactMetrics(String text, TextLanguage language) {
        return HostText.shared().shape(text, TextFace.COMPACT, language).metrics();
    }

    private String cellLabel(int index) {
        if (index < characterCells()) return characters().charAt(index) == ' ' ? UiMessages.text("menu.text.spc_caps") : Character.toString(characters().charAt(index));
        return switch (actionCell(index)) {
            case 50 -> page == 0 ? "ABC" : page == 1 ? "SYM" : "abc";
            case 51 -> UiMessages.text("menu.text.spc_caps");
            // The numeric keypad's five wide cells name their action; the 10-column keyboard abbreviates.
            case 52 -> numeric() ? UiMessages.text("menu.text.delete") : UiMessages.text("menu.text.del_caps");
            case 53 -> numeric() ? UiMessages.text("menu.text.clear") : UiMessages.text("menu.text.clr_caps");
            case 54 -> defaultValue == null ? (numeric() ? UiMessages.text("menu.text.undo") : UiMessages.text("menu.text.undo_caps")) : numeric() ? UiMessages.text("menu.text.default") : UiMessages.text("menu.text.def_caps");
            case 55 -> "<";
            case 56 -> ">";
            case 57 -> "|<";
            case 58 -> ">|";
            case 59 -> numeric() ? UiMessages.text("menu.text.set") : UiMessages.text("menu.text.set_caps");
            default -> "";
        };
    }
    private String cellDescription() {
        if (cell < characterCells()) return insertDescription(characters().charAt(cell));
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
            case 59 -> UiMessages.text("menu.text.set_and_close");
            default -> insertDescription(characters().charAt(cell));
        };
    }
    private String insertDescription(char key) {
        if (key == ' ') return UiMessages.text("menu.text.insert_a_space");
        if (!numeric()) return UiMessages.text("menu.text.insert_character", java.util.Map.of("character", String.valueOf(key)));
        return switch (key) {
            case '-' -> UiMessages.text("menu.text.insert_minus");
            case '+' -> UiMessages.text("menu.text.insert_plus");
            case '.' -> UiMessages.text("menu.text.insert_decimal");
            case 'e' -> UiMessages.text("menu.text.insert_exponent");
            default -> UiMessages.text("menu.text.insert_character", java.util.Map.of("character", String.valueOf(key)));
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
