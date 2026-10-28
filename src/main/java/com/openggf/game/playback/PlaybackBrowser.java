package com.openggf.game.playback;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.recording.RecordingVersionWarning;
import com.openggf.game.recording.menu.UserRecordingMenu;
import com.openggf.graphics.PixelFont;
import com.openggf.testmode.TraceLaunchStatus;
import com.openggf.testmode.TracePlaybackFeedback;
import com.openggf.testmode.TraceRunFailureStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        record Details(String title, List<String> lines, int scroll) implements Page { }
        record Closed() implements Page { }
    }

    private enum Focus { GAME, KIND, LIST }

    private static final int W = 320;
    private static final int LIST_TOP = 69;
    private static final int ROW_H = 14;
    private static final int VISIBLE_ROWS = 6;
    private static final int DETAIL_ROWS = 12;
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

    public void update(InputHandler input) {
        this.input = Objects.requireNonNull(input, "input");
        switch (page) {
            case Page.Library ignored -> updateLibrary();
            case Page.Recording selected -> {
                selected.menu().update(input);
                if (selected.menu().consumeCloseRequested()) page = LIBRARY;
            }
            case Page.Trace selected -> updateTrace(selected.feedback());
            case Page.Details details -> updateDetails(details);
            case Page.Closed ignored -> { }
        }
    }

    private void updateLibrary() {
        if (MenuInput.back(input)) {
            MenuFeedback.emit(CANCEL);
            finish(Result.BACK);
            return;
        }
        if (MenuInput.details(input)) {
            openDetails(selectedEntry());
            return;
        }
        List<PlaybackEntry> entries = visibleEntries();
        int beforeCursor = cursor;
        Focus beforeFocus = focus;
        if (MenuInput.up(input)) {
            if (focus == Focus.LIST && cursor > 0) cursor--;
            else if (focus == Focus.LIST) focus = Focus.KIND;
            else if (focus == Focus.KIND) focus = Focus.GAME;
        }
        if (MenuInput.down(input)) {
            if (focus == Focus.GAME) focus = Focus.KIND;
            else if (focus == Focus.KIND) focus = Focus.LIST;
            else cursor = Math.min(Math.max(0, entries.size() - 1), cursor + 1);
        }
        if (focus == Focus.LIST) {
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_HOME)) cursor = 0;
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_END)) cursor = Math.max(0, entries.size() - 1);
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_DOWN))
                cursor = Math.min(Math.max(0, entries.size() - 1), cursor + VISIBLE_ROWS);
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_UP)) cursor = Math.max(0, cursor - VISIBLE_ROWS);
        }
        if (beforeCursor != cursor || beforeFocus != focus) MenuFeedback.emit(NAVIGATE);
        if (focus != Focus.LIST && (MenuInput.left(input) || MenuInput.right(input) || MenuInput.accept(input))) {
            changeFilter(MenuInput.left(input) ? -1 : 1);
        } else if (focus == Focus.LIST && MenuInput.accept(input)) openSelected();
    }

    private void changeFilter(int direction) {
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
                        else throw new IllegalStateException("Recording playback could not be started");
                    }));
            case PlaybackEntry.Trace trace -> page = new Page.Trace(new TracePlaybackFeedback(trace.trace(), font));
            case PlaybackEntry.Movie movie -> throw new IllegalStateException("Unconfigured movie advertised launch readiness");
        }
        MenuFeedback.emit(CONFIRM);
    }

    private void updateTrace(TracePlaybackFeedback feedback) {
        feedback.update(input);
        switch (feedback.consumeResult()) {
            case BACK -> page = LIBRARY;
            case LAUNCH -> {
                try {
                    if (launcher.launch(new PlaybackRequest.Trace(new PlaybackEntry.Trace(feedback.selectedEntry())))) {
                        finish(Result.LAUNCHED);
                        return;
                    }
                    if (!hasTraceFailure()) TraceLaunchStatus.record(feedback.selectedEntry(), "Trace playback could not be started");
                } catch (Exception failure) {
                    TraceLaunchStatus.record(feedback.selectedEntry(), failure);
                }
                feedback.launchFailed();
            }
            case NONE -> { }
        }
    }

    private void openDetails(PlaybackEntry selected) {
        List<String> messages = new ArrayList<>();
        if (selected != null) {
            messages.add(selected.label());
            messages.addAll(selected.details());
        }
        if (!library.issues().isEmpty()) {
            messages.add("Catalog issues");
            messages.addAll(library.issues());
        }
        if (messages.isEmpty()) messages.add("No recordings or traces match these filters");
        List<String> lines = new ArrayList<>();
        for (String message : messages) {
            for (int start = 0; start < message.length(); start += 48)
                lines.add(message.substring(start, Math.min(message.length(), start + 48)));
        }
        page = new Page.Details("PLAYBACK DETAILS", List.copyOf(lines), 0);
        MenuFeedback.emit(CONFIRM);
    }

    private void updateDetails(Page.Details details) {
        if (MenuInput.back(input)) { page = LIBRARY; MenuFeedback.emit(CANCEL); return; }
        int scroll = details.scroll();
        if (MenuInput.up(input)) scroll = Math.max(0, scroll - 1);
        if (MenuInput.down(input)) scroll = Math.min(Math.max(0, details.lines().size() - DETAIL_ROWS), scroll + 1);
        if (scroll != details.scroll()) {
            page = new Page.Details(details.title(), details.lines(), scroll);
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
        ticks++;
        font.beginMegaBatch();
        try { draw.run(); }
        finally { font.endMegaBatch(); }
    }

    private void renderLibrary() {
        MenuChrome.page(font, W, "PLAYBACK BROWSER", traceOnly ? "ROM PARITY" : library.issues().isEmpty() ? null : "PARTIAL", null);
        filterRow("Game", gameId.isEmpty() ? "All games" : gameName(gameId), 35, focus == Focus.GAME);
        filterRow("Show", filterName(), 49, focus == Focus.KIND);
        List<PlaybackEntry> entries = visibleEntries();
        if (entries.isEmpty()) {
            MenuChrome.descriptionBox(font, 9, 76, W - 18, 65, List.of(), List.of());
            MenuChrome.icon(font, MenuChrome.Icon.FILM, 17, 85, MenuChrome.AMBER);
            MenuChrome.compact(font, "No matching recordings or traces", 30, 85, MenuChrome.AMBER);
            MenuChrome.compact(font, "Change the Game or Show filters above.", 17, 106, MenuChrome.MUTED);
            if (!library.issues().isEmpty())
                MenuChrome.compact(font, "Some sources could not be read. See Details.", 17, 121, MenuChrome.AMBER);
        } else {
            int first = Math.max(0, Math.min(cursor - 2, entries.size() - VISIBLE_ROWS));
            for (int i = first, y = LIST_TOP; i < Math.min(entries.size(), first + VISIBLE_ROWS); i++, y += ROW_H)
                renderRow(entries.get(i), y, i == cursor && focus == Focus.LIST);
            MenuChrome.scrollbar(font, W - 7, LIST_TOP, VISIBLE_ROWS * ROW_H, first, VISIBLE_ROWS, entries.size());
            renderSummary(selectedEntry());
        }
        List<MenuChrome.Hint> hints = new ArrayList<>();
        hints.add(MenuChrome.upDown(input, focus == Focus.LIST ? "SELECT" : "ROW"));
        if (focus == Focus.LIST) {
            hints.add(MenuChrome.hint(MenuChrome.confirmKey(input), "OPEN"));
            hints.add(MenuChrome.hint(MenuChrome.detailsKey(input), "DETAILS"));
        } else hints.add(MenuChrome.leftRight(input, "CHANGE"));
        hints.add(MenuChrome.hint(MenuChrome.backKey(input), "BACK"));
        MenuChrome.footer(font, W, null, hints, MenuChrome.controller(input));
    }

    private void filterRow(String label, String value, int y, boolean selected) {
        if (selected) MenuChrome.focus(font, 10, y - 1, W - 20, 13, ticks);
        MenuChrome.compact(font, label, 16, y + 2, MenuChrome.MUTED);
        MenuChrome.compact(font, MenuChrome.fitCompact(value, W - 106), 80, y + 2,
                selected ? MenuChrome.WHITE : MenuChrome.TEXT);
        if (selected && !(traceOnly && focus == Focus.KIND)) {
            MenuChrome.triangle(font, W - 25, y + 4, 3, 'L', MenuChrome.GOLD, 1);
            MenuChrome.triangle(font, W - 17, y + 4, 3, 'R', MenuChrome.GOLD, 1);
        }
    }

    private void renderRow(PlaybackEntry entry, int y, boolean selected) {
        if (selected) MenuChrome.focus(font, 10, y, W - 22, ROW_H - 1, ticks);
        String tag = switch (entry.kind()) { case RECORDING -> "REC"; case TRACE -> "TRACE"; case MOVIE -> "BK2"; };
        int nameX = 15 + MenuChrome.tag(font, 14, y + 2, tag, MenuChrome.CYAN) + 6;
        String status = switch (entry.availability().status()) {
            case READY -> entry instanceof PlaybackEntry.Recording recording
                    && recording.recording().versionWarning() != RecordingVersionWarning.NONE ? "WARN" : null;
            case NEEDS_SETUP -> "SETUP";
            case INVALID -> "ERR";
        };
        int right = W - 16;
        if (status != null) {
            right -= MenuChrome.tagWidth(status);
            MenuChrome.tag(font, right, y + 2, status, MenuChrome.AMBER);
            right -= 6;
        }
        String name = gameId.isEmpty() && !entry.gameId().isEmpty()
                ? entry.gameId().toUpperCase(Locale.ROOT) + "  " + entry.label() : entry.label();
        MenuChrome.compact(font, MenuChrome.fitCompact(name, right - nameX), nameX, y + 3,
                selected ? MenuChrome.WHITE : MenuChrome.TEXT);
    }

    private void renderSummary(PlaybackEntry selected) {
        MenuChrome.descriptionBox(font, 9, 157, W - 18, 43, List.of(), List.of());
        MenuChrome.compact(font, MenuChrome.fitCompact(selected.label(), W - 32), 15, 163, MenuChrome.WHITE);
        MenuChrome.compact(font, MenuChrome.fitCompact(selected.extent().label(), W - 32), 15, 175, MenuChrome.MUTED);
        MenuChrome.compact(font, MenuChrome.fitCompact(selected.availability().reason(), W - 32), 15, 187,
                selected.availability().ready() ? MenuChrome.DIM : MenuChrome.AMBER);
    }

    private void renderDetails(Page.Details details) {
        MenuChrome.page(font, W, details.title(), null, "Source, launch requirements and diagnostics");
        MenuChrome.descriptionBox(font, 9, 48, W - 18, 150, List.of(), List.of());
        for (int i = details.scroll(), y = 53; i < Math.min(details.lines().size(), details.scroll() + DETAIL_ROWS); i++, y += 12)
            MenuChrome.compact(font, details.lines().get(i), 15, y, MenuChrome.TEXT);
        MenuChrome.scrollbar(font, W - 15, 52, 142, details.scroll(), DETAIL_ROWS, details.lines().size());
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (details.lines().size() > DETAIL_ROWS) hints.add(MenuChrome.upDown(input, "SCROLL"));
        hints.add(MenuChrome.hint(MenuChrome.backKey(input), "BACK"));
        MenuChrome.footer(font, W, null, hints, MenuChrome.controller(input));
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
            case ALL -> "All recordings and traces";
            case RECORDINGS -> "Engine recordings";
            case TRACES -> traceOnly ? "ROM traces only" : "ROM traces";
            case MOVIES -> "Bare BK2 movies";
        };
    }

    private static String gameName(String id) {
        return switch (id) { case "s1" -> "Sonic 1"; case "s2" -> "Sonic 2"; case "s3k" -> "Sonic 3 & Knuckles"; default -> id; };
    }

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
