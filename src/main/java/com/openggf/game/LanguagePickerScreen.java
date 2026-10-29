package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.i18n.UiLanguage;
import com.openggf.i18n.UiMessages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Catalog-discovered language choices. Each endonym is shaped in its own language. */
public final class LanguagePickerScreen {
    @FunctionalInterface
    public interface Selection {
        /** A rejected save keeps this picker open so the same choice can be retried. */
        void choose(String tag) throws IOException;
    }

    record Choice(String tag, String name) { }
    private static final int TOP = 43;
    private static final int BOTTOM = 178;
    private final List<Choice> choices;
    private final Selection selection;
    private int selected;
    private int scroll;
    private int width = 320;
    private int ticks;
    private boolean closed;
    private String error;
    private InputHandler lastInput;

    public LanguagePickerScreen(String currentTag, Selection selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
        List<Choice> options = new ArrayList<>();
        options.add(new Choice("auto", ""));
        for (UiLanguage language : UiMessages.languages()) {
            options.add(new Choice(language.tag(), language.name()));
        }
        choices = List.copyOf(options);
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).tag().equalsIgnoreCase(currentTag)) selected = i;
        }
        keepVisible();
    }

    public boolean update(InputHandler input) {
        return update(input, MenuInput.pointer(input, width, 224));
    }

    boolean update(InputHandler input, MenuInput.Pointer pointer) {
        if (closed || input == null) return closed;
        ticks++;
        lastInput = input;
        if (MenuInput.textBack(input) || pointer.rightPressed()) {
            closed = true;
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return true;
        }
        int before = selected;
        if (MenuInput.textUp(input)) selected = Math.floorMod(selected - 1, choices.size());
        if (MenuInput.textDown(input)) selected = (selected + 1) % choices.size();
        int pitch = rowPitch();
        boolean clicked = false;
        for (int row = 0; row < visibleRows(); row++) {
            int index = scroll + row;
            if (index >= choices.size()) break;
            if (pointer.leftPressed() && pointer.over(12, TOP + row * pitch, width - 24, pitch)) {
                selected = index;
                clicked = true;
            }
        }
        if (before != selected) {
            error = null;
            MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        }
        keepVisible();
        if (MenuInput.textAccept(input) || clicked) choose();
        return closed;
    }

    /** The same confirmation boundary is used by keyboard, controller and pointer. */
    void choose() {
        try {
            selection.choose(choices.get(selected).tag());
            closed = true;
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        } catch (IOException e) {
            error = UiMessages.text("language.save_failed");
            MenuFeedback.emit(MenuFeedback.Cue.ERROR);
        } catch (IllegalArgumentException e) {
            error = UiMessages.text("language.invalid_choice");
            MenuFeedback.emit(MenuFeedback.Cue.ERROR);
        }
    }

    public void render(PixelFont font, int viewportWidth) {
        if (font == null) return;
        width = Math.max(320, viewportWidth);
        keepVisible();
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            MenuChrome.header(font, width, UiMessages.text("language.picker.title"), null);
            int pitch = rowPitch();
            for (int row = 0; row < visibleRows(); row++) {
                int index = scroll + row;
                if (index >= choices.size()) break;
                Choice choice = choices.get(index);
                int y = TOP + row * pitch;
                boolean focused = index == selected;
                if (focused) MenuChrome.focus(font, 12, y, width - 24, pitch - 1, ticks);
                String name = name(choice);
                TextLanguage language = language(choice);
                int textY = y + 2 + HostText.shared().lineMetrics(TextFace.COMPACT, language).above();
                float[] color = focused ? MenuChrome.GOLD : MenuChrome.TEXT;
                int labelWidth = width - 44;
                if (font instanceof MenuPixelFont menuFont) {
                    menuFont.drawText(MenuStyle.pageText(language, name, labelWidth, MenuStyle.COMPACT, ticks),
                            21, textY, MenuStyle.COMPACT, color[0], color[1], color[2], 1, language);
                } else {
                    String fitted = MenuStyle.pageText(language, name, labelWidth, MenuStyle.COMPACT, ticks);
                    font.drawText(fitted, 21, textY, MenuStyle.COMPACT, color[0], color[1], color[2], 1);
                }
            }
            if (choices.size() > visibleRows()) {
                int track = bodyBottom() - TOP;
                int thumb = Math.max(8, track * visibleRows() / choices.size());
                int y = TOP + (track - thumb) * scroll / Math.max(1, choices.size() - visibleRows());
                MenuChrome.fill(font, width - 7, TOP, 2, track, MenuChrome.EDGE);
                MenuChrome.fill(font, width - 8, y, 4, thumb, MenuChrome.CYAN);
            }
            MenuChrome.compact(font, MenuStyle.pageText(error == null ? quality() : error, width - 24, MenuStyle.COMPACT, ticks),
                    12, MenuChrome.FOOTER_Y - 3 - MenuChrome.compactLineHeight() + MenuChrome.compactMetrics().above(),
                    error == null ? MenuChrome.MUTED : MenuChrome.AMBER);
            MenuChrome.footer(font, width, null, List.of(
                    MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("language.picker.choose")),
                    MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("language.picker.back"))),
                    MenuChrome.controller(lastInput), ticks);
        } finally {
            font.endMegaBatch();
        }
    }

    private static TextLanguage language(Choice choice) {
        return choice.tag().equals("auto") ? HostTextContext.current() : TextLanguage.of(choice.tag());
    }

    private static String name(Choice choice) {
        return choice.tag().equals("auto") ? UiMessages.text("language.auto") : choice.name();
    }

    private String quality() {
        String tag = choices.get(selected).tag();
        if (tag.equals("auto")) return UiMessages.text("language.picker.pending");
        UiLanguage language = UiMessages.languages().stream().filter(l -> l.tag().equals(tag)).findFirst().orElseThrow();
        if (language.quality().equals("source")) return UiMessages.text("language.quality.source");
        return UiMessages.text(language.quality().equals("machine") ? "language.quality.machine" : "language.quality.community",
                java.util.Map.of("translated", language.translated(), "total", language.total()));
    }

    private int rowPitch() {
        int pitch = MenuChrome.compactLineHeight() + 5;
        for (Choice choice : choices) {
            pitch = Math.max(pitch, HostText.shared().lineMetrics(TextFace.COMPACT, language(choice)).lineHeight() + 5);
        }
        return pitch;
    }

    private int bodyBottom() {
        return Math.min(BOTTOM, MenuChrome.FOOTER_Y - MenuChrome.compactLineHeight() - 7);
    }

    private int visibleRows() { return Math.max(1, (bodyBottom() - TOP) / rowPitch()); }

    private void keepVisible() {
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + visibleRows()) scroll = selected - visibleRows() + 1;
    }

    static String displayName(String tag) {
        if (tag.equalsIgnoreCase("auto")) return UiMessages.text("language.auto");
        return UiMessages.languages().stream().filter(language -> language.tag().equalsIgnoreCase(tag))
                .map(UiLanguage::name).findFirst().orElse(tag);
    }
}
