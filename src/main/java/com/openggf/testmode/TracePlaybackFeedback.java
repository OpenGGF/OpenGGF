package com.openggf.testmode;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.graphics.PixelFont;
import com.openggf.trace.catalog.TraceEntry;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.MenuFeedback.Cue.*;

/** Loading and held diagnostics for a trace selected in the playback browser. */
public final class TracePlaybackFeedback {
    public enum Result { NONE, LAUNCH, BACK }

    private static final int W = 320;
    private static final int CHARS_PER_LINE = 48;
    private static final int LINES_PER_PAGE = 11;

    private final TraceEntry selected;
    private final PixelFont font;
    private InputHandler input;
    private Result pendingResult = Result.NONE;
    private boolean loadingPresented;
    private boolean launchIssued;
    private boolean stopped;
    private boolean failureAnnounced;
    private int failurePage;

    public TracePlaybackFeedback(TraceEntry selected, PixelFont font) {
        this.selected = selected;
        this.font = font;
    }

    public void update(InputHandler input) {
        this.input = input;
        if (stopped) return;
        if (hasHeldFailure()) {
            if (!failureAnnounced) { MenuFeedback.emit(ERROR); failureAnnounced = true; }
            int before = failurePage;
            if (MenuInput.left(input)) failurePage = Math.max(0, failurePage - 1);
            if (MenuInput.right(input)) failurePage = Math.min(pageCount() - 1, failurePage + 1);
            if (before != failurePage) MenuFeedback.emit(NAVIGATE);
            if (MenuInput.accept(input) || MenuInput.back(input)) {
                TraceLaunchStatus.clear();
                TraceRunFailureStatus.clear();
                MenuFeedback.emit(MenuInput.back(input) ? CANCEL : CONFIRM);
                finish();
            }
            return;
        }
        if (MenuInput.back(input)) {
            MenuFeedback.emit(CANCEL);
            finish();
        } else if (selected != null && loadingPresented && !launchIssued) {
            launchIssued = true;
            pendingResult = Result.LAUNCH;
        }
    }

    public void render() {
        if (font == null) return;
        font.beginMegaBatch();
        try {
            MenuChrome.page(font, W, "TRACE REPLAY", "ROM PARITY", "Replay recorded traces against the engine");
            if (hasHeldFailure()) {
                renderFailure();
            } else if (selected != null && !stopped) {
                MenuChrome.descriptionBox(font, 9, 66, W - 18, 60, List.of(), List.of());
                MenuChrome.label(font, "LOADING TRACE...", 17, 74, MenuChrome.GOLD);
                MenuChrome.compact(font, MenuChrome.fitCompact(TraceLaunchStatus.catalogLabel(selected), W - 34),
                        17, 92, MenuChrome.WHITE);
                MenuChrome.compact(font, "Preparing the recorded game and team", 17, 106, MenuChrome.MUTED);
                MenuChrome.footer(font, W, null, List.of(MenuChrome.hint(backHint(), "CANCEL")), controller());
                loadingPresented = true;
            }
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderFailure() {
        List<String> lines = failureLines();
        int pages = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        failurePage = Math.min(failurePage, pages - 1);
        String title = TraceRunFailureStatus.current().isPresent() ? "TRACE FAILED" : "TRACE LAUNCH FAILED";
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, 9, 50, MenuChrome.RED);
        MenuChrome.compact(font, title, 20, 50, MenuChrome.RED);
        MenuChrome.descriptionBox(font, 9, 62, W - 18, 136, List.of(), List.of());
        for (int i = failurePage * LINES_PER_PAGE, y = 67;
                i < Math.min(lines.size(), (failurePage + 1) * LINES_PER_PAGE); i++, y += 11) {
            MenuStyle.text(font, lines.get(i), 12, y, W - 24, 1, 1, 1);
        }
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (pages > 1) hints.add(MenuChrome.leftRight(input, "PAGE " + (failurePage + 1) + "/" + pages));
        hints.add(MenuChrome.hint(confirmHint(), "ACKNOWLEDGE"));
        MenuChrome.footer(font, W, null, hints, controller());
    }

    private static List<String> failureLines() {
        List<String> messages = new ArrayList<>();
        var run = TraceRunFailureStatus.current();
        if (run.isPresent()) {
            var failure = run.get();
            messages.add("Segment: " + failure.segmentIndex());
            if (failure.isComparison()) {
                messages.add("Expected: " + failure.expectedIdentity());
                messages.add("Actual: " + failure.actualIdentity());
            } else messages.add("Reason: " + failure.reason());
            messages.add("Cursor: " + failure.cursor() + "  Steps: " + failure.stepCount());
        } else TraceLaunchStatus.current().ifPresent(failure -> {
            messages.add("Trace: " + failure.traceLabel());
            messages.add("Reason: " + failure.reason());
        });
        List<String> lines = new ArrayList<>();
        for (String message : messages) {
            for (int start = 0; start < message.length(); start += CHARS_PER_LINE)
                lines.add(message.substring(start, Math.min(message.length(), start + CHARS_PER_LINE)));
        }
        return lines;
    }

    private static boolean hasHeldFailure() {
        return TraceLaunchStatus.current().isPresent() || TraceRunFailureStatus.current().isPresent();
    }

    private int pageCount() {
        return Math.max(1, (failureLines().size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    private void finish() { stopped = true; pendingResult = Result.BACK; }
    private String confirmHint() { return MenuChrome.confirmKey(input); }
    private String backHint() { return MenuChrome.backKey(input); }
    private boolean controller() { return MenuChrome.controller(input); }

    public Result consumeResult() {
        Result result = pendingResult;
        pendingResult = Result.NONE;
        return result;
    }

    public TraceEntry selectedEntry() { return selected; }
    public void launchFailed() { loadingPresented = false; launchIssued = true; }
    public void markFailureFeedbackAnnounced() { failureAnnounced = true; }
}
