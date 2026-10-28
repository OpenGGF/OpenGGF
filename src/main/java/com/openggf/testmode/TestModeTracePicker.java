package com.openggf.testmode;

import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuStyle;
import java.util.ArrayList;
import com.openggf.trace.catalog.TraceEntry;

import java.util.List;
import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

/**
 * Trace picker opened through Tools or the legacy TEST_MODE_ENABLED flag. Owned by
 * MasterTitleScreen; it substitutes this screen's update/render for the
 * normal game-selection ACTIVE behaviour.
 */
public final class TestModeTracePicker {

    public enum Result { NONE, LAUNCH, BACK }

    private final List<TraceEntry> entries;
    private final PixelFont font;
    private InputHandler menuInput;
    private int cursor;
    private int firstVisible;
    private int failurePage;
    private Result pendingResult = Result.NONE;
    private TraceEntry loadingEntry;
    private boolean loadingPresented;
    private boolean launchIssued;
    private boolean failureAnnounced;

    public TestModeTracePicker(List<TraceEntry> entries, PixelFont font) {
        this.entries = entries;
        this.font = font;
    }

    /** A host ROM error page has already announced this pending launch failure. */
    public void markFailureFeedbackAnnounced() {
        failureAnnounced = true;
    }

    public void update(InputHandler input) {
        menuInput = input;
        if (hasHeldFailure()) {
            if (!failureAnnounced) { MenuFeedback.emit(ERROR); failureAnnounced = true; }
            updateHeldFailure(input);
            return;
        }
        failureAnnounced = false;
        if ((!input.isAnyModifierDown() && MenuInput.back(input))) {
            MenuFeedback.emit(CANCEL);
            loadingEntry = null;
            pendingResult = Result.BACK;
            return;
        }
        if (loadingEntry != null) {
            if (loadingPresented && !launchIssued) {
                launchIssued = true;
                pendingResult = Result.LAUNCH;
            }
            return;
        }
        if (entries.isEmpty()) {
            if (MenuInput.accept(input)) MenuFeedback.emit(ERROR);
            if ((!input.isAnyModifierDown() && MenuInput.back(input))) {
                pendingResult = Result.BACK;
            }
            return;
        }
        int previousCursor = cursor;
        if ((!input.isAnyModifierDown() && MenuInput.down(input))) {
            cursor = Math.min(entries.size() - 1, cursor + 1);
        }
        if ((!input.isAnyModifierDown() && MenuInput.up(input))) {
            cursor = Math.max(0, cursor - 1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_HOME)) {
            cursor = 0;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_END)) {
            cursor = entries.size() - 1;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_DOWN) || MenuInput.right(input)) {
            cursor = nextGroupStart(cursor);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_UP) || MenuInput.left(input)) {
            cursor = prevGroupStart(cursor);
        }
        if (cursor != previousCursor) MenuFeedback.emit(NAVIGATE);
        // Keep the scroll window in sync with the cursor after any movement
        // so the selected entry is always within the visible viewport.
        firstVisible = computeFirstVisible(firstVisible, cursor);
        if ((!input.isAnyModifierDown() && MenuInput.accept(input))) {
            MenuFeedback.emit(CONFIRM);
            loadingEntry = entries.get(cursor);
            loadingPresented = false;
            launchIssued = false;
        }
        if ((!input.isAnyModifierDown() && MenuInput.back(input))) {
            pendingResult = Result.BACK;
        }
    }

    private void updateHeldFailure(InputHandler input) {
        int previousPage = failurePage;
        if (MenuInput.left(input)) failurePage = Math.max(0, failurePage - 1);
        if (MenuInput.right(input)) failurePage = Math.min(failurePages() - 1, failurePage + 1);
        if (previousPage != failurePage) MenuFeedback.emit(NAVIGATE);
        if ((!input.isAnyModifierDown() && MenuInput.accept(input))
                || (!input.isAnyModifierDown() && MenuInput.back(input))) {
            MenuFeedback.emit(MenuInput.back(input) ? CANCEL : CONFIRM);
            clearHeldFailure();
            return;
        }
        if (entries.isEmpty()) {
            return;
        }
        int previousCursor = cursor;
        if ((!input.isAnyModifierDown() && MenuInput.down(input))) {
            cursor = Math.min(entries.size() - 1, cursor + 1);
        }
        if ((!input.isAnyModifierDown() && MenuInput.up(input))) {
            cursor = Math.max(0, cursor - 1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_HOME)) {
            cursor = 0;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_END)) {
            cursor = entries.size() - 1;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_DOWN)) {
            cursor = nextGroupStart(cursor);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_UP)) {
            cursor = prevGroupStart(cursor);
        }
        firstVisible = computeFirstVisible(firstVisible, cursor);
        if (cursor != previousCursor) {
            MenuFeedback.emit(NAVIGATE);
            clearHeldFailure();
        }
    }

    private int failurePages() {
        List<String> messages = new ArrayList<>();
        TraceRunFailureStatus.current().ifPresent(f -> {
            messages.add("Segment: " + f.segmentIndex());
            if (f.isComparison()) { messages.add("Expected: " + f.expectedIdentity()); messages.add("Actual: " + f.actualIdentity()); }
            else messages.add("Reason: " + f.reason());
            messages.add("Cursor: " + f.cursor() + "  Steps: " + f.stepCount());
        });
        if (messages.isEmpty()) TraceLaunchStatus.current().ifPresent(f -> {
            messages.add("Trace: " + f.traceLabel()); messages.add("Reason: " + f.reason());
        });
        int lines = messages.stream().mapToInt(message -> MenuStyle.chunks(message, FAILURE_WIDTH, MenuStyle.COMPACT).size()).sum();
        return Math.max(1, (lines + 10) / 11);
    }

    private String confirmHint() { return menuInput == null ? "Enter" : MenuInput.confirmLabel(menuInput); }
    private String backHint() { return menuInput == null ? "Esc" : MenuInput.backLabel(menuInput); }

    private static final int W = 320;
    private int ticks;
    private static final int LINE_HEIGHT = 12;
    private static final int GROUP_GAP = 2;
    private static final int HEADING_HEIGHT = 12;
    private static final int LIST_TOP = 47;
    private static final int LIST_AREA_BOTTOM = 134;
    /** Failure lines keep every character, split at the measured width of the report box. */
    private static final int FAILURE_WIDTH = W - 20;

    public void render() {
        if (font == null) return;
        ticks++;
        font.beginMegaBatch();
        try {
            MenuChrome.page(font, W, "TRACE REPLAYS", "ROM PARITY", "Replay recorded traces against the engine");
            Optional<TraceRunFailureStatus.Failure> failure = TraceRunFailureStatus.current();
            if (failure.isPresent()) {
                var f = failure.get();
                List<String> lines = new ArrayList<>();
                lines.add("Segment: " + f.segmentIndex());
                if (f.isComparison()) {
                    lines.add("Expected: " + f.expectedIdentity());
                    lines.add("Actual: " + f.actualIdentity());
                } else lines.add("Reason: " + f.reason());
                lines.add("Cursor: " + f.cursor() + "  Steps: " + f.stepCount());
                renderFailurePage("TRACE FAILED", lines);
                return;
            }
            Optional<TraceLaunchStatus.Failure> launchFailure = TraceLaunchStatus.current();
            if (launchFailure.isPresent()) {
                var f = launchFailure.get();
                renderFailurePage("TRACE LAUNCH FAILED", List.of("Trace: " + f.traceLabel(), "Reason: " + f.reason()));
                return;
            }
            failurePage = 0;
            if (loadingEntry != null) {
                MenuChrome.descriptionBox(font, 9, 66, W - 18, 60, List.of(), List.of());
                MenuChrome.label(font, "LOADING TRACE...", 17, 74, MenuChrome.GOLD);
                MenuChrome.compact(font, MenuChrome.fitCompact(TraceLaunchStatus.catalogLabel(loadingEntry), W - 34),
                        17, 92, MenuChrome.WHITE);
                MenuChrome.compact(font, "Preparing the recorded game and team", 17, 106, MenuChrome.MUTED);
                MenuChrome.footer(font, W, null, List.of(MenuChrome.hint(backHint(), "CANCEL")), controller());
                loadingPresented = true;
                return;
            }
            if (entries.isEmpty()) {
                MenuChrome.descriptionBox(font, 9, 60, W - 18, 64, List.of(), List.of());
                MenuChrome.icon(font, MenuChrome.Icon.FOLDER, 17, 70, MenuChrome.AMBER);
                MenuChrome.label(font, "No traces found", 29, 69, MenuChrome.AMBER);
                MenuChrome.compact(font, "Set the trace folder in Settings:", 17, 92, MenuChrome.TEXT);
                MenuChrome.compact(font, "Files > Trace catalog folder", 17, 104, MenuChrome.CYAN);
                MenuChrome.footer(font, W, null, List.of(MenuChrome.hint(backHint(), "BACK")), controller());
                return;
            }
            firstVisible = computeFirstVisible(firstVisible, cursor);
            int lastVisible = lastFullyVisibleIndex(firstVisible);
            heading(entries.get(firstVisible).gameId(), LIST_TOP);
            String position = (cursor + 1) + " / " + entries.size();
            MenuChrome.compact(font, position, W - 12 - MenuChrome.compactWidth(position), LIST_TOP + 2, MenuChrome.DIM);
            int y = LIST_TOP + HEADING_HEIGHT;
            for (int i = firstVisible; i <= lastVisible; i++) {
                TraceEntry e = entries.get(i);
                if (i > firstVisible && !e.gameId().equals(entries.get(i - 1).gameId())) {
                    y += GROUP_GAP;
                    heading(e.gameId(), y);
                    y += HEADING_HEIGHT;
                }
                row(e, y, i == cursor);
                y += LINE_HEIGHT;
            }
            MenuChrome.scrollbar(font, W - 7, LIST_TOP + HEADING_HEIGHT, LIST_AREA_BOTTOM - LIST_TOP - HEADING_HEIGHT,
                    firstVisible, lastVisible - firstVisible + 1, entries.size());
            renderInfoPanel(entries.get(cursor));
            MenuChrome.footer(font, W, null, List.of(
                    MenuChrome.upDown(menuInput, "SELECT"), MenuChrome.leftRight(menuInput, "GAME"),
                    MenuChrome.hint(confirmHint(), "REPLAY"), MenuChrome.hint(backHint(), "BACK")), controller());
        } finally {
            font.endMegaBatch();
        }
    }

    private void heading(String gameId, int y) {
        MenuChrome.section(font, gameHeading(gameId), 12, y + 2, W - 70);
    }

    /** One trace: its catalog name, a kind tag for runs and special stages, and its length. */
    private void row(TraceEntry e, int y, boolean focused) {
        if (focused) MenuChrome.focus(font, 10, y, W - 22, LINE_HEIGHT - 1, ticks);
        String length = duration(e.frameCount());
        int lengthX = W - 16 - MenuChrome.compactWidth(length);
        MenuChrome.compact(font, length, lengthX, y + 2, focused ? MenuChrome.MUTED : MenuChrome.DIM);
        int x = 16;
        String kind = e.isRun() ? e.runManifest().segments().size() + " SEGMENTS"
                : e.displayLabel().contains("SPECIAL STAGE") ? "SPECIAL STAGE" : null;
        int nameRoom = lengthX - 8 - x - (kind == null ? 0 : MenuChrome.tagWidth(kind) + 6);
        String name = MenuChrome.fitCompact(identity(e), nameRoom);
        MenuChrome.compact(font, name, x, y + 2, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
        if (kind != null) MenuChrome.tag(font, x + MenuChrome.compactWidth(name) + 6, y + 1, kind, MenuChrome.CYAN);
    }

    private static String identity(TraceEntry e) {
        return e.isRun() ? e.runManifest().runId() : e.dir().getFileName().toString();
    }

    /** Frames at 60 Hz as minutes:seconds. */
    static String duration(int frames) {
        int seconds = Math.max(0, frames) / 60;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private boolean controller() { return MenuChrome.controller(menuInput); }

    private void renderFailurePage(String title, List<String> messages) {
        List<String> lines = new ArrayList<>();
        for (String message : messages) lines.addAll(MenuStyle.chunks(message, FAILURE_WIDTH, MenuStyle.COMPACT));
        int pages = Math.max(1, (lines.size() + 10) / 11);
        failurePage = Math.min(failurePage, pages - 1);
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, 9, 50, MenuChrome.RED);
        MenuChrome.compact(font, title, 20, 50, MenuChrome.RED);
        MenuChrome.descriptionBox(font, 9, 62, W - 18, 136, List.of(), List.of());
        for (int i = failurePage * 11, y = 67; i < Math.min(lines.size(), (failurePage + 1) * 11); i++, y += 11)
            line(lines.get(i), 12, y, W - 20, 1, 1, 1);
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (pages > 1) hints.add(MenuChrome.leftRight(menuInput, "PAGE " + (failurePage + 1) + "/" + pages));
        hints.add(MenuChrome.hint(confirmHint(), "ACKNOWLEDGE"));
        MenuChrome.footer(font, W, null, hints, controller());
    }

    private void line(String text, int x, int y, int width, float r, float g, float b) {
        MenuStyle.text(font, text, x, y, width, r, g, b);
    }

    private static boolean hasHeldFailure() {
        return TraceRunFailureStatus.current().isPresent()
                || TraceLaunchStatus.current().isPresent();
    }

    private static void clearHeldFailure() {
        TraceRunFailureStatus.clear();
        TraceLaunchStatus.clear();
    }

    /**
     * Index of the last entry that fully fits in the scroll viewport when the
     * window begins at {@code firstVisible}. Accounts for the sticky heading and
     * any inline group headings that appear within the window. Always returns at
     * least {@code firstVisible} so the selected row can render even on a screen
     * too short to fit it. Package-private for unit testing.
     */
    int lastFullyVisibleIndex(int firstVisible) {
        if (entries.isEmpty()) {
            return 0;
        }
        firstVisible = Math.max(0, Math.min(firstVisible, entries.size() - 1));
        int y = LIST_TOP + HEADING_HEIGHT; // below the sticky heading
        int last = firstVisible;
        for (int i = firstVisible; i < entries.size(); i++) {
            int rowHeight = LINE_HEIGHT;
            if (i > firstVisible
                    && !entries.get(i).gameId().equals(entries.get(i - 1).gameId())) {
                rowHeight += GROUP_GAP + HEADING_HEIGHT;
            }
            if (y + rowHeight > LIST_AREA_BOTTOM) {
                break;
            }
            y += rowHeight;
            last = i;
        }
        return last;
    }

    /**
     * Minimal scroll-window start that keeps {@code cursor} visible. Scrolls up
     * when the cursor is above the window and down (one entry at a time) until
     * the cursor fits, never moving past the cursor. Package-private for testing.
     */
    int computeFirstVisible(int firstVisible, int cursor) {
        if (entries.isEmpty()) {
            return 0;
        }
        firstVisible = Math.max(0, Math.min(firstVisible, entries.size() - 1));
        cursor = Math.max(0, Math.min(cursor, entries.size() - 1));
        if (firstVisible > cursor) {
            firstVisible = cursor;
        }
        while (firstVisible < cursor && cursor > lastFullyVisibleIndex(firstVisible)) {
            firstVisible++;
        }
        return firstVisible;
    }

    private void renderInfoPanel(TraceEntry e) {
        MenuChrome.descriptionBox(font, 9, 143, W - 18, 54, List.of(), List.of());
        MenuChrome.compact(font, MenuChrome.fitCompact(TraceLaunchStatus.catalogLabel(e), W - 32), 15, 148, MenuChrome.WHITE);
        line(String.format("%,d frames (%s)   Team: %s", e.frameCount(), duration(e.frameCount()), formatTeam(e)),
                15, 161, W - 30, .72f, .77f, .9f);
        line("Movie: " + e.bk2Path().getFileName(), 15, 172, W - 30, .53f, .58f, .74f);
        line("Starts at movie frame " + e.bk2StartOffset() + "   Pre-osc: " + e.preTraceOscFrames(),
                15, 183, W - 30, .53f, .58f, .74f);
    }

    private static String gameHeading(String gameId) {
        return switch (gameId) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3 & KNUCKLES";
            default -> gameId.toUpperCase();
        };
    }

    private static String formatTeam(TraceEntry e) {
        StringBuilder sb = new StringBuilder(capitalised(e.team().mainCharacter()));
        for (String sk : e.team().sidekicks()) {
            sb.append(" & ").append(capitalised(sk));
        }
        return sb.toString();
    }

    private static String capitalised(String name) {
        return name == null || name.isEmpty() ? "" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // Package-private for targeted unit testing.
    int nextGroupStart(int from) {
        String current = entries.get(from).gameId();
        for (int i = from + 1; i < entries.size(); i++) {
            if (!entries.get(i).gameId().equals(current)) {
                return i;
            }
        }
        return from;
    }

    // Package-private for targeted unit testing.
    int prevGroupStart(int from) {
        String current = entries.get(from).gameId();
        // Walk backwards to find the group that precedes the current one,
        // then walk to that group's FIRST entry.
        int prevGroupLastIdx = -1;
        for (int i = from - 1; i >= 0; i--) {
            if (!entries.get(i).gameId().equals(current)) {
                prevGroupLastIdx = i;
                break;
            }
        }
        if (prevGroupLastIdx < 0) {
            return 0;
        }
        String prevGame = entries.get(prevGroupLastIdx).gameId();
        int start = prevGroupLastIdx;
        while (start > 0 && entries.get(start - 1).gameId().equals(prevGame)) {
            start--;
        }
        return start;
    }

    public Result consumeResult() {
        Result r = pendingResult;
        pendingResult = Result.NONE;
        return r;
    }

    // Package-private for targeted unit testing.
    int cursor() {
        return cursor;
    }

    public TraceEntry selectedEntry() {
        return loadingEntry != null
                ? loadingEntry
                : cursor < entries.size() ? entries.get(cursor) : null;
    }

    /** Returns the picker to its selected entry after a synchronous launch failure. */
    public void launchFailed() {
        loadingEntry = null;
        loadingPresented = false;
        launchIssued = false;
    }
}
