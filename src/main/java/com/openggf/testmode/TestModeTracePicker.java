package com.openggf.testmode;

import com.openggf.i18n.UiMessages;
import com.openggf.game.MenuFeedback;
import static com.openggf.game.MenuFeedback.Cue.*;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
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

    /** Measured held-diagnostic page count; package-private for complete presentation checks. */
    int failurePages() {
        List<String> messages = new ArrayList<>();
        TraceRunFailureStatus.current().ifPresent(f -> {
            messages.add(UiMessages.text("menu.traces.segment", java.util.Map.of("segment", canonicalDecimal(f.segmentIndex()))));
            if (f.isComparison()) { messages.add(UiMessages.text("menu.traces.expected", java.util.Map.of("identity", String.valueOf(f.expectedIdentity())))); messages.add(UiMessages.text("menu.traces.actual", java.util.Map.of("identity", String.valueOf(f.actualIdentity())))); }
            else messages.add(UiMessages.text("menu.traces.reason", java.util.Map.of("reason", f.reason())));
            messages.add(UiMessages.text("menu.traces.cursor", java.util.Map.of("cursor", canonicalDecimal(f.cursor()), "steps", canonicalDecimal(f.stepCount()))));
        });
        if (messages.isEmpty()) TraceLaunchStatus.current().ifPresent(f -> {
            messages.add(UiMessages.text("menu.traces.trace", java.util.Map.of("trace", f.traceLabel()))); messages.add(UiMessages.text("menu.traces.reason", java.util.Map.of("reason", f.reason())));
        });
        int lines = failureLines(messages).size();
        return Math.max(1, (lines + failureCapacity() - 1) / failureCapacity());
    }

    private String confirmHint() { return menuInput == null ? "Enter" : MenuInput.confirmLabel(menuInput); }
    private String backHint() { return menuInput == null ? "Esc" : MenuInput.backLabel(menuInput); }

    private static final float SCALE = MenuStyle.COMPACT;
    private static final int LINE_HEIGHT = 12;
    private static final int GROUP_GAP = 2;
    private static final int HEADING_HEIGHT = 12;
    private static final int LIST_TOP = 47;
    private static final int LIST_AREA_BOTTOM = 134;
    /** Trace coordinates and counters are machine identifiers, independent of UI number formatting. */
    private static String canonicalDecimal(long value) { return Long.toString(value); }

    public void render() {
        if (font == null) return;
        font.beginMegaBatch();
        try {
            MenuStyle.page(font, 320, UiMessages.text("menu.traces.ui.trace_replays_caps"), UiMessages.text("menu.traces.ui.rom_parity_playback_native_320x224"));
            Optional<TraceRunFailureStatus.Failure> failure = TraceRunFailureStatus.current();
            if (failure.isPresent()) {
                var f = failure.get();
                List<String> lines = new ArrayList<>();
                lines.add(UiMessages.text("menu.traces.segment", java.util.Map.of("segment", canonicalDecimal(f.segmentIndex()))));
                if (f.isComparison()) {
                    lines.add(UiMessages.text("menu.traces.expected", java.util.Map.of("identity", String.valueOf(f.expectedIdentity()))));
                    lines.add(UiMessages.text("menu.traces.actual", java.util.Map.of("identity", String.valueOf(f.actualIdentity()))));
                } else lines.add(UiMessages.text("menu.traces.reason", java.util.Map.of("reason", f.reason())));
                lines.add(UiMessages.text("menu.traces.cursor", java.util.Map.of("cursor", canonicalDecimal(f.cursor()), "steps", canonicalDecimal(f.stepCount()))));
                renderFailurePage(UiMessages.text("menu.traces.ui.trace_failed_caps"), lines);
                return;
            }
            Optional<TraceLaunchStatus.Failure> launchFailure = TraceLaunchStatus.current();
            if (launchFailure.isPresent()) {
                var f = launchFailure.get();
                renderFailurePage(UiMessages.text("menu.traces.ui.trace_launch_failed_caps"), List.of(UiMessages.text("menu.traces.trace", java.util.Map.of("trace", f.traceLabel())), UiMessages.text("menu.traces.reason", java.util.Map.of("reason", f.reason()))));
                return;
            }
            failurePage = 0;
            if (loadingEntry != null) {
                MenuStyle.panel(font, 9, 66, 302, 71);
                line(UiMessages.text("menu.traces.ui.loading_trace_caps"), 17, 76, 286, 1, .85f, .3f);
                line(TraceLaunchStatus.catalogLabel(loadingEntry), 17, 94, 286, 1, 1, 1);
                line(UiMessages.text("menu.traces.ui.parsing_replay_data"), 17, 116, 286, .75f, .82f, 1);
                MenuStyle.footer(font, 320, UiMessages.text("menu.traces.ui.preparing_the_recorded_game_and_team"), UiMessages.text("menu.traces.cancel", java.util.Map.of("back", backHint())));
                loadingPresented = true;
                return;
            }
            if (entries.isEmpty()) {
                MenuStyle.panel(font, 9, 55, 302, 108);
                line(UiMessages.text("menu.traces.ui.no_traces_found"), 17, 67, 286, 1, .75f, .3f);
                line(UiMessages.text("menu.traces.ui.set_the_trace_folder_in_engine_settings"), 17, 92, 286, 1, 1, 1);
                line(UiMessages.text("menu.traces.ui.files_trace_catalog_folder"), 17, 108, 286, .7f, .8f, 1);
                MenuStyle.footer(font, 320, UiMessages.text("menu.traces.ui.return_to_the_hub_to_open_settings"), UiMessages.text("menu.traces.back", java.util.Map.of("back", backHint())));
                return;
            }
            firstVisible = computeFirstVisible(firstVisible, cursor);
            int lastVisible = lastFullyVisibleIndex(firstVisible);
            line(UiMessages.text("menu.traces.position", java.util.Map.of("game", gameHeading(entries.get(firstVisible).gameId()), "position", cursor + 1, "count", entries.size())),
                    9, LIST_TOP, 302, 1, .82f, .3f);
            int y = LIST_TOP + headingPitch();
            for (int i = firstVisible; i <= lastVisible; i++) {
                TraceEntry e = entries.get(i);
                if (i > firstVisible && !e.gameId().equals(entries.get(i - 1).gameId())) {
                    y += GROUP_GAP;
                    line(gameHeading(e.gameId()), 9, y, 302, 1, .82f, .3f);
                    y += headingPitch();
                }
                if (i == cursor) MenuStyle.focusLabel(font, 9, y, 302, rowPitch());
                MenuStyle.label(font, (i == cursor ? "> " : "  ") + e.displayLabel(), 13, y, 292,
                        i == cursor ? 1 : .72f, i == cursor ? 1 : .78f, 1);
                y += rowPitch();
            }
            renderInfoPanel(entries.get(cursor));
            MenuStyle.footer(font, 320, UiMessages.text("menu.traces.ui.up_down_select_left_right_game"),
                    UiMessages.text("menu.traces.replay_controls", java.util.Map.of("confirm", confirmHint(), "back", backHint())));
        } finally {
            font.endMegaBatch();
        }
    }

    private static int compactPitch() { return Math.max(11, com.openggf.game.MenuChrome.compactLineHeight() + 2); }
    private static int failureCapacity() { return Math.max(1, 125 / compactPitch()); }
    private static List<String> failureLines(List<String> messages) {
        List<String> lines = new ArrayList<>();
        for (String message : messages) lines.addAll(com.openggf.graphics.text.HostText.shared().wrap(message, 300,
                com.openggf.graphics.text.TextFace.COMPACT, com.openggf.graphics.text.HostTextContext.current()));
        return lines;
    }
    private static int rowPitch() { return Math.max(LINE_HEIGHT, com.openggf.game.MenuChrome.labelLineHeight() + 2); }
    private static int headingPitch() { return Math.max(HEADING_HEIGHT, compactPitch()); }

    private void renderFailurePage(String title, List<String> messages) {
        List<String> lines = failureLines(messages);
        int capacity = failureCapacity();
        int pages = Math.max(1, (lines.size() + capacity - 1) / capacity);
        failurePage = Math.min(failurePage, pages - 1);
        line(title, 9, 49, 302, 1, .3f, .3f);
        MenuStyle.panel(font, 9, 62, 302, 133);
        for (int i = failurePage * capacity, y = 67; i < Math.min(lines.size(), (failurePage + 1) * capacity); i++, y += compactPitch())
            line(lines.get(i), 12, y, 300, 1, 1, 1);
        MenuStyle.footer(font, 320, UiMessages.text("menu.traces.failure_pages", java.util.Map.of("page", failurePage + 1, "count", pages)),
                UiMessages.text("menu.traces.acknowledge", java.util.Map.of("confirm", confirmHint(), "back", backHint())));
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
        int y = LIST_TOP + headingPitch(); // below the sticky heading
        int last = firstVisible;
        for (int i = firstVisible; i < entries.size(); i++) {
            int rowHeight = rowPitch();
            if (i > firstVisible
                    && !entries.get(i).gameId().equals(entries.get(i - 1).gameId())) {
                rowHeight += GROUP_GAP + headingPitch();
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
        MenuStyle.panel(font, 9, 143, 302, 51);
        line(UiMessages.text("menu.traces.selected", java.util.Map.of("trace", TraceLaunchStatus.catalogLabel(e))), 13, 147, 292, 1, 1, 1);
        line(UiMessages.text("menu.traces.frames_team", java.util.Map.of("frames", canonicalDecimal(e.frameCount()), "team", formatTeam(e))), 13, 159, 292, .85f, .9f, 1);
        line(UiMessages.text("menu.traces.bk2", java.util.Map.of("file", e.bk2Path().getFileName().toString())), 13, 171, 292, .75f, .82f, 1);
        line(UiMessages.text("menu.traces.offset", java.util.Map.of("offset", canonicalDecimal(e.bk2StartOffset()), "preosc", canonicalDecimal(e.preTraceOscFrames()))),
                13, 183, 292, .75f, .82f, 1);
    }

    private static String gameHeading(String gameId) {
        return switch (gameId) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3&K";
            default -> gameId;
        };
    }

    private static String formatTeam(TraceEntry e) {
        StringBuilder sb = new StringBuilder(e.team().mainCharacter());
        for (String sk : e.team().sidekicks()) {
            sb.append('+').append(sk);
        }
        return sb.toString();
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
