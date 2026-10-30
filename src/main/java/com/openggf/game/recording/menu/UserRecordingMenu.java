package com.openggf.game.recording.menu;

import com.openggf.i18n.UiMessages;
import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFieldLines;
import com.openggf.game.MenuStyle;
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
        ticks++;
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
                        targetEditor.reject(UiMessages.text("menu.recordings.ui.enter_a_whole_frame_number"));
                    }
                }
                case CANCELLED -> targetEditor = null;
                case NONE -> { }
            }
            return;
        }
        if (detailsOpen) {
            if (MenuInput.back(input) || MenuInput.textDetails(input)) { detailsOpen = false; MenuFeedback.emit(CANCEL); }
            else if (MenuInput.up(input) || MenuInput.down(input) || MenuInput.left(input) || MenuInput.right(input)) {
                int previous = detailScroll;
                int step = MenuInput.left(input) || MenuInput.right(input) ? Math.max(1, detailCapacity()-1) : 1;
                detailScroll = Math.clamp(detailScroll + (MenuInput.down(input) || MenuInput.right(input) ? step : -step),
                        0, Math.max(0, detailLines().size() - detailCapacity()));
                if (previous != detailScroll) MenuFeedback.emit(NAVIGATE);
            }
            return;
        }
        if (MenuInput.back(input)) {
            closeRequested = true;
            MenuFeedback.emit(CANCEL);
            return;
        }
        if (MenuInput.textDetails(input)) {
            detailsOpen = true; detailScroll = 0; MenuFeedback.emit(CONFIRM); return;
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
                    targetEditor = new MenuTextEditor(UiMessages.text("menu.recordings.ui.target_frame_caps"), Integer.toString(state.options().targetFrame()),
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
            statusText = UiMessages.text("menu.userrecordingmenu.playback_failed") + failure.getMessage();
            MenuFeedback.emit(ERROR);
            LOGGER.log(Level.WARNING, UiMessages.text("menu.userrecordingmenu.failed_to_start_user_recording_playback"), failure);
        }
    }

    private String confirmHint() { return menuInput == null ? UiMessages.text("menu.contentimportscreen.enter") : MenuInput.confirmLabel(menuInput); }
    private String backHint() { return menuInput == null ? UiMessages.text("menu.contentimportscreen.esc") : MenuInput.backLabel(menuInput); }

    public void render() {
        if (font == null) return;
        if (targetEditor != null) { targetEditor.render(font, W); return; }
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
        MenuChrome.page(font, W, page(UiMessages.text("menu.recordings.ui.recording_options_caps"), W - 70, 1), gameName(state.gameId()), page(entry.displayName(), W - 20, MenuStyle.COMPACT));
        var options = state.options();
        int defaultTarget = Math.max(0, entry.frameCount() - 1);
        String[] labels = {UiMessages.text("menu.userrecordingmenu.pause_at_frame"), UiMessages.text("menu.recordings.ui.pause_on_desync"), UiMessages.text("menu.recordings.ui.fast_forward")};
        for (int i = 0; i < labels.length; i++) {
            int y = 52 + i * optionPitch();
            boolean focused = optionCursor == i;
            if (focused) MenuChrome.focus(font, 10, y, W - 20, optionPitch() - 3, ticks);
            boolean changed = i == 0 ? options.targetFrame() != defaultTarget
                    : i == 1 ? options.pauseOnDesync() : options.fastForward();
            if (changed) MenuChrome.changedMarker(font, 13, y + 4);
            int right = W - 18 - (focused ? 8 : 0);
            int valueX;
            if (i == 0) {
                String value = String.format("%,d", options.targetFrame());
                valueX = right - MenuChrome.compactWidth(value);
                MenuChrome.compact(font, value, valueX, y + 3 + MenuChrome.compactMetrics().above(), changed ? MenuChrome.AMBER : MenuChrome.WHITE);
            } else {
                valueX = right - MenuChrome.TOGGLE_W;
                MenuChrome.toggle(font, valueX, y + 3, i == 1 ? options.pauseOnDesync() : options.fastForward(), true);
            }
            MenuChrome.compact(font, page(labels[i], valueX - 34, MenuStyle.COMPACT), 20, y + 3 + MenuChrome.compactMetrics().above(), focused ? MenuChrome.WHITE : MenuChrome.TEXT);
            if (focused) {
                MenuChrome.triangle(font, valueX - 7, y + 4, 3, 'L', MenuChrome.GOLD, 1f);
                MenuChrome.triangle(font, W - 21, y + 4, 3, 'R', MenuChrome.GOLD, 1f);
            }
        }
        MenuChrome.button(font, 10, playTop(), W - 20, playHeight(), page(UiMessages.text("menu.recordings.ui.play_recording"), W - 28, MenuStyle.COMPACT), optionCursor == 3, true, ticks);
        MenuChrome.button(font, 10, detailsTop(), W - 20, detailsHeight(), page(UiMessages.text("menu.userrecordingmenu.details_and_warnings"), W - 28, MenuStyle.COMPACT), optionCursor == 4, false, ticks);
        renderSummary(entry);
        List<MenuChrome.Hint> hints = new ArrayList<>();
        hints.add(MenuChrome.upDown(menuInput, UiMessages.text("hub.ui.select_caps")));
        if (optionCursor < 3) hints.add(MenuChrome.leftRight(menuInput, UiMessages.text("launch.ui.change_caps")));
        hints.add(MenuChrome.hint(confirmHint(), optionCursor == 3 ? "PLAY" : optionCursor == 0 ? UiMessages.text("settings.screen.edit_caps") : UiMessages.text("hub.ui.select_caps")));
        hints.add(MenuChrome.hint(backHint(), UiMessages.text("language.picker.back")));
        hints.add(MenuChrome.hint(MenuChrome.detailsKey(menuInput), UiMessages.text("menu.path.details_caps")));
        MenuChrome.footer(font, W, summaryTop() + MenuChrome.compactLineHeight() + 12 > MenuChrome.FOOTER_Y ? statusText : null, hints, controller(), ticks);
    }

    private static int optionPitch() { return Math.max(16, MenuChrome.compactLineHeight() + 7); }
    private static int playTop() { return 52 + 3 * optionPitch() + 8; }
    private static int playHeight() { return Math.max(18, MenuChrome.compactLineHeight() + 8); }
    private static int detailsTop() { return playTop() + Math.max(24, playHeight() + 6); }
    private static int detailsHeight() { return Math.max(15, MenuChrome.compactLineHeight() + 6); }
    private static int summaryTop() { return detailsTop() + detailsHeight() + 10; }
    private String page(String text, int pixels, float scale) {
        return MenuStyle.pageText(MenuStyle.language(font), text, pixels, scale, ticks);
    }

    /** Same facts as the library card; long rows page inside its measured summary area. */
    private void renderSummary(UserRecordingEntry entry) {
        int top = summaryTop(), height = MenuChrome.FOOTER_Y - top - 5;
        if (height < MenuChrome.compactLineHeight() + 7) return;
        MenuChrome.descriptionBox(font, 9, top, W - 18, height, List.of(), List.of());
        var manifest = entry.manifest();
        var launch = manifest == null ? null : manifest.launchContext();
        List<String> facts = new ArrayList<>();
        if (launch != null) {
            facts.add(PlaybackFacts.zoneAct(launch.zone(), launch.act()));
            facts.add(PlaybackFacts.team(launch.mainCharacter(), launch.sidekickCharacters()));
        }
        facts.add(PlaybackFacts.duration(entry.frameCount()) + " long");
        String warning = PlaybackFacts.versionWarning(entry.versionWarning());
        String status = statusText != null ? statusText : warning != null ? warning : UiMessages.text("menu.userrecordingmenu.ready_to_replay_from_its_recorded_act_start");
        float[] statusColor = statusText != null ? MenuChrome.RED : warning != null ? MenuChrome.AMBER : MenuChrome.GREEN;
        String[] text = {String.join("   ", facts), UiMessages.text("menu.userrecordingmenu.recorded") + PlaybackFacts.date(manifest != null
                && manifest.createdAt() != null ? manifest.createdAt() : entry.modifiedAt()), status};
        float[][] colors = {MenuChrome.MUTED, MenuChrome.DIM, statusText == null && warning == null ? MenuChrome.TEXT : statusColor};
        int pitch = Math.max(12, MenuChrome.compactLineHeight() + 3);
        int capacity = Math.max(1, (height - 7) / pitch);
        int first = Math.floorMod(ticks / 90, (text.length + capacity - 1) / capacity) * capacity;
        for (int i = first; i < Math.min(text.length, first + capacity); i++) {
            int y = top + 5 + (i - first) * pitch + MenuChrome.compactMetrics().above();
            int x = i == 2 ? 26 : 15;
            if (i == 2) MenuChrome.icon(font, statusText == null && warning == null ? MenuChrome.Icon.CHECK : MenuChrome.Icon.WARNING, 15, y, statusColor);
            MenuChrome.compact(font, page(text[i], W - x - 17, MenuStyle.COMPACT), x, y, colors[i]);
        }
    }

    private boolean controller() { return MenuChrome.controller(menuInput); }

    private static String gameName(String gameId) {
        return switch (gameId) {
            case "s1" -> UiMessages.text("launch.ui.sonic_1_caps");
            case "s2" -> UiMessages.text("launch.ui.sonic_2_caps");
            case "s3k" -> UiMessages.text("launch.ui.sonic_3_k_caps");
            default -> gameId.toUpperCase(Locale.ROOT);
        };
    }

    private int detailLabelWidth() { return MenuFieldLines.labelWidth(detailFields()); }

    private List<MenuFieldLines.Field> detailFields() {
        List<MenuFieldLines.Field> fields = new ArrayList<>();
        UserRecordingEntry entry = state.selectedEntry();
        if (!entry.isLoadable()) fields.add(new MenuFieldLines.Field(UiMessages.text("menu.userrecordingmenu.status"), entry.loadError(), MenuChrome.RED));
        for (PlaybackFacts.Fact fact : PlaybackFacts.recording(entry)) {
            fields.add(new MenuFieldLines.Field(fact.label(), fact.value(),
                    fact.tone() == PlaybackFacts.Tone.WARNING ? MenuChrome.AMBER : MenuChrome.TEXT));
        }
        if (statusText != null) fields.add(new MenuFieldLines.Field(UiMessages.text("menu.userrecordingmenu.playback"), statusText, MenuChrome.RED));
        var options = state.options();
        fields.add(new MenuFieldLines.Field(UiMessages.text("menu.userrecordingmenu.pause_at_frame"), Integer.toString(options.targetFrame())));
        fields.add(new MenuFieldLines.Field(UiMessages.text("menu.recordings.ui.pause_on_desync"), UiMessages.text(options.pauseOnDesync() ? "mutator.menu.on" : "mutator.menu.off")));
        fields.add(new MenuFieldLines.Field(UiMessages.text("menu.recordings.ui.fast_forward"), UiMessages.text(options.fastForward() ? "mutator.menu.on" : "mutator.menu.off")));
        fields.add(new MenuFieldLines.Field(null, UiMessages.text("menu.recordings.ui.play_recording")));
        fields.add(new MenuFieldLines.Field(null, UiMessages.text("menu.userrecordingmenu.details_and_warnings")));
        var bounded = new ArrayList<MenuFieldLines.Field>();
        for (var field : fields) {
            if (field.label() != null && MenuChrome.compactWidth(field.label()) > 112) {
                bounded.add(new MenuFieldLines.Field(null, field.label(), MenuChrome.MUTED));
                bounded.add(new MenuFieldLines.Field(null, field.value(), field.color()));
            } else bounded.add(field);
        }
        return bounded;
    }

    private List<MenuFieldLines.Line> detailLines() {
        int labelWidth = detailLabelWidth();
        return MenuFieldLines.layout(detailFields(), labelWidth, W - 42 - labelWidth);
    }

    private static int detailPitch() { return Math.max(12, MenuChrome.compactLineHeight() + 3); }
    private static int detailCapacity() { return Math.max(1, 142 / detailPitch()); }

    private void renderDetails() {
        List<MenuFieldLines.Line> lines = detailLines();
        detailScroll = Math.min(detailScroll, Math.max(0, lines.size() - detailCapacity()));
        MenuChrome.page(font, W, UiMessages.text("menu.recordings.ui.recording_details_caps"), gameName(state.gameId()),
                page(state.selectedEntry().displayName(), W - 20, MenuStyle.COMPACT));
        MenuChrome.descriptionBox(font, 9, 48, W - 18, 150, List.of(), List.of());
        MenuFieldLines.draw(font, lines, detailScroll, detailCapacity(), 16, 54, detailPitch(), detailLabelWidth());
        MenuChrome.scrollbar(font, W - 15, 52, 142, detailScroll, detailCapacity(), lines.size());
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (lines.size() > detailCapacity()) hints.add(MenuChrome.upDown(menuInput, UiMessages.text("menu.details.scroll")));
        hints.add(MenuChrome.hint(backHint(), UiMessages.text("language.picker.back")));
        MenuChrome.footer(font, W, null, hints, controller(), ticks);
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
