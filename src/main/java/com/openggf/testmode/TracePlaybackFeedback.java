package com.openggf.testmode;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuFieldLines;
import com.openggf.graphics.PixelFont;
import com.openggf.trace.catalog.TraceEntry;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.MenuFeedback.Cue.*;

/** Loading and held diagnostics for a trace selected in the playback browser. */
public final class TracePlaybackFeedback {
    public enum Result { NONE, LAUNCH, BACK }

    private static final int W = 320;
    private static final int BODY_TOP = 68;
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
            MenuChrome.page(font, W, "TRACE REPLAY", "ROM PARITY", "Replays the recording and compares it with the ROM");
            if (hasHeldFailure()) {
                renderFailure();
            } else if (selected != null && !stopped) {
                renderLoading();
                loadingPresented = true;
            }
        } finally {
            font.endMegaBatch();
        }
    }

    /**
     * The loading card names the trace as the library row did (kind tag, game badge, identity),
     * repeats the facts that told it apart, then lists the launch steps in order: the selection
     * is done, preparation is under way and the replay starts by itself.
     */
    private void renderLoading() {
        MenuChrome.descriptionBox(font, 9, 52, W - 18, 92, List.of(), List.of());
        MenuChrome.label(font, "LOADING TRACE", 17, 59, MenuChrome.GOLD);
        int x = 17 + MenuChrome.tag(font, 17, 75, "TRACE", MenuChrome.CYAN) + 4;
        x += MenuChrome.gameBadge(font, x, 75, selected.gameId()) + 6;
        MenuChrome.compact(font, MenuChrome.fitCompact(identity(selected), W - 17 - x), x, 76, MenuChrome.WHITE);
        MenuChrome.compact(font, MenuChrome.fitCompact(facts(selected), W - 34), 17, 89, MenuChrome.MUTED);
        MenuChrome.fill(font, 17, 102, W - 34, 1, MenuChrome.EDGE, .8f);
        MenuChrome.icon(font, MenuChrome.Icon.CHECK, 17, 108, MenuChrome.GREEN);
        MenuChrome.compact(font, "Trace selected", 29, 108, MenuChrome.TEXT);
        MenuChrome.triangle(font, 19, 120, 3, 'R', MenuChrome.GOLD, 1f);
        MenuChrome.compact(font, "Preparing the recorded game and team", 29, 119, MenuChrome.GOLD);
        MenuChrome.fill(font, 19, 133, 3, 3, MenuChrome.DIM);
        MenuChrome.compact(font, "Replay starts by itself", 29, 130, MenuChrome.DIM);
        MenuChrome.icon(font, MenuChrome.Icon.INFO, 12, 156, MenuChrome.CYAN);
        MenuChrome.compact(font, "During the replay, Tab opens its details.", 23, 156, MenuChrome.MUTED);
        MenuChrome.footer(font, W, null, List.of(MenuChrome.hint(backHint(), "CANCEL")), controller());
    }

    private static String identity(TraceEntry entry) {
        return entry.isRun() ? entry.runManifest().runId() : entry.dir().getFileName().toString();
    }

    /** One-based zone/act (or special stage / run size), team and exact length, as in the library summary. */
    private static String facts(TraceEntry entry) {
        List<String> facts = new ArrayList<>();
        if (entry.isRun()) facts.add(entry.runManifest().segments().size() + " segments");
        else {
            String label = entry.displayLabel();
            int special = label.indexOf("SPECIAL STAGE ");
            facts.add(special >= 0 ? "Special stage " + label.substring(special + 14)
                    : "Zone " + (entry.zone() + 1) + ", act " + (entry.act() + 1));
        }
        if (entry.team() != null && entry.team().mainCharacter() != null) {
            List<String> members = new ArrayList<>();
            members.add(entry.team().mainCharacter());
            if (entry.team().sidekicks() != null) members.addAll(entry.team().sidekicks());
            facts.add(String.join(" & ", members.stream().filter(name -> name != null && !name.isBlank())
                    .map(name -> Character.toUpperCase(name.charAt(0)) + name.substring(1)).toList()));
        }
        if (entry.frameCount() > 0) facts.add(String.format(java.util.Locale.ROOT, "%,d frames", entry.frameCount()));
        return String.join("   ", facts);
    }

    private void renderFailure() {
        List<MenuFieldLines.Line> lines = failureLines();
        int pages = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        failurePage = Math.min(failurePage, pages - 1);
        String title = TraceRunFailureStatus.current().isPresent() ? "TRACE FAILED" : "TRACE LAUNCH FAILED";
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, 12, 50, MenuChrome.RED);
        MenuChrome.compact(font, title, 23, 50, MenuChrome.RED);
        if (pages > 1) {
            String page = "Page " + (failurePage + 1) + " of " + pages;
            MenuChrome.compact(font, page, W - 12 - MenuChrome.compactWidth(page), 50, MenuChrome.DIM);
        }
        MenuChrome.descriptionBox(font, 9, 62, W - 18, 136, List.of(), List.of());
        MenuFieldLines.draw(font, lines, failurePage * LINES_PER_PAGE, LINES_PER_PAGE, 16, BODY_TOP, 11, labelWidth());
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (pages > 1) hints.add(MenuChrome.leftRight(input, "PAGE"));
        // Either key clears the failure and returns to the Playback list with the same row selected.
        hints.add(MenuChrome.hint(confirmHint(), "BACK TO LIST"));
        MenuChrome.footer(font, W, null, hints, controller());
    }

    private static List<MenuFieldLines.Field> failureFields() {
        List<MenuFieldLines.Field> fields = new ArrayList<>();
        var run = TraceRunFailureStatus.current();
        if (run.isPresent()) {
            var failure = run.get();
            fields.add(new MenuFieldLines.Field("Segment", Long.toString(failure.segmentIndex())));
            if (failure.isComparison()) {
                fields.add(new MenuFieldLines.Field("Expected", failure.expectedIdentity(), MenuChrome.GREEN));
                fields.add(new MenuFieldLines.Field("Actual", failure.actualIdentity(), MenuChrome.RED));
            } else fields.add(new MenuFieldLines.Field("Reason", failure.reason(), MenuChrome.WHITE));
            fields.add(new MenuFieldLines.Field("Cursor", Long.toString(failure.cursor())));
            fields.add(new MenuFieldLines.Field("Steps", Long.toString(failure.stepCount())));
        } else TraceLaunchStatus.current().ifPresent(failure -> {
            fields.add(new MenuFieldLines.Field("Trace", failure.traceLabel()));
            fields.add(new MenuFieldLines.Field("Reason", failure.reason(), MenuChrome.WHITE));
        });
        return fields;
    }

    private static int labelWidth() { return MenuFieldLines.labelWidth(failureFields()); }

    /** Complete diagnostic values wrapped by measured width, paged rather than shortened. */
    private static List<MenuFieldLines.Line> failureLines() {
        List<MenuFieldLines.Field> fields = failureFields();
        int labelWidth = MenuFieldLines.labelWidth(fields);
        return MenuFieldLines.layout(fields, labelWidth, W - 34 - labelWidth);
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
