package com.openggf.testmode;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.TraceSessionLauncher;
import com.openggf.debug.DebugColor;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.configuration.GlfwKeyNameResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.MenuChrome;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceHudModel;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Compact replay status with manually opened, paged native-pixel diagnostics.
 * Comparison failures never control visibility or playback state.
 */
public final class TraceHudOverlay implements TraceSessionOverlay {

    private final TraceHudModel model;
    private Supplier<TracePlaybackStatus> playbackStatusSupplier =
            TraceHudOverlay::currentPlaybackStatus;
    private final Supplier<String> pauseKeyLabelSupplier;
    private final BooleanSupplier pausedSupplier;
    private final Supplier<String> focusLabelSupplier;
    private final Supplier<String> rewindStatusSupplier;
    private final boolean centeredNative;
    private String referenceLabel = "rom";

    public TraceHudOverlay(LiveTraceComparator comparator) {
        this(comparator, TraceHudOverlay::configuredPauseKeyLabel,
                TraceHudOverlay::isGameLoopPaused, () -> null, () -> null);
    }

    public TraceHudOverlay(LiveTraceComparator comparator, Supplier<String> focusLabelSupplier) {
        this(comparator, TraceHudOverlay::configuredPauseKeyLabel,
                TraceHudOverlay::isGameLoopPaused, focusLabelSupplier, () -> null);
    }

    public TraceHudOverlay(LiveTraceComparator comparator,
                           Supplier<String> focusLabelSupplier,
                           Supplier<String> rewindStatusSupplier) {
        this(comparator, TraceHudOverlay::configuredPauseKeyLabel,
                TraceHudOverlay::isGameLoopPaused, focusLabelSupplier, rewindStatusSupplier);
    }

    public TraceHudOverlay(TraceHudModel model,
                           Supplier<String> focusLabelSupplier,
                           Supplier<String> rewindStatusSupplier) {
        this(model, TraceHudOverlay::configuredPauseKeyLabel,
                TraceHudOverlay::isGameLoopPaused,
                focusLabelSupplier, rewindStatusSupplier, true);
    }

    TraceHudOverlay(LiveTraceComparator comparator,
                    Supplier<String> pauseKeyLabelSupplier,
                    BooleanSupplier pausedSupplier,
                    Supplier<String> focusLabelSupplier,
                    Supplier<String> rewindStatusSupplier) {
        this.model = comparator;
        this.pauseKeyLabelSupplier = pauseKeyLabelSupplier;
        this.pausedSupplier = pausedSupplier;
        this.focusLabelSupplier = focusLabelSupplier;
        this.rewindStatusSupplier = rewindStatusSupplier;
        this.centeredNative = true;
    }

    TraceHudOverlay(TraceHudModel model,
                    Supplier<String> pauseKeyLabelSupplier,
                    BooleanSupplier pausedSupplier,
                    Supplier<String> focusLabelSupplier,
                    Supplier<String> rewindStatusSupplier) {
        this(model, pauseKeyLabelSupplier, pausedSupplier, focusLabelSupplier,
                rewindStatusSupplier, false);
    }

    TraceHudOverlay(TraceHudModel model,
                    Supplier<String> pauseKeyLabelSupplier,
                    BooleanSupplier pausedSupplier,
                    Supplier<String> focusLabelSupplier,
                    Supplier<String> rewindStatusSupplier,
                    boolean centeredNative) {
        this.model = model;
        this.pauseKeyLabelSupplier = pauseKeyLabelSupplier;
        this.pausedSupplier = pausedSupplier;
        this.focusLabelSupplier = focusLabelSupplier;
        this.rewindStatusSupplier = rewindStatusSupplier;
        this.centeredNative = centeredNative;
    }

    TraceHudOverlay(LiveTraceComparator comparator,
                    Supplier<String> pauseKeyLabelSupplier,
                    BooleanSupplier pausedSupplier,
                    Supplier<String> focusLabelSupplier) {
        this(comparator, pauseKeyLabelSupplier, pausedSupplier, focusLabelSupplier, () -> null);
    }

    /** Shares the trace panel with engine recordings without labelling their comparison data as ROM values. */
    public static TraceHudOverlay forRecordingReplay(TraceHudModel model, BooleanSupplier paused,
                                                      Supplier<TracePlaybackStatus> playback,
                                                      Supplier<String> verificationNotice) {
        var overlay = new TraceHudOverlay(model, TraceHudOverlay::configuredPauseKeyLabel,
                paused, () -> null, verificationNotice, true);
        overlay.referenceLabel = "rec";
        overlay.setPlaybackStatusSupplier(playback);
        return overlay;
    }

    private static final float SCALE = 1;
    private static final int ROW = 12, BODY_TOP = 34, PAGE_LINES = 13;
    private boolean detailsVisible;
    private int page;

    @Override
    public void handleInput(com.openggf.control.InputHandler input) {
        if (input == null) return;
        if (input.isPhysicalKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB)) {
            detailsVisible = !detailsVisible;
            page = 0;
        }
        if (!detailsVisible) return;
        if (input.isPhysicalKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN)) page++;
        if (input.isPhysicalKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP)) page = Math.max(0, page - 1);
    }

    @Override public boolean detailsVisible() { return detailsVisible; }

    @Override
    public void render(PixelFontTextRenderer text) {
        if (!detailsVisible) {
            renderSummary(text);
            return;
        }
        int width = projectionWidth();
        int columns = Math.max(1, (width - 32) / Math.max(1, text.measureWidth("M", SCALE)));
        List<DetailLine> lines = detailLines(columns);
        int pages = Math.max(1, (lines.size() + PAGE_LINES - 1) / PAGE_LINES);
        page = Math.min(page, pages - 1);
        GraphicsManager graphics = graphics();
        if (graphics != null && graphics.isGlInitialized()) {
            panel(graphics, 8, 8, width - 16, 208);
            graphics.flushScreenSpace();
        }
        text.beginBatch();
        try {
            text.drawShadowedText("TRACE DETAILS", 16, 14, DebugColor.YELLOW, SCALE);
            int first = page * PAGE_LINES, end = Math.min(lines.size(), first + PAGE_LINES);
            for (int i = first; i < end; i++) {
                DetailLine line = lines.get(i);
                text.drawShadowedText(line.text(), 16, BODY_TOP + (i - first) * ROW, line.color(), SCALE);
            }
            text.drawShadowedText("Page " + (page + 1) + "/" + pages, 16, 190, DebugColor.GRAY, SCALE);
            text.drawShadowedText("PgUp/PgDn: Page   Tab: Close", 16, 204, DebugColor.LIGHT_GRAY, SCALE);
        } finally { text.endBatch(); }
    }

    private void renderSummary(PixelFontTextRenderer text) {
        TracePlaybackStatus playback = playbackStatusSupplier.get();
        boolean problem = model.hasRecordingDesync() || model.errorCount() > 0 || model.warningCount() > 0;
        String title = model.errorCount() > 0 ? "ERRORS" : model.warningCount() > 0 ? "WARN"
                : model.hasRecordingDesync() ? "DESYNC" : "PLAY";
        String value = model.errorCount() > 0 ? Integer.toString(model.errorCount())
                : model.warningCount() > 0 ? Integer.toString(model.warningCount())
                : playback == null ? "" : playback.frame() + "/" + playback.frameCount();
        if (model.isComplete()) { title = "TRACE"; value = "COMPLETE"; }
        String rewind = rewindStatusSupplier.get();
        boolean rewinding = rewind != null && rewind.startsWith("REWIND ");
        if (rewinding) { title = "REW"; value = "F:" + rewind.substring(7); }
        String hint = playback == null ? "Tab: Details" : playback.rateLabel() + "  Tab: Details";
        var status = problem ? com.openggf.game.presentation.TransportHud.Status.WARNING
                : com.openggf.game.presentation.TransportHud.Status.PLAY;
        if (rewinding) status = com.openggf.game.presentation.TransportHud.Status.REWIND;
        com.openggf.game.presentation.TransportHud.render(text, graphics(), projectionWidth(),
                com.openggf.game.presentation.TransportHud.Placement.BOTTOM_RIGHT,
                new com.openggf.game.presentation.TransportHud.Message(status, title, value, hint, -1));
    }

    private record DetailLine(String text, DebugColor color) { }

    private List<DetailLine> detailLines(int columns) {
        var lines = new java.util.ArrayList<DetailLine>();
        TracePlaybackStatus playback = playbackStatusSupplier.get();
        if (playback != null) {
            add(lines, "Movie: " + playback.movieName(), columns, DebugColor.LIGHT_GRAY);
            add(lines, "Frame: " + playback.frame() + "/" + playback.frameCount(), columns, DebugColor.LIGHT_GRAY);
            add(lines, "Mode: " + playback.mode() + "   Rate: " + playback.rateLabel(), columns, DebugColor.CYAN);
        }
        add(lines, "Errors: " + model.errorCount() + "   Warnings: " + model.warningCount()
                + "   Lag: " + model.laggedFrames(), columns, model.errorCount() > 0 ? DebugColor.RED : DebugColor.LIGHT_GRAY);
        int action = model.recentActionMask(), input = model.recentInputMask();
        String keys = "" + bit(action, 1, 'A') + bit(action, 2, 'B') + bit(action, 4, 'C')
                + bit(input, AbstractPlayableSprite.INPUT_UP, 'U') + bit(input, AbstractPlayableSprite.INPUT_DOWN, 'D')
                + bit(input, AbstractPlayableSprite.INPUT_LEFT, 'L') + bit(input, AbstractPlayableSprite.INPUT_RIGHT, 'R')
                + (model.recentStartPressed() ? 'S' : '.');
        add(lines, "Input: " + keys, columns, DebugColor.GREEN);
        String notice = rewindStatusSupplier.get();
        if (notice != null && !notice.isBlank()) add(lines, notice, columns, DebugColor.CYAN);
        if (pausedSupplier.getAsBoolean()) {
            add(lines, "Paused - " + pauseKeyLabelSupplier.get() + " resumes", columns, DebugColor.YELLOW);
            String focus = focusLabelSupplier.get();
            if (focus != null) add(lines, "Camera: " + focus + " (Left/Right)", columns, DebugColor.LIGHT_GRAY);
        }
        List<MismatchEntry> recent = model.recentMismatches();
        if (recent.isEmpty()) add(lines, "No mismatches recorded", columns, DebugColor.GRAY);
        for (int i = 0; i < recent.size(); i++) {
            MismatchEntry m = recent.get(i);
            add(lines, "Mismatch " + (i + 1) + "/" + recent.size() + "  F:" + m.frame(), columns,
                    m.severity() == Severity.ERROR ? DebugColor.RED : DebugColor.ORANGE);
            add(lines, "Field: " + m.field(), columns, DebugColor.LIGHT_GRAY);
            add(lines, (referenceLabel.equals("rec") ? "Recorded: " : "ROM: ") + m.romValue(), columns, DebugColor.CYAN);
            add(lines, "Engine: " + m.engineValue(), columns, DebugColor.LIGHT_GRAY);
            add(lines, "Delta: " + m.delta() + (m.repeatCount() > 1 ? "  Repeated: " + m.repeatCount() : ""),
                    columns, DebugColor.LIGHT_GRAY);
        }
        if (model.isComplete()) add(lines, "TRACE COMPLETE", columns, DebugColor.YELLOW);
        return lines;
    }

    /** Wrap, including long identifiers, without dropping or ellipsizing diagnostic values. */
    private static void add(List<DetailLine> out, String value, int columns, DebugColor color) {
        String readable = com.openggf.game.MenuDetailsScreen.readable(value == null ? "" : value);
        for (String paragraph : readable.split("\\R", -1)) {
            while (paragraph.length() > columns) {
                int cut = paragraph.lastIndexOf(' ', columns);
                if (cut <= 0) cut = columns;
                out.add(new DetailLine(paragraph.substring(0, cut), color));
                paragraph = paragraph.substring(cut).stripLeading();
            }
            out.add(new DetailLine(paragraph, color));
        }
    }

    private static GraphicsManager graphics() {
        try { return GameServices.graphics(); }
        catch (IllegalStateException unavailable) { return null; }
    }

    private static void panel(GraphicsManager graphics, int x, int y, int width, int height) {
        float[] c = MenuChrome.NAVY;
        int argb = 0xF2000000 | Math.round(c[0] * 255) << 16
                | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
        graphics.registerCommand(GLCommand.screenSpaceRect(graphics, 224, x, y, width, height, argb));
    }

    private static int projectionWidth() {
        GraphicsManager graphics = graphics();
        return graphics == null ? 320 : Math.max(320, graphics.getProjectionWidth());
    }

    static TracePlaybackStatus currentPlaybackStatus() {
        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session == null) {
            return null;
        }
        PlaybackDebugManager playback = GameServices.playbackDebug();
        String movieName = playback.movieName();
        if (movieName == null) {
            return null;
        }
        return new TracePlaybackStatus(
                movieName,
                playback.observedMode().name(),
                playback.getCursorFrame(),
                Math.max(0, playback.getMovieFrameCount() - 1),
                session.playbackRateDisplay());
    }

    /** Test seam for the transport summary. */
    void setPlaybackStatusSupplier(Supplier<TracePlaybackStatus> supplier) {
        this.playbackStatusSupplier = supplier;
    }

    private static char bit(int mask, int flag, char letter) {
        return (mask & flag) != 0 ? letter : '.';
    }

    static String configuredPauseKeyLabel() {
        int pauseKey = GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY);
        return GlfwKeyNameResolver.nameOf(pauseKey);
    }

    static boolean isGameLoopPaused() {
        GameLoop loop = Engine.currentGameLoop();
        return loop != null && loop.isPaused();
    }
}
