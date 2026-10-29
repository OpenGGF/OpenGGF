package com.openggf.game.recording.menu;

import com.openggf.i18n.UiMessages;
import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MenuStyle;
import com.openggf.game.MenuTextEditor;
import java.util.ArrayList;
import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.game.recording.UserRecordingPlaybackOptions;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UserRecordingMenu {
    private static final Logger LOGGER = Logger.getLogger(UserRecordingMenu.class.getName());

    private final UserRecordingMenuState state;
    private final PixelFont font;
    private InputHandler menuInput;
    private final PlaybackStarter playbackStarter;
    private String statusText;
    private boolean optionsOpen;
    private boolean detailsOpen;
    private int optionCursor;
    private int detailScroll;
    private MenuTextEditor targetEditor;

    public UserRecordingMenu(String gameId,
            List<UserRecordingEntry> entries,
            PixelFont font,
            PlaybackStarter playbackStarter) {
        this.state = new UserRecordingMenuState(gameId, entries);
        this.font = font;
        this.playbackStarter = Objects.requireNonNull(playbackStarter, "playbackStarter");
    }

    public void update(InputHandler input) {
        int beforeCursor = state.cursor(), beforeOption = optionCursor, beforeScroll = detailScroll;
        var beforeOptions = state.options();
        boolean wasOptions = optionsOpen, wasDetails = detailsOpen;
        boolean wasEditor = targetEditor != null;
        boolean outcome = false;
        try {
            menuInput = input;
            if (targetEditor != null) {
                targetEditor.update(input);
                switch (targetEditor.consumeResult()) {
                    case ACCEPTED -> {
                        try {
                            state.setTargetFrame(Integer.parseInt(targetEditor.value()));
                            MenuFeedback.emit(CONFIRM);
                            targetEditor = null;
                        } catch (NumberFormatException invalid) {
                            targetEditor.reject(UiMessages.text("menu.recordings.ui.enter_a_whole_frame_number"));
                        }
                    }
                    case CANCELLED -> targetEditor = null;
                    case NONE -> { }
                }
                return;
            }
            if (detailsOpen) {
                if (MenuInput.back(input)) detailsOpen = false;
                else if (MenuInput.up(input)) detailScroll = Math.max(0, detailScroll - 1);
                else if (MenuInput.down(input)) detailScroll = Math.min(Math.max(0, detailLines().size() - detailCapacity()), detailScroll + 1);
                return;
            }
            if (optionsOpen) {
                if (MenuInput.back(input)) { optionsOpen = false; return; }
                if (MenuInput.up(input)) optionCursor = Math.floorMod(optionCursor - 1, 5);
                if (MenuInput.down(input)) optionCursor = (optionCursor + 1) % 5;
                if (MenuInput.left(input) || MenuInput.right(input)) {
                    if (optionCursor == 0) {
                        if (MenuInput.left(input)) state.pressLeft(); else state.pressRight();
                    } else if (optionCursor == 1) state.pressP();
                    else if (optionCursor == 2) state.pressF();
                }
                if (MenuInput.accept(input)) {
                    switch (optionCursor) {
                        case 0 -> { targetEditor = new MenuTextEditor(UiMessages.text("menu.recordings.ui.target_frame_caps"), Integer.toString(state.options().targetFrame()), 9, null, MenuTextEditor.Mode.INTEGER); targetEditor.deferAcceptanceFeedback(); }
                        case 1 -> state.pressP();
                        case 2 -> state.pressF();
                        case 3 -> {
                            state.pressPlay();
                            if (state.selectedEntry() == null || !state.selectedEntry().isLoadable()) { MenuFeedback.emit(ERROR); outcome = true; }
                        }
                        case 4 -> { detailsOpen = true; detailScroll = 0; }
                        default -> { }
                    }
                }
            } else if (MenuInput.accept(input) && state.selectedEntry() != null) {
                optionsOpen = true;
                optionCursor = 3;
                return;
            } else {
                state.update(input);
            }
            UserRecordingMenuState.PlaybackRequest request = state.consumePlaybackRequest();
            if (request != null) {
                try {
                    playbackStarter.start(request.entry(), request.options());
                    statusText = null;
                    MenuFeedback.emit(CONFIRM); outcome = true;
                } catch (Exception ex) {
                    statusText = UiMessages.text("menu.recordings.playback_failed", java.util.Map.of("error", String.valueOf(ex.getMessage())));
                    MenuFeedback.emit(ERROR); outcome = true;
                    LOGGER.log(Level.WARNING, "Failed to start user recording playback", ex);
                }
            }
        } finally {
            if (!wasEditor && !outcome) {
                if (MenuInput.back(input)) MenuFeedback.emit(CANCEL);
                else if ((!wasOptions && optionsOpen) || (!wasDetails && detailsOpen) || targetEditor != null)
                    MenuFeedback.emit(CONFIRM);
                else if (beforeCursor != state.cursor() || beforeOption != optionCursor || beforeScroll != detailScroll
                        || !beforeOptions.equals(state.options())) MenuFeedback.emit(MenuInput.accept(input) ? CONFIRM : NAVIGATE);
                else if (MenuInput.accept(input) && state.selectedEntry() == null) MenuFeedback.emit(ERROR);
            }
        }
    }

    private String confirmHint() { return menuInput == null ? "Enter" : MenuInput.confirmLabel(menuInput); }
    private String backHint() { return menuInput == null ? "Esc" : MenuInput.backLabel(menuInput); }

    public void render() {
        if (font == null) return;
        if (targetEditor != null) { targetEditor.render(font, 320); return; }
        font.beginMegaBatch();
        try {
            if (detailsOpen) renderDetails();
            else if (optionsOpen) renderOptions();
            else renderLibrary();
        } finally {
            font.endMegaBatch();
        }
    }

    private static int linePitch() { return Math.max(12, com.openggf.game.MenuChrome.labelLineHeight() + 2); }
    private static int libraryCapacity() { return Math.max(1, 96 / linePitch()); }
    private static int detailCapacity() { return Math.max(1, 138 / linePitch()); }

    private void renderLibrary() {
        List<UserRecordingEntry> entries = state.entries();
        MenuStyle.page(font, 320, UiMessages.text("menu.recordings.ui.recordings_caps"), UiMessages.text("menu.recordings.position", java.util.Map.of("game", state.gameId(), "position", entries.isEmpty() ? 0 : state.cursor() + 1, "count", entries.size())));
        if (entries.isEmpty()) {
            MenuStyle.panel(font, 9, 56, 302, 99);
            line(UiMessages.text("menu.recordings.ui.no_recordings_found"), 17, 70, 286, 1, .75f, .3f);
            line(UiMessages.text("menu.recordings.ui.record_a_run_while_playing_to_add_it_here"), 17, 94, 286, 1, 1, 1);
            line(UiMessages.text("menu.recordings.ui.recording_preferences_are_in_settings"), 17, 118, 286, .75f, .85f, 1);
        } else {
            int first = Math.max(0, Math.min(state.cursor() - libraryCapacity() / 2, entries.size() - libraryCapacity()));
            for (int i = first, y = 53; i < Math.min(entries.size(), first + libraryCapacity()); i++, y += linePitch()) {
                boolean selected = i == state.cursor();
                if (selected) MenuStyle.focusLabel(font, 9, y, 302, linePitch());
                MenuStyle.label(font, (selected ? "> " : "  ") + entries.get(i).displayName(), 13, y, 292,
                        selected ? 1 : .7f, selected ? 1 : .78f, 1);
            }
            MenuStyle.panel(font, 9, 153, 302, 41);
            List<String> info = state.selectedInfoLines();
            for (int i = 0; i < Math.min(3, info.size()); i++)
                line(info.get(i), 13, 157 + i * 12, 292, .83f, .88f, 1);
        }
        MenuStyle.footer(font, 320, UiMessages.text("menu.recordings.ui.up_down_select_recording"), UiMessages.text("menu.recordings.library_controls", java.util.Map.of("confirm", confirmHint(), "back", backHint())));
    }

    private void renderOptions() {
        MenuStyle.page(font, 320, UiMessages.text("menu.recordings.ui.recording_options_caps"), state.selectedEntry().displayName());
        String[] labels = {UiMessages.text("menu.recordings.ui.target_frame"), UiMessages.text("menu.recordings.ui.pause_on_desync"), UiMessages.text("menu.recordings.ui.fast_forward"), UiMessages.text("menu.recordings.ui.play_recording"), UiMessages.text("menu.recordings.ui.details_warnings")};
        String[] values = {Integer.toString(state.options().targetFrame()), state.options().pauseOnDesync() ? UiMessages.text("menu.recordings.ui.on") : UiMessages.text("menu.recordings.ui.off"),
                state.options().fastForward() ? UiMessages.text("menu.recordings.ui.on") : UiMessages.text("menu.recordings.ui.off"), "", ""};
        int pitch = Math.max(24, com.openggf.game.MenuChrome.labelLineHeight() + 8);
        int visible = Math.max(1, (178 - 52) / pitch);
        int first = Math.clamp(optionCursor - visible / 2, 0, Math.max(0, labels.length - visible));
        for (int i = first; i < labels.length && i < first + visible; i++) {
            int y = 52 + (i - first) * pitch;
            MenuStyle.panel(font, 9, y, 302, 21);
            if (optionCursor == i) MenuStyle.focus(font, 9, y, 302, 21);
            MenuStyle.label(font, labels[i], 17, MenuStyle.textY(y, 21, 1), 188, 1, 1, 1);
            boolean changed = i == 0 ? state.options().targetFrame() != Math.max(0, state.selectedEntry().frameCount() - 1)
                    : i == 1 ? state.options().pauseOnDesync() : i == 2 && state.options().fastForward();
            MenuStyle.label(font, values[i], 211, MenuStyle.textY(y, 21, 1), 92, 1, changed ? .72f : 1, changed ? .25f : 1);
        }
        String warning = statusText != null ? statusText : state.warningText();
        if (warning != null) line(warning, 9, 182, 302, 1, .65f, .3f);
        MenuStyle.footer(font, 320, UiMessages.text("menu.recordings.ui.up_down_option_left_right_change"),
                UiMessages.text("menu.recordings.option_controls", java.util.Map.of("confirm", confirmHint(), "play", optionCursor == 3 ? "yes" : "no", "back", backHint())));
    }

    private List<String> detailLines() {
        List<String> lines = new ArrayList<>();
        List<String> messages = new ArrayList<>(state.selectedInfoLines());
        if (state.warningText() != null) messages.add(state.warningText());
        if (statusText != null) messages.add(statusText);
        for (String message : messages) {
            lines.addAll(com.openggf.graphics.text.HostText.shared().wrap(message, 292,
                    com.openggf.graphics.text.TextFace.COMPACT, com.openggf.graphics.text.HostTextContext.current()));
        }
        return lines;
    }

    private void renderDetails() {
        List<String> lines = detailLines();
        detailScroll = Math.min(detailScroll, Math.max(0, lines.size() - detailCapacity()));
        MenuStyle.page(font, 320, UiMessages.text("menu.recordings.ui.recording_details_caps"), state.selectedEntry().displayName());
        MenuStyle.panel(font, 9, 48, 302, 145);
        for (int i = detailScroll, y = 53; i < Math.min(lines.size(), detailScroll + detailCapacity()); i++, y += linePitch())
            line(lines.get(i), 13, y, 292, .9f, .94f, 1);
        MenuStyle.footer(font, 320, UiMessages.text("menu.recordings.ui.up_down_scroll_details"), UiMessages.text("menu.recordings.details_controls", java.util.Map.of("back", backHint())));
    }

    private void line(String text, int x, int y, int width, float r, float g, float b) {
        MenuStyle.text(font, text, x, y, width, r, g, b);
    }

    public boolean consumeCloseRequested() {
        return state.consumeCloseRequested();
    }

    public UserRecordingMenuState state() {
        return state;
    }

    @FunctionalInterface
    public interface PlaybackStarter {
        void start(UserRecordingEntry entry, UserRecordingPlaybackOptions options) throws Exception;
    }
}
