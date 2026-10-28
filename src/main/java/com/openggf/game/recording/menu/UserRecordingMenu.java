package com.openggf.game.recording.menu;

import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuStyle;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
                            targetEditor.reject("Enter a whole frame number");
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
                else if (MenuInput.down(input)) detailScroll = Math.min(Math.max(0, detailLines().size() - 12), detailScroll + 1);
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
                        case 0 -> { targetEditor = new MenuTextEditor("TARGET FRAME", Integer.toString(state.options().targetFrame()), 9, null, MenuTextEditor.Mode.INTEGER); targetEditor.deferAcceptanceFeedback(); }
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
                    statusText = "Playback failed: " + ex.getMessage();
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
        if (targetEditor != null) { targetEditor.render(font, W); return; }
        ticks++;
        font.beginMegaBatch();
        try {
            if (detailsOpen) renderDetails();
            else if (optionsOpen) renderOptions();
            else renderLibrary();
        } finally {
            font.endMegaBatch();
        }
    }

    private static final int W = 320;
    private static final int ROW_H = 12;
    private static final int LIST_TOP = 50;
    private static final int VISIBLE_ROWS = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private int ticks;

    private void renderLibrary() {
        List<UserRecordingEntry> entries = state.entries();
        MenuChrome.page(font, W, "RECORDINGS", gameName(state.gameId()), "Replay runs you recorded while playing");
        if (entries.isEmpty()) {
            MenuChrome.descriptionBox(font, 9, 60, W - 18, 64, List.of(), List.of());
            MenuChrome.icon(font, MenuChrome.Icon.FILM, 17, 70, MenuChrome.AMBER);
            MenuChrome.label(font, "No recordings yet", 29, 69, MenuChrome.AMBER);
            MenuChrome.compact(font, "Record a run while playing to add it here.", 17, 92, MenuChrome.TEXT);
            MenuChrome.compact(font, "Recording preferences are in Settings.", 17, 104, MenuChrome.MUTED);
            MenuChrome.footer(font, W, null, List.of(MenuChrome.hint(backHint(), "BACK")), controller());
            return;
        }
        String position = (state.cursor() + 1) + " / " + entries.size();
        MenuChrome.compact(font, position, W - 9 - MenuChrome.compactWidth(position), MenuChrome.HEADER_H + 7, MenuChrome.DIM);
        int first = Math.max(0, Math.min(state.cursor() - 3, entries.size() - VISIBLE_ROWS));
        for (int i = first, y = LIST_TOP; i < Math.min(entries.size(), first + VISIBLE_ROWS); i++, y += ROW_H) {
            UserRecordingEntry entry = entries.get(i);
            boolean selected = i == state.cursor();
            if (selected) MenuChrome.focus(font, 10, y, W - 22, ROW_H - 1, ticks);
            MenuChrome.icon(font, MenuChrome.Icon.FILM, 15, y + 2, selected ? MenuChrome.GOLD : MenuChrome.DIM);
            String meta = UserRecordingMenu.duration(entry.frameCount()) + "   " + date(entry, DATE);
            int metaX = W - 16 - MenuChrome.compactWidth(meta);
            MenuChrome.compact(font, meta, metaX, y + 2, selected ? MenuChrome.MUTED : MenuChrome.DIM);
            boolean warn = entry.versionWarning() != com.openggf.game.recording.RecordingVersionWarning.NONE;
            int nameRoom = metaX - 8 - 27 - (warn ? 10 : 0);
            String name = MenuChrome.fitCompact(entry.displayName(), nameRoom);
            MenuChrome.compact(font, name, 27, y + 2, selected ? MenuChrome.WHITE : MenuChrome.TEXT);
            if (warn) MenuChrome.icon(font, MenuChrome.Icon.WARNING, 27 + MenuChrome.compactWidth(name) + 4, y + 2,
                    MenuChrome.AMBER);
        }
        MenuChrome.scrollbar(font, W - 7, LIST_TOP, VISIBLE_ROWS * ROW_H, first, VISIBLE_ROWS, entries.size());
        renderSummary(state.selectedEntry(), 150);
        MenuChrome.footer(font, W, null, List.of(MenuChrome.upDown(menuInput, "SELECT"),
                MenuChrome.hint(confirmHint(), "OPTIONS"), MenuChrome.hint(backHint(), "BACK")), controller());
    }

    /** The selected recording in plain terms: where it starts, who plays, when and how long. */
    private void renderSummary(UserRecordingEntry entry, int y) {
        MenuChrome.descriptionBox(font, 9, y, W - 18, 50, List.of(), List.of());
        var manifest = entry.manifest();
        var context = manifest == null ? null : manifest.launchContext();
        MenuChrome.compact(font, MenuChrome.fitCompact(entry.displayName(), W - 32), 15, y + 5, MenuChrome.WHITE);
        String where = context == null ? "Start unknown" : String.format("Zone %02X, Act %d   %s",
                context.zone(), context.act(), team(context));
        MenuChrome.compact(font, MenuChrome.fitCompact(where, W - 32), 15, y + 17, MenuChrome.MUTED);
        String when = "Recorded " + date(entry, DATE_TIME) + "   " + String.format("%,d frames", entry.frameCount());
        MenuChrome.compact(font, MenuChrome.fitCompact(when, W - 32), 15, y + 27, MenuChrome.DIM);
        String engine = manifest == null || manifest.engineIdentity() == null ? "unknown"
                : manifest.engineIdentity().displayVersion();
        MenuChrome.compact(font, MenuChrome.fitCompact(entry.path().getFileName() + "   OpenGGF " + engine, W - 32),
                15, y + 37, MenuChrome.DIM);
    }

    private void renderOptions() {
        UserRecordingEntry entry = state.selectedEntry();
        MenuChrome.page(font, W, "RECORDING OPTIONS", gameName(state.gameId()), entry.displayName());
        var options = state.options();
        int defaultTarget = Math.max(0, entry.frameCount() - 1);
        String[] labels = {"Pause at frame", "Pause on desync", "Fast forward"};
        for (int i = 0; i < labels.length; i++) {
            int y = 52 + i * 16;
            boolean focused = optionCursor == i;
            if (focused) MenuChrome.focus(font, 10, y, W - 20, 13, ticks);
            boolean changed = i == 0 ? options.targetFrame() != defaultTarget
                    : i == 1 ? options.pauseOnDesync() : options.fastForward();
            if (changed) MenuChrome.changedMarker(font, 13, y + 4);
            MenuChrome.compact(font, labels[i], 20, y + 3, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
            int right = W - 18 - (focused ? 8 : 0);
            int valueX;
            if (i == 0) {
                String value = String.format("%,d", options.targetFrame());
                valueX = right - MenuChrome.compactWidth(value);
                MenuChrome.compact(font, value, valueX, y + 3, changed ? MenuChrome.AMBER : MenuChrome.WHITE);
            } else {
                valueX = right - MenuChrome.TOGGLE_W;
                MenuChrome.toggle(font, valueX, y + 3, i == 1 ? options.pauseOnDesync() : options.fastForward(), true);
            }
            if (focused) {
                MenuChrome.triangle(font, valueX - 7, y + 4, 3, 'L', MenuChrome.GOLD, 1f);
                MenuChrome.triangle(font, W - 21, y + 4, 3, 'R', MenuChrome.GOLD, 1f);
            }
        }
        MenuChrome.button(font, 10, 108, W - 20, 18, "Play recording", optionCursor == 3, true, ticks);
        MenuChrome.button(font, 10, 132, W - 20, 15, "Details and warnings", optionCursor == 4, false, ticks);
        String warning = statusText != null ? statusText : state.warningText();
        if (warning != null) {
            MenuChrome.icon(font, MenuChrome.Icon.WARNING, 10, 182, MenuChrome.AMBER);
            MenuChrome.compact(font, MenuChrome.fitCompact(warning, W - 32), 21, 182, MenuChrome.AMBER);
        }
        List<MenuChrome.Hint> hints = new ArrayList<>();
        hints.add(MenuChrome.upDown(menuInput, "SELECT"));
        if (optionCursor < 3) hints.add(MenuChrome.leftRight(menuInput, "CHANGE"));
        hints.add(MenuChrome.hint(confirmHint(), optionCursor == 3 ? "PLAY" : optionCursor == 0 ? "EDIT" : "SELECT"));
        hints.add(MenuChrome.hint(backHint(), "BACK"));
        MenuChrome.footer(font, W, null, hints, controller());
    }

    private boolean controller() { return MenuChrome.controller(menuInput); }

    private static String gameName(String gameId) {
        return switch (gameId) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3&K";
            default -> gameId.toUpperCase(Locale.ROOT);
        };
    }

    private static String team(com.openggf.game.recording.RecordingLaunchContext context) {
        StringBuilder team = new StringBuilder(capitalised(context.mainCharacter()));
        for (String sidekick : context.sidekickCharacters()) team.append(" & ").append(capitalised(sidekick));
        return team.toString();
    }

    private static String capitalised(String name) {
        return name == null || name.isEmpty() ? "" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String date(UserRecordingEntry entry, DateTimeFormatter format) {
        Instant at = entry.manifest() != null && entry.manifest().createdAt() != null
                ? entry.manifest().createdAt() : entry.modifiedAt();
        return at == null ? "unknown" : format.format(at.atZone(ZoneId.systemDefault()));
    }

    /** Frames at 60 Hz as minutes:seconds. */
    static String duration(int frames) {
        int seconds = Math.max(0, frames) / 60;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private List<String> detailLines() {
        List<String> lines = new ArrayList<>();
        List<String> messages = new ArrayList<>(state.selectedInfoLines());
        if (state.warningText() != null) messages.add(state.warningText());
        if (statusText != null) messages.add(statusText);
        for (String message : messages) lines.addAll(MenuStyle.chunks(message, W - 34, MenuStyle.COMPACT));
        return lines;
    }

    private void renderDetails() {
        List<String> lines = detailLines();
        detailScroll = Math.min(detailScroll, Math.max(0, lines.size() - 12));
        MenuChrome.page(font, W, "RECORDING DETAILS", gameName(state.gameId()), state.selectedEntry().displayName());
        MenuChrome.descriptionBox(font, 9, 48, W - 18, 150, List.of(), List.of());
        for (int i = detailScroll, y = 53; i < Math.min(lines.size(), detailScroll + 12); i++, y += 12)
            line(lines.get(i), 15, y, W - 34, .91f, .93f, 1);
        MenuChrome.scrollbar(font, W - 15, 52, 142, detailScroll, 12, lines.size());
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (lines.size() > 12) hints.add(MenuChrome.upDown(menuInput, "SCROLL"));
        hints.add(MenuChrome.hint(backHint(), "BACK"));
        MenuChrome.footer(font, W, null, hints, controller());
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
