package com.openggf.game.recording.menu;

import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFieldLines;
import com.openggf.game.playback.PlaybackFacts;
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
    private boolean closeRequested;
    private boolean detailsOpen;
    private int optionCursor = 3;
    private int detailScroll;
    private MenuTextEditor targetEditor;

    public UserRecordingMenu(String gameId,
            UserRecordingEntry entry,
            PixelFont font,
            PlaybackStarter playbackStarter) {
        this.state = new UserRecordingMenuState(gameId, entry);
        this.font = font;
        this.playbackStarter = Objects.requireNonNull(playbackStarter, "playbackStarter");
    }

    public void update(InputHandler input) {
        menuInput = Objects.requireNonNull(input, "input");
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
            if (MenuInput.back(input)) { detailsOpen = false; MenuFeedback.emit(CANCEL); }
            else if (MenuInput.up(input) || MenuInput.down(input)) {
                int previous = detailScroll;
                detailScroll = Math.clamp(detailScroll + (MenuInput.down(input) ? 1 : -1),
                        0, Math.max(0, detailLines().size() - 12));
                if (previous != detailScroll) MenuFeedback.emit(NAVIGATE);
            }
            return;
        }
        if (MenuInput.back(input)) {
            closeRequested = true;
            MenuFeedback.emit(CANCEL);
            return;
        }
        int beforeCursor = optionCursor;
        var beforeOptions = state.options();
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
                case 0 -> {
                    targetEditor = new MenuTextEditor("TARGET FRAME", Integer.toString(state.options().targetFrame()),
                            9, null, MenuTextEditor.Mode.INTEGER);
                    targetEditor.deferAcceptanceFeedback();
                    MenuFeedback.emit(CONFIRM);
                }
                case 1 -> state.pressP();
                case 2 -> state.pressF();
                case 3 -> { startPlayback(); return; }
                case 4 -> { detailsOpen = true; detailScroll = 0; MenuFeedback.emit(CONFIRM); }
                default -> { }
            }
        }
        if (beforeCursor != optionCursor || !beforeOptions.equals(state.options()))
            MenuFeedback.emit(MenuInput.accept(input) ? CONFIRM : NAVIGATE);
    }

    private void startPlayback() {
        if (!state.selectedEntry().isLoadable()) { MenuFeedback.emit(ERROR); return; }
        try {
            playbackStarter.start(state.selectedEntry(), state.options());
            statusText = null;
            MenuFeedback.emit(CONFIRM);
        } catch (Exception failure) {
            statusText = "Playback failed: " + failure.getMessage();
            MenuFeedback.emit(ERROR);
            LOGGER.log(Level.WARNING, "Failed to start user recording playback", failure);
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
            else renderOptions();
        } finally {
            font.endMegaBatch();
        }
    }

    private static final int W = 320;
    private int ticks;

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
        renderSummary(entry);
        List<MenuChrome.Hint> hints = new ArrayList<>();
        hints.add(MenuChrome.upDown(menuInput, "SELECT"));
        if (optionCursor < 3) hints.add(MenuChrome.leftRight(menuInput, "CHANGE"));
        hints.add(MenuChrome.hint(confirmHint(), optionCursor == 3 ? "PLAY" : optionCursor == 0 ? "EDIT" : "SELECT"));
        hints.add(MenuChrome.hint(backHint(), "BACK"));
        MenuChrome.footer(font, W, null, hints, controller());
    }

    /** Same card as the library summary: start and team, when it was made, then readiness or the failure. */
    private void renderSummary(UserRecordingEntry entry) {
        MenuChrome.descriptionBox(font, 9, 157, W - 18, 43, List.of(), List.of());
        var manifest = entry.manifest();
        var launch = manifest == null ? null : manifest.launchContext();
        List<String> facts = new ArrayList<>();
        if (launch != null) {
            facts.add(PlaybackFacts.zoneAct(launch.zone(), launch.act()));
            facts.add(PlaybackFacts.team(launch.mainCharacter(), launch.sidekickCharacters()));
        }
        facts.add(PlaybackFacts.duration(entry.frameCount()) + " long");
        MenuChrome.compact(font, MenuChrome.fitCompact(String.join("   ", facts), W - 32), 15, 162, MenuChrome.MUTED);
        MenuChrome.compact(font, MenuChrome.fitCompact("Recorded " + PlaybackFacts.date(manifest != null
                && manifest.createdAt() != null ? manifest.createdAt() : entry.modifiedAt()), W - 32), 15, 174, MenuChrome.DIM);
        String warning = PlaybackFacts.versionWarning(entry.versionWarning());
        String status = statusText != null ? statusText : warning != null ? warning : "Ready to replay from its recorded act start";
        float[] color = statusText != null ? MenuChrome.RED : warning != null ? MenuChrome.AMBER : MenuChrome.GREEN;
        MenuChrome.icon(font, statusText == null && warning == null ? MenuChrome.Icon.CHECK : MenuChrome.Icon.WARNING,
                15, 186, color);
        MenuChrome.compact(font, MenuChrome.fitCompact(status, W - 43), 26, 186,
                statusText == null && warning == null ? MenuChrome.TEXT : color);
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

    private int detailLabelWidth() { return MenuFieldLines.labelWidth(detailFields()); }

    private List<MenuFieldLines.Field> detailFields() {
        List<MenuFieldLines.Field> fields = new ArrayList<>();
        UserRecordingEntry entry = state.selectedEntry();
        if (!entry.isLoadable()) fields.add(new MenuFieldLines.Field("Status", entry.loadError(), MenuChrome.RED));
        for (PlaybackFacts.Fact fact : PlaybackFacts.recording(entry)) {
            fields.add(new MenuFieldLines.Field(fact.label(), fact.value(),
                    fact.tone() == PlaybackFacts.Tone.WARNING ? MenuChrome.AMBER : MenuChrome.TEXT));
        }
        if (statusText != null) fields.add(new MenuFieldLines.Field("Playback", statusText, MenuChrome.RED));
        return fields;
    }

    private List<MenuFieldLines.Line> detailLines() {
        int labelWidth = detailLabelWidth();
        return MenuFieldLines.layout(detailFields(), labelWidth, W - 42 - labelWidth);
    }

    private void renderDetails() {
        List<MenuFieldLines.Line> lines = detailLines();
        detailScroll = Math.min(detailScroll, Math.max(0, lines.size() - 12));
        MenuChrome.page(font, W, "RECORDING DETAILS", gameName(state.gameId()),
                MenuChrome.fitCompact(state.selectedEntry().displayName(), W - 20));
        MenuChrome.descriptionBox(font, 9, 48, W - 18, 150, List.of(), List.of());
        MenuFieldLines.draw(font, lines, detailScroll, 12, 16, 54, 12, detailLabelWidth());
        MenuChrome.scrollbar(font, W - 15, 52, 142, detailScroll, 12, lines.size());
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (lines.size() > 12) hints.add(MenuChrome.upDown(menuInput, "SCROLL"));
        hints.add(MenuChrome.hint(backHint(), "BACK"));
        MenuChrome.footer(font, W, null, hints, controller());
    }

    public boolean consumeCloseRequested() {
        boolean requested = closeRequested;
        closeRequested = false;
        return requested;
    }

    public UserRecordingMenuState state() {
        return state;
    }

    @FunctionalInterface
    public interface PlaybackStarter {
        void start(UserRecordingEntry entry, UserRecordingPlaybackOptions options) throws Exception;
    }
}
