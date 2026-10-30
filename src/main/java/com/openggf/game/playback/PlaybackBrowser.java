package com.openggf.game.playback;

import com.openggf.i18n.UiMessages;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuChrome;
import com.openggf.game.MasterTitlePointer;
import com.openggf.game.MenuStyle;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuFieldLines;
import com.openggf.game.recording.menu.UserRecordingMenu;
import com.openggf.graphics.PixelFont;
import com.openggf.testmode.TraceLaunchStatus;
import com.openggf.testmode.TracePlaybackFeedback;
import com.openggf.testmode.TraceRunFailureStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.MenuFeedback.Cue.*;
import static org.lwjgl.glfw.GLFW.*;

/** One library and selected-item views over the existing playback owners. */
public final class PlaybackBrowser {
    public enum Filter { ALL, RECORDINGS, TRACES, MOVIES }
    public enum Result { NONE, BACK, LAUNCHED }

    /** Navigation memory shared by title screens recreated after playback. */
    public static final class SelectionMemory {
        private PlaybackEntry selected;

        public void remember(PlaybackBrowser browser) {
            PlaybackEntry current = browser.selectedEntry();
            if (current != null) selected = current;
        }

        public void restore(PlaybackBrowser browser) {
            browser.selectEntry(selected);
        }
    }

    private sealed interface Page {
        record Library() implements Page { }
        record Recording(UserRecordingMenu menu) implements Page { }
        record Trace(TracePlaybackFeedback feedback) implements Page { }
        record Details(String title, List<MenuFieldLines.Line> lines, int labelWidth, int scroll) implements Page { }
        record Closed() implements Page { }
    }

    private enum Focus { GAME, KIND, LIST }
    private enum FooterAction { MOVE, OPEN, DETAILS, CHANGE, BACK, SCROLL }
    private MasterTitlePointer pointer;
    private int listFirst;
    private boolean mouseListFocus;
    private List<MenuChrome.FooterTarget> footerTargets = List.of();
    private List<FooterAction> footerActions = List.of();

    private static final int W = 320;
    private static final int LIST_TOP = 69;
    private static final int ROW_H = 14;
    private static final int VISIBLE_ROWS = 6;
    private static final int DETAIL_ROWS = 12;
    /** Text column inside the details box, clear of its scrollbar. */
    private static int rowHeight() { return Math.max(ROW_H, MenuChrome.compactLineHeight() + 6); }
    private static int filterPitch() { return Math.max(14, MenuChrome.compactLineHeight() + 4); }
    private static int listTop() { return 35 + 2 * filterPitch() + 6; }
    private static int visibleRows() { return Math.min(VISIBLE_ROWS, Math.max(1, (153 - listTop()) / rowHeight())); }
    private static int detailPitch() { return Math.max(12, MenuChrome.compactLineHeight() + 3); }
    private static int detailRows() { return Math.min(DETAIL_ROWS, Math.max(1, 142 / detailPitch())); }
    private static final int DETAIL_TEXT_W = W - 42;
    private static final Page.Library LIBRARY = new Page.Library();

    private final PlaybackLibrary library;
    private final boolean traceOnly;
    private final PixelFont font;
    private final PlaybackLauncher launcher;
    private final List<String> games;
    private InputHandler input;
    private String gameId;
    private Filter filter;
    private Focus focus = Focus.LIST;
    private int cursor;
    private int ticks;
    private Page page;
    private Result pendingResult = Result.NONE;

    public PlaybackBrowser(PlaybackLibrary library, String initialGameId, Filter initialFilter,
                           boolean traceOnly, PixelFont font, PlaybackLauncher launcher) {
        this.library = Objects.requireNonNull(library, "library");
        this.traceOnly = traceOnly;
        this.font = font;
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        gameId = Objects.requireNonNull(initialGameId, "initialGameId");
        filter = traceOnly ? Filter.TRACES : Objects.requireNonNull(initialFilter, "initialFilter");
        List<String> gameIds = new ArrayList<>();
        gameIds.add("");
        library.entries().stream().map(PlaybackEntry::gameId).filter(id -> !id.isBlank())
                .distinct().sorted().forEach(gameIds::add);
        if (!gameIds.contains(gameId)) gameIds.add(gameId);
        games = List.copyOf(gameIds);
        page = hasTraceFailure() ? new Page.Trace(new TracePlaybackFeedback(null, font)) : LIBRARY;
    }

    public void update(InputHandler input) { update(input, null); }

    /** One already-mapped native-page mouse snapshot, including the title's consumed wheel. */
    public void update(InputHandler input, MasterTitlePointer pointer) {
        this.input = Objects.requireNonNull(input, "input");
        ticks++;
        this.pointer = pointer != null && !input.hasLogicalOverride() && pointer.over(0, 0, W, 224) ? pointer : null;
        switch (page) {
            case Page.Library ignored -> updateLibrary();
            case Page.Recording selected -> {
                selected.menu().update(input, this.pointer);
                if (selected.menu().consumeCloseRequested()) page = LIBRARY;
            }
            case Page.Trace selected -> updateTrace(selected.feedback());
            case Page.Details details -> updateDetails(details);
            case Page.Closed ignored -> { }
        }
    }

    private void updateLibrary() {
        if (MenuInput.back(input) || pointer != null && pointer.backClick()) {
            MenuFeedback.emit(CANCEL);
            finish(Result.BACK);
            return;
        }
        if (handleFooter()) return;
        if (MenuInput.details(input)) {
            openDetails(selectedEntry());
            return;
        }
        List<PlaybackEntry> entries = visibleEntries();
        if (pointer != null) {
            int before = cursor;
            Focus previous = focus;
            if (pointer.wheel() != 0) {
                mouseListFocus = false;
                if (pointer.over(10, 35, W - 20, filterPitch())) focus = Focus.GAME;
                else if (pointer.over(10, 35 + filterPitch(), W - 20, filterPitch())) focus = Focus.KIND;
                else focus = Focus.LIST;
                if (focus != Focus.LIST) {
                    int direction = pointer.wheel() > 0 ? -1 : 1;
                    for (int i = 0; i < Math.abs(pointer.wheel()); i++) changeFilter(direction);
                    return;
                }
                cursor = Math.clamp(cursor - pointer.wheel(), 0, Math.max(0, entries.size() - 1));
            }
            if (pointer.moved() || pointer.click()) {
                for (int row = 0; row < 2; row++) {
                    if (!pointer.over(10, 35 + row * filterPitch(), W - 20, filterPitch())) continue;
                    focus = row == 0 ? Focus.GAME : Focus.KIND;
                    if (pointer.click()) { changeFilter(pointer.x() < W / 2 ? -1 : 1); return; }
                    break;
                }
                int first = firstVisible(entries.size());
                for (int i = first; i < Math.min(entries.size(), first + visibleRows()); i++) {
                    if (!pointer.over(10, listTop() + (i - first) * rowHeight(), W - 20, rowHeight())) continue;
                    cursor = i;
                    focus = Focus.LIST;
                    mouseListFocus = true; // Hover must not scroll this row away from the pointer.
                    if (pointer.click()) {
                        if (before != cursor || previous != focus) MenuFeedback.emit(NAVIGATE);
                        openSelected();
                        return;
                    }
                    break;
                }
            }
            if (before != cursor || previous != focus) MenuFeedback.emit(NAVIGATE);
        }
        int beforeCursor = cursor;
        Focus beforeFocus = focus;
        if (MenuInput.up(input)) move(-1);
        if (MenuInput.down(input)) move(1);
        if (focus == Focus.LIST) {
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_HOME)) cursor = 0;
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_END)) cursor = Math.max(0, entries.size() - 1);
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_DOWN))
                cursor = Math.min(Math.max(0, entries.size() - 1), cursor + visibleRows());
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_UP)) cursor = Math.max(0, cursor - visibleRows());
        }
        if (beforeCursor != cursor || beforeFocus != focus) {
            mouseListFocus = false;
            MenuFeedback.emit(NAVIGATE);
        }
        if (focus != Focus.LIST && (MenuInput.left(input) || MenuInput.right(input) || MenuInput.accept(input))) {
            changeFilter(MenuInput.left(input) ? -1 : 1);
        } else if (focus == Focus.LIST && MenuInput.accept(input)) openSelected();
    }

    private int firstVisible(int size) {
        int max = Math.max(0, size - visibleRows());
        if (!mouseListFocus) listFirst = Math.clamp(cursor - 2, 0, max);
        return listFirst = Math.clamp(listFirst, 0, max);
    }

    private void move(int direction) {
        mouseListFocus = false;
        if (direction < 0) {
            if (focus == Focus.LIST && cursor > 0) cursor--;
            else if (focus == Focus.LIST) focus = Focus.KIND;
            else if (focus == Focus.KIND) focus = Focus.GAME;
        } else {
            if (focus == Focus.GAME) focus = Focus.KIND;
            else if (focus == Focus.KIND) focus = Focus.LIST;
            else cursor = Math.min(Math.max(0, visibleEntries().size() - 1), cursor + 1);
        }
    }

    private boolean handleFooter() {
        if (pointer == null || !pointer.click()) return false;
        for (MenuChrome.FooterTarget target : footerTargets) {
            if (!target.contains(pointer)) continue;
            int direction = pointer.x() < target.x() + MenuChrome.keyCapWidth(target.hint().key()) / 2 ? -1 : 1;
            switch (footerActions.get(target.index())) {
                case MOVE -> { move(direction); MenuFeedback.emit(NAVIGATE); }
                case OPEN -> openSelected();
                case DETAILS -> openDetails(selectedEntry());
                case CHANGE -> changeFilter(direction);
                case BACK -> {
                    if (page instanceof Page.Details) page = LIBRARY;
                    else finish(Result.BACK);
                    MenuFeedback.emit(CANCEL);
                }
                case SCROLL -> {
                    if (page instanceof Page.Details details) scrollDetails(details, direction);
                }
            }
            return true;
        }
        return false;
    }

    private void footer(List<MenuChrome.Hint> hints, List<FooterAction> actions) {
        footerTargets = MenuChrome.footerLayout(W, null, hints, ticks).targets();
        footerActions = List.copyOf(actions);
        MenuChrome.footer(font, W, null, hints, MenuChrome.controller(input), ticks);
    }

    private void changeFilter(int direction) {
        mouseListFocus = false;
        PlaybackEntry previous = selectedEntry();
        if (focus == Focus.GAME) {
            gameId = games.get(Math.floorMod(games.indexOf(gameId) + direction, games.size()));
        } else {
            if (traceOnly) { MenuFeedback.emit(ERROR); return; }
            filter = Filter.values()[Math.floorMod(filter.ordinal() + direction, Filter.values().length)];
        }
        cursor = 0;
        if (previous != null) selectEntry(previous);
        MenuFeedback.emit(NAVIGATE);
    }

    private void openSelected() {
        PlaybackEntry selected = selectedEntry();
        if (selected == null) { MenuFeedback.emit(ERROR); return; }
        if (!selected.availability().ready()) { openDetails(selected); return; }
        switch (selected) {
            case PlaybackEntry.Recording recording -> page = new Page.Recording(new UserRecordingMenu(
                    recording.gameId(), recording.recording(), font, (entry, options) -> {
                        if (launcher.launch(new PlaybackRequest.Recording(recording, options))) finish(Result.LAUNCHED);
                        else throw new IllegalStateException(UiMessages.text("menu.playbackbrowser.recording_playback_could_not_be_started"));
                    }));
            case PlaybackEntry.Trace trace -> page = new Page.Trace(new TracePlaybackFeedback(trace.trace(), font));
            case PlaybackEntry.Movie movie -> throw new IllegalStateException(UiMessages.text("menu.playbackbrowser.unconfigured_movie_advertised_launch_readiness"));
        }
        MenuFeedback.emit(CONFIRM);
    }

    private void updateTrace(TracePlaybackFeedback feedback) {
        feedback.update(input, pointer);
        switch (feedback.consumeResult()) {
            case BACK -> page = LIBRARY;
            case LAUNCH -> {
                try {
                    if (launcher.launch(new PlaybackRequest.Trace(new PlaybackEntry.Trace(feedback.selectedEntry())))) {
                        finish(Result.LAUNCHED);
                        return;
                    }
                    if (!hasTraceFailure()) TraceLaunchStatus.record(feedback.selectedEntry(), UiMessages.text("menu.playbackbrowser.trace_playback_could_not_be_started"));
                } catch (Exception failure) {
                    TraceLaunchStatus.record(feedback.selectedEntry(), failure);
                }
                feedback.launchFailed();
            }
            case NONE -> { }
        }
    }

    private void openDetails(PlaybackEntry selected) {
        List<MenuFieldLines.Field> fields = new ArrayList<>();
        if (selected != null) {
            fields.add(new MenuFieldLines.Field("", title(selected)));
            for (PlaybackFacts.Fact fact : selected.details())
                fields.add(new MenuFieldLines.Field(fact.label(), fact.value(), toneColor(fact.tone())));
        }
        if (selected == null) {
            fields.add(new MenuFieldLines.Field("", emptyTitle()));
            fields.add(new MenuFieldLines.Field("", emptyHelp()));
        }
        for (String issue : library.issues()) fields.add(new MenuFieldLines.Field(UiMessages.text("menu.playbackbrowser.source"), issue, MenuChrome.AMBER));
        if (fields.isEmpty()) fields.add(new MenuFieldLines.Field(UiMessages.text("editor.editor_hud.library_ua1q57"), UiMessages.text("menu.playbackbrowser.no_recordings_or_traces_match_these_filters")));
        int labelWidth = MenuFieldLines.labelWidth(fields);
        page = new Page.Details(selected == null ? UiMessages.text("menu.playbackbrowser.library_sources") : title(selected),
                MenuFieldLines.layout(fields, labelWidth, DETAIL_TEXT_W - labelWidth), labelWidth, 0);
        MenuFeedback.emit(CONFIRM);
    }

    static float[] toneColor(PlaybackFacts.Tone tone) {
        return switch (tone) {
            case NORMAL -> MenuChrome.TEXT;
            case READY -> MenuChrome.GREEN;
            case WARNING -> MenuChrome.AMBER;
            case ERROR -> MenuChrome.RED;
        };
    }

    private void updateDetails(Page.Details details) {
        if (MenuInput.back(input) || pointer != null && pointer.backClick()) {
            page = LIBRARY; MenuFeedback.emit(CANCEL); return;
        }
        if (handleFooter()) return;
        int step = pointer == null ? 0 : -pointer.wheel();
        if (MenuInput.up(input)) step--;
        if (MenuInput.down(input)) step++;
        scrollDetails(details, step);
    }

    private void scrollDetails(Page.Details details, int step) {
        int scroll = Math.clamp(details.scroll() + step, 0, Math.max(0, details.lines().size() - detailRows()));
        if (scroll != details.scroll()) {
            page = new Page.Details(details.title(), details.lines(), details.labelWidth(), scroll);
            MenuFeedback.emit(NAVIGATE);
        }
    }

    public void render() {
        if (font == null) return;
        switch (page) {
            case Page.Recording selected -> selected.menu().render();
            case Page.Trace selected -> selected.feedback().render();
            case Page.Library ignored -> renderOwnPage(this::renderLibrary);
            case Page.Details details -> renderOwnPage(() -> renderDetails(details));
            case Page.Closed ignored -> { }
        }
    }

    private void renderOwnPage(Runnable draw) {
        font.beginMegaBatch();
        try { draw.run(); }
        finally { font.endMegaBatch(); }
    }

    private void renderLibrary() {
        MenuChrome.page(font, W, UiMessages.text("menu.playbackbrowser.playback_browser"), traceOnly ? UiMessages.text("menu.playbackbrowser.rom_parity") : library.issues().isEmpty() ? null : UiMessages.text("menu.playbackbrowser.partial"), null);
        filterRow(UiMessages.text("editor.stageplay_menu_presentation.game_1beoi"), gameId.isEmpty() ? UiMessages.text("menu.playbackbrowser.all_games") : gameName(gameId), 35, focus == Focus.GAME, false);
        filterRow(UiMessages.text("menu.playbackbrowser.show"), filterName(), 35 + filterPitch(), focus == Focus.KIND, traceOnly);
        List<PlaybackEntry> entries = visibleEntries();
        if (entries.isEmpty()) {
            renderEmpty();
        } else {
            int first = firstVisible(entries.size());
            for (int i = first, y = listTop(); i < Math.min(entries.size(), first + visibleRows()); i++, y += rowHeight())
                renderRow(entries.get(i), y, i == cursor && focus == Focus.LIST);
            MenuChrome.scrollbar(font, W - 7, listTop(), visibleRows() * rowHeight(), first, visibleRows(), entries.size());
            renderSummary(selectedEntry());
        }
        List<MenuChrome.Hint> hints = new ArrayList<>();
        List<FooterAction> actions = new ArrayList<>();
        hints.add(MenuChrome.upDown(input, UiMessages.text("hub.ui.select_caps"))); actions.add(FooterAction.MOVE);
        if (focus == Focus.LIST) {
            if (!entries.isEmpty()) {
                hints.add(MenuChrome.hint(MenuChrome.confirmKey(input), UiMessages.text("menu.path.open_caps")));
                actions.add(FooterAction.OPEN);
            }
            hints.add(MenuChrome.hint(MenuChrome.detailsKey(input), UiMessages.text("menu.path.details_caps")));
            actions.add(FooterAction.DETAILS);
        } else {
            hints.add(MenuChrome.leftRight(input, UiMessages.text("launch.ui.change_caps"))); actions.add(FooterAction.CHANGE);
        }
        hints.add(MenuChrome.hint(MenuChrome.backKey(input), UiMessages.text("language.picker.back"))); actions.add(FooterAction.BACK);
        footer(hints, actions);
    }

    /** Names what is missing for this exact scope and the one action that adds it. */
    private void renderEmpty() {
        MenuChrome.descriptionBox(font, 9, 76, W - 18, 65, List.of(), List.of());
        String title = emptyTitle();
        MenuChrome.icon(font, MenuChrome.Icon.FILM, 17, 85, MenuChrome.CYAN);
        MenuChrome.compact(font, page(title, W - 48), 30, 85, MenuChrome.WHITE);
        List<String> lines = MenuStyle.wrap(emptyHelp(), W - 34, MenuStyle.COMPACT);
        int pitch = MenuChrome.compactLineHeight();
        int capacity = Math.max(1, (125 - 102) / pitch);
        int first = Math.floorMod(ticks / 120, Math.max(1, (lines.size()+capacity-1)/capacity))*capacity;
        for (int i = 0; i < capacity && first+i < lines.size(); i++)
            MenuChrome.compact(font, lines.get(first+i), 17, 102 + MenuChrome.compactMetrics().above() + i * pitch, MenuChrome.MUTED);
        if (!library.issues().isEmpty()) {
            MenuChrome.icon(font, MenuChrome.Icon.WARNING, 17, 127, MenuChrome.AMBER);
            MenuChrome.compact(font, page(UiMessages.text("menu.playbackbrowser.some_sources_could_not_be_read_see_details"), W - 48),
                    28, 127, MenuChrome.AMBER);
        }
    }

    private String emptyTitle() {
        return switch (filter) {
            case ALL -> library.entries().isEmpty() ? UiMessages.text("menu.playbackbrowser.no_recordings_or_traces_yet") : UiMessages.text("menu.playbackbrowser.nothing_matches_these_filters");
            case RECORDINGS -> UiMessages.text("menu.playback.empty_recordings", java.util.Map.of(
                    "scope", gameId.isEmpty() ? "all" : "game", "game", gameName(gameId)));
            case TRACES -> UiMessages.text("menu.playback.empty_traces", java.util.Map.of(
                    "scope", gameId.isEmpty() ? "all" : "game", "game", gameName(gameId)));
            case MOVIES -> UiMessages.text("menu.playbackbrowser.no_bare_bk2_movies");
        };
    }

    private String emptyHelp() {
        return switch (filter) {
            case RECORDINGS -> UiMessages.text("menu.playbackbrowser.record_while_playing_to_add_one_here_set_the_record_key");
            case TRACES -> UiMessages.text("menu.playbackbrowser.choose_the_trace_replay_folder_in_advanced_settings");
            case MOVIES -> UiMessages.text("menu.playbackbrowser.put_bk2_files_in_the_recordings_folder_or_choose_one_in");
            case ALL -> library.entries().isEmpty()
                    ? UiMessages.text("menu.playbackbrowser.record_while_playing_or_choose_a_trace_replay_folder_in")
                    : UiMessages.text("menu.playbackbrowser.change_the_game_or_show_filter_above");
        };
    }

    private String page(String text, int room) {
        return MenuStyle.pageText(MenuDetailsScreen.readable(text), room, MenuStyle.COMPACT, ticks);
    }

    private void filterRow(String label, String value, int y, boolean selected, boolean locked) {
        if (selected) MenuChrome.focus(font, 10, y - 1, W - 20, filterPitch() - 1, ticks);
        MenuChrome.compact(font, page(label, 56), 16, y + 2 + MenuChrome.compactMetrics().above(), MenuChrome.MUTED);
        MenuChrome.compact(font, page(value, W - 106), 80, y + 2 + MenuChrome.compactMetrics().above(),
                selected ? MenuChrome.WHITE : MenuChrome.TEXT);
        if (locked) MenuChrome.icon(font, MenuChrome.Icon.LOCK, W - 22, y + 2, MenuChrome.DIM);
        else if (selected) {
            MenuChrome.triangle(font, W - 25, y + 4, 3, 'L', MenuChrome.GOLD, 1);
            MenuChrome.triangle(font, W - 17, y + 4, 3, 'R', MenuChrome.GOLD, 1);
        }
    }

    private static final String[] KIND_TAGS = {"REC", "TRACE", "BK2"};

    private void renderRow(PlaybackEntry entry, int y, boolean selected) {
        if (selected) MenuChrome.focus(font, 10, y, W - 22, rowHeight() - 1, ticks);
        int kindW = 0;
        for (String tag : KIND_TAGS) kindW = Math.max(kindW, MenuChrome.tagWidth(tag));
        MenuChrome.tag(font, 14, y + 2, KIND_TAGS[entry.kind().ordinal()], MenuChrome.CYAN);
        int nameX = 14 + kindW + 5;
        if (gameId.isEmpty()) {
            if (!entry.gameId().isEmpty()) MenuChrome.gameBadge(font, nameX, y + 2, entry.gameId());
            nameX += MenuChrome.tagWidth("S3K") + 5;
        }
        int right = W - 16;
        String status = switch (entry.availability().status()) {
            case READY -> null;
            case NEEDS_SETUP -> UiMessages.text("menu.playbackbrowser.setup");
            case INVALID -> "ERROR";
        };
        if (status != null) {
            right -= MenuChrome.tagWidth(status);
            MenuChrome.tag(font, right, y + 2, status,
                    entry.availability().status() == PlaybackEntry.Availability.Status.INVALID ? MenuChrome.RED : MenuChrome.AMBER);
        } else {
            String length = PlaybackFacts.duration(extentCount(entry));
            right -= MenuChrome.compactWidth(length);
            MenuChrome.compact(font, length, right, y + 3 + MenuChrome.compactMetrics().above(), selected ? MenuChrome.MUTED : MenuChrome.DIM);
            if (versionWarning(entry) != null) {
                right -= MenuChrome.ICON_SIZE + 4;
                MenuChrome.icon(font, MenuChrome.Icon.WARNING, right, y + 3 + MenuChrome.compactMetrics().above(), MenuChrome.AMBER);
            }
        }
        MenuChrome.compact(font, page(title(entry), right - 6 - nameX), nameX, y + 3 + MenuChrome.compactMetrics().above(),
                selected ? MenuChrome.WHITE : MenuChrome.TEXT);
    }

    /** Title, the facts that tell similar entries apart, then launch readiness in its status colour. */
    private void renderSummary(PlaybackEntry selected) {
        MenuChrome.descriptionBox(font, 9, 157, W - 18, 43, List.of(), List.of());
        List<String> facts = new ArrayList<>();
        String place = place(selected);
        if (place != null) facts.add(place);
        String team = team(selected);
        if (team != null) facts.add(team);
        facts.add(selected.extent().label());
        String factLine = String.join("   ", facts);
        if (MenuChrome.compactWidth(factLine) > W - 32) factLine = String.join("  ", facts);
        if (MenuChrome.compactWidth(factLine) > W - 32 && team != null) {
            facts.remove(team);
            factLine = String.join("   ", facts);
        }
        String warning = versionWarning(selected);
        var status = selected.availability().status();
        MenuChrome.Icon icon = status == PlaybackEntry.Availability.Status.READY && warning == null
                ? MenuChrome.Icon.CHECK : MenuChrome.Icon.WARNING;
        float[] color = status == PlaybackEntry.Availability.Status.INVALID ? MenuChrome.RED
                : status == PlaybackEntry.Availability.Status.NEEDS_SETUP || warning != null ? MenuChrome.AMBER
                : MenuChrome.GREEN;
        String reason = warning != null && status == PlaybackEntry.Availability.Status.READY
                ? warning : selected.availability().reason();
        List<String> rows = List.of(title(selected), factLine, reason);
        int pitch = Math.max(12, MenuChrome.compactLineHeight());
        int capacity = Math.max(1, 36 / pitch);
        int pages = (rows.size() + capacity - 1) / capacity;
        int first = Math.floorMod(ticks / 120, pages) * capacity;
        int ownTicks = Math.floorDiv(ticks, 120 * pages) * 90;
        for (int i = 0; i < capacity && first + i < rows.size(); i++) {
            int part = first + i;
            int y = 162 + i * pitch + MenuChrome.compactMetrics().above();
            if (part == 2) MenuChrome.icon(font, icon, 15, y - MenuChrome.compactMetrics().above(), color);
            String shown = MenuStyle.pageText(MenuDetailsScreen.readable(rows.get(part)),
                    W - (part == 2 ? 43 : 32), MenuStyle.COMPACT, ownTicks);
            MenuChrome.compact(font, shown, part == 2 ? 26 : 15, y,
                    part == 0 ? MenuChrome.WHITE : part == 1 ? MenuChrome.MUTED
                    : status == PlaybackEntry.Availability.Status.READY && warning == null ? MenuChrome.TEXT : color);
        }
    }

    /** User-facing entry name: the recording title or the trace's catalog identity, not its zone index text. */
    static String title(PlaybackEntry entry) {
        return switch (entry) {
            case PlaybackEntry.Recording recording -> recording.label();
            case PlaybackEntry.Trace trace -> trace.trace().isRun() ? trace.trace().runManifest().runId()
                    : trace.trace().dir().getFileName().toString();
            case PlaybackEntry.Movie movie -> movie.label();
        };
    }

    private static String place(PlaybackEntry entry) {
        return switch (entry) {
            case PlaybackEntry.Recording recording -> {
                var manifest = recording.recording().manifest();
                var launch = manifest == null ? null : manifest.launchContext();
                yield launch == null ? null : PlaybackFacts.zoneAct(launch.zone(), launch.act());
            }
            case PlaybackEntry.Trace trace -> PlaybackFacts.traceStart(trace.trace());
            case PlaybackEntry.Movie ignored -> UiMessages.text("menu.playbackbrowser.start_not_declared");
        };
    }

    private static String team(PlaybackEntry entry) {
        return switch (entry) {
            case PlaybackEntry.Recording recording -> {
                var manifest = recording.recording().manifest();
                var launch = manifest == null ? null : manifest.launchContext();
                yield launch == null ? null : PlaybackFacts.team(launch.mainCharacter(), launch.sidekickCharacters());
            }
            case PlaybackEntry.Trace trace -> trace.trace().team() == null ? null
                    : PlaybackFacts.team(trace.trace().team().mainCharacter(), trace.trace().team().sidekicks());
            case PlaybackEntry.Movie ignored -> null;
        };
    }

    private static int extentCount(PlaybackEntry entry) {
        return switch (entry.extent()) {
            case PlaybackEntry.Extent.MovieRows rows -> rows.count();
            case PlaybackEntry.Extent.TraceFrames frames -> frames.count();
            case PlaybackEntry.Extent.RunTraceFrames frames -> frames.count();
        };
    }

    private static String versionWarning(PlaybackEntry entry) {
        return entry instanceof PlaybackEntry.Recording recording
                ? PlaybackFacts.versionWarning(recording.recording().versionWarning()) : null;
    }

    private void renderDetails(Page.Details details) {
        MenuChrome.page(font, W, UiMessages.text("menu.playbackbrowser.playback_details"), null, page(details.title(), W - 20));
        MenuChrome.descriptionBox(font, 9, 48, W - 18, 150, List.of(), List.of());
        MenuFieldLines.draw(font, details.lines(), details.scroll(), detailRows(), 16, 54 + MenuChrome.compactMetrics().above(), detailPitch(), details.labelWidth());
        MenuChrome.scrollbar(font, W - 15, 52, 142, details.scroll(), detailRows(), details.lines().size());
        List<MenuChrome.Hint> hints = new ArrayList<>();
        List<FooterAction> actions = new ArrayList<>();
        if (details.lines().size() > detailRows()) {
            hints.add(MenuChrome.upDown(input, UiMessages.text("menu.details.scroll"))); actions.add(FooterAction.SCROLL);
        }
        hints.add(MenuChrome.hint(MenuChrome.backKey(input), UiMessages.text("language.picker.back"))); actions.add(FooterAction.BACK);
        footer(hints, actions);
    }

    private List<PlaybackEntry> visibleEntries() {
        return library.entries().stream()
                .filter(entry -> gameId.isEmpty() || gameId.equals(entry.gameId()))
                .filter(entry -> switch (filter) {
                    case ALL -> true;
                    case RECORDINGS -> entry.kind() == PlaybackEntry.Kind.RECORDING;
                    case TRACES -> entry.kind() == PlaybackEntry.Kind.TRACE;
                    case MOVIES -> entry.kind() == PlaybackEntry.Kind.MOVIE;
                }).toList();
    }

    private String filterName() {
        return switch (filter) {
            case ALL -> UiMessages.text("menu.playbackbrowser.all_recordings_and_traces");
            case RECORDINGS -> UiMessages.text("menu.playbackbrowser.engine_recordings");
            case TRACES -> traceOnly ? UiMessages.text("menu.playbackbrowser.rom_traces_only") : UiMessages.text("menu.playbackbrowser.rom_traces");
            case MOVIES -> UiMessages.text("menu.playbackbrowser.bare_bk2_movies");
        };
    }

    private static String gameName(String id) { return PlaybackFacts.gameName(id); }

    private static boolean hasTraceFailure() {
        return TraceLaunchStatus.current().isPresent() || TraceRunFailureStatus.current().isPresent();
    }

    private void finish(Result result) { pendingResult = result; page = new Page.Closed(); }

    public Result consumeResult() {
        Result result = pendingResult;
        pendingResult = Result.NONE;
        return result;
    }

    public PlaybackEntry selectedEntry() {
        List<PlaybackEntry> entries = visibleEntries();
        return cursor < entries.size() ? entries.get(cursor) : null;
    }

    public void selectEntry(PlaybackEntry previous) {
        if (previous == null) return;
        List<PlaybackEntry> entries = visibleEntries();
        for (int i = 0; i < entries.size(); i++) {
            PlaybackEntry entry = entries.get(i);
            if (entry.kind() == previous.kind() && entry.path().toAbsolutePath().normalize()
                    .equals(previous.path().toAbsolutePath().normalize())) {
                cursor = i;
                return;
            }
        }
    }

    public void markFailureFeedbackAnnounced() {
        if (page instanceof Page.Trace selected) selected.feedback().markFailureFeedbackAnnounced();
    }
}
