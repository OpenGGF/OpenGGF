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
 * Bottom-right HUD painted each frame while a trace session is active.
 * Red ERRORS, orange WARN, grey LAG counters; BK2 input visualiser
 * (A/B/C/U/D/L/R/S); and the last five mismatch entries, severity
 * coloured, with repeat counts.
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
    private boolean desyncPauseMessageShown;
    private boolean desyncPauseMessageDismissed;

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

    private static final float SCALE = 0.5f;
    private static final int LINE_HEIGHT = 6;
    private static final int SECTION_GAP = 8;

    // Lower-left corner, above the lives counter at y~208. Leaves the
    // top-bar HUD (rings/time/score) and right-side game HUD clear.
    private static final int X = 4;
    private static final int COMPLETE_BANNER_Y = 110;
    private static final int TOP_Y = 120;
    private static final int RIGHT_MARGIN = 4;
    private static final int NATIVE_WIDTH = 320;

    // Genuine top-right corner, clear of both the ROM's top-bar HUD (which is
    // left-anchored) and the paused camera-focus block down at TOP_Y.
    private static final int STATUS_TOP_Y = 8;

    @Override
    public void render(PixelFontTextRenderer text) {
        TracePlaybackStatus playback = playbackStatusSupplier.get();
        List<MismatchEntry> recent = model.recentMismatches();
        String rewindStatus = rewindStatusSupplier.get();
        boolean paused = pausedSupplier.getAsBoolean();
        String focusLabel = paused ? focusLabelSupplier.get() : null;
        renderBacking(text, playback, recent, rewindStatus, focusLabel);
        text.beginBatch();
        try {
            renderPlaybackStatus(text, playback);
            int x = horizontalOrigin() + X;
            int y = TOP_Y;
            if (desyncPauseMessageShown && !paused) {
                desyncPauseMessageDismissed = true;
            }
            if (model.hasRecordingDesync() && paused && !desyncPauseMessageDismissed) {
                desyncPauseMessageShown = true;
                text.drawShadowedText("Paused: recording desync", x,
                        TOP_Y - 2 * LINE_HEIGHT, DebugColor.RED, SCALE);
                text.drawShadowedText("Press " + pauseKeyLabelSupplier.get() + " to resume",
                        x, TOP_Y - LINE_HEIGHT, DebugColor.LIGHT_GRAY, SCALE);
            }

            text.drawShadowedText(String.format("ERRORS %4d", model.errorCount()),
                    x, y, DebugColor.RED, SCALE);
            y += LINE_HEIGHT;
            text.drawShadowedText(String.format("WARN   %4d", model.warningCount()),
                    x, y, DebugColor.ORANGE, SCALE);
            y += LINE_HEIGHT;
            text.drawShadowedText(String.format("LAG    %4d", model.laggedFrames()),
                    x, y, DebugColor.GRAY, SCALE);
            y += SECTION_GAP;

            int actionMask = model.recentActionMask();
            int inputMask = model.recentInputMask();
            boolean start = model.recentStartPressed();
            StringBuilder active = new StringBuilder();
            active.append(bit(actionMask, 0x01, 'A'));
            active.append(bit(actionMask, 0x02, 'B'));
            active.append(bit(actionMask, 0x04, 'C'));
            active.append(bit(inputMask, AbstractPlayableSprite.INPUT_UP, 'U'));
            active.append(bit(inputMask, AbstractPlayableSprite.INPUT_DOWN, 'D'));
            active.append(bit(inputMask, AbstractPlayableSprite.INPUT_LEFT, 'L'));
            active.append(bit(inputMask, AbstractPlayableSprite.INPUT_RIGHT, 'R'));
            active.append(start ? 'S' : '.');
            text.drawShadowedText(active.toString(), x, y, DebugColor.GREEN, SCALE);
            y += SECTION_GAP;

            if (rewindStatus != null && !rewindStatus.isBlank()) {
                text.drawShadowedText(rewindStatus, x, y, DebugColor.CYAN, SCALE);
                y += SECTION_GAP;
            }

            text.drawShadowedText("Last mismatches:", x, y, DebugColor.LIGHT_GRAY, SCALE);
            y += LINE_HEIGHT;
            for (MismatchEntry m : recent) {
                String line = mismatchLine(m);
                DebugColor color = m.severity() == Severity.ERROR
                        ? DebugColor.RED : DebugColor.ORANGE;
                text.drawShadowedText(line, x, y, color, SCALE);
                y += LINE_HEIGHT;
            }

            if (model.isComplete()) {
                text.drawShadowedText("TRACE COMPLETE", x, COMPLETE_BANNER_Y,
                        DebugColor.YELLOW, SCALE);
            }

            if (paused) {
                if (focusLabel != null) {
                    String main = "Camera: " + focusLabel;
                    String hint = "<- -> Cycle Cameras";
                    int mainW = text.measureWidth(main, SCALE);
                    int hintW = text.measureWidth(hint, SCALE);
                    // Right-anchor the camera-focus block against the actual projection width
                    // so it stays in the top-right corner at any viewport width.
                    // Falls back to NATIVE_WIDTH (320) when GraphicsManager is unavailable
                    // (e.g. headless tests). At native 320 the value is unchanged.
                    int right = horizontalOrigin() + NATIVE_WIDTH - RIGHT_MARGIN;
                    int mainX = right - mainW;
                    int hintX = right - hintW;
                    text.drawShadowedText(main, mainX, TOP_Y, DebugColor.LIGHT_GRAY, SCALE);
                    text.drawShadowedText(hint, hintX, TOP_Y + LINE_HEIGHT, DebugColor.GRAY, SCALE);
                }
            }
        } finally {
            text.endBatch();
        }
    }

    private String mismatchLine(MismatchEntry m) {
        return String.format("f %04X %s %s=%s eng=%s \u0394%s%s", m.frame(), m.field(), referenceLabel,
                m.romValue(), m.engineValue(), m.delta(), m.repeatCount() > 1 ? " \u00D7" + m.repeatCount() : "");
    }

    /** Quiet backing plates keep diagnostics readable over bright terrain without dimming the whole game. */
    private void renderBacking(PixelFontTextRenderer text, TracePlaybackStatus playback,
                               List<MismatchEntry> recent, String rewindStatus, String focusLabel) {
        GraphicsManager graphics;
        try { graphics = GameServices.graphics(); }
        catch (IllegalStateException unavailable) { return; }
        // Logic-only hosts intentionally have no GL; the text recording seams still run there.
        if (graphics == null || !graphics.isGlInitialized()) return;
        int origin = horizontalOrigin();
        int bodyWidth = text.measureWidth("Last mismatches:", SCALE);
        for (MismatchEntry mismatch : recent)
            bodyWidth = Math.max(bodyWidth, text.measureWidth(mismatchLine(mismatch), SCALE));
        boolean rewind = rewindStatus != null && !rewindStatus.isBlank();
        if (rewind) bodyWidth = Math.max(bodyWidth, text.measureWidth(rewindStatus, SCALE));
        // Reserve the notice and completion row; presentation never changes comparator/pause state.
        bodyWidth = Math.max(bodyWidth, text.measureWidth("Paused: recording desync", SCALE));
        bodyWidth = Math.max(bodyWidth, text.measureWidth("Press " + pauseKeyLabelSupplier.get() + " to resume", SCALE));
        int bottom = TOP_Y + 2 * LINE_HEIGHT + 2 * SECTION_GAP
                + (rewind ? SECTION_GAP : 0) + (recent.size() + 1) * LINE_HEIGHT;
        int top = COMPLETE_BANNER_Y - 4;
        panel(graphics, origin, top, Math.min(320, bodyWidth + 8), bottom - top + 4);
        if (focusLabel != null) {
            int focusWidth = Math.max(text.measureWidth("Camera: " + focusLabel, SCALE),
                    text.measureWidth("<- -> Cycle Cameras", SCALE)) + 8;
            panel(graphics, origin + NATIVE_WIDTH - RIGHT_MARGIN - focusWidth + 4,
                    TOP_Y - 4, focusWidth, 2 * LINE_HEIGHT + 8);
        }
        if (playback != null) {
            int statusWidth = 0;
            for (String line : List.of(playback.movieName() == null ? "" : playback.movieName(),
                    "Mode: " + playback.mode(), "Frame: " + playback.frame() + "/" + playback.frameCount(),
                    "Rate: " + playback.rateLabel()))
                statusWidth = Math.max(statusWidth, text.measureWidth(line, SCALE));
            statusWidth = Math.min(320, statusWidth + 8);
            panel(graphics, origin + NATIVE_WIDTH - statusWidth, STATUS_TOP_Y - 4, statusWidth, 4 * LINE_HEIGHT + 8);
        }
        graphics.flushScreenSpace();
    }

    private static void panel(GraphicsManager graphics, int x, int y, int width, int height) {
        float[] c = MenuChrome.NAVY;
        int argb = 0xF2000000 | Math.round(c[0] * 255) << 16
                | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
        graphics.registerCommand(GLCommand.screenSpaceRect(graphics, 224, x, y, width, height, argb));
    }

    /**
     * Returns the current projection-space width of the viewport.
     * Used to right-anchor the camera-focus label block in the top-right corner.
     * Falls back to {@link #NATIVE_WIDTH} (320) when the graphics service is unavailable
     * (e.g. headless tests). At native 320 the returned value equals NATIVE_WIDTH.
     */
    private static int projectionWidth() {
        try {
            com.openggf.graphics.GraphicsManager gm = GameServices.graphics();
            if (gm != null) {
                return gm.getProjectionWidth();
            }
        } catch (Exception ignored) {
            // GraphicsManager may not be available in all test contexts.
        }
        return NATIVE_WIDTH;
    }

    private int horizontalOrigin() {
        return centeredNative ? Math.max(0, (projectionWidth() - NATIVE_WIDTH) / 2) : 0;
    }

    /**
     * Paints the transport summary this HUD took over from the legacy
     * {@code == PLAYBACK ==} panel, right-anchored in the top-right corner.
     * Each line is measured and placed individually so the block stays flush
     * to the right edge with a proportional font.
     */
    private void renderPlaybackStatus(PixelFontTextRenderer text, TracePlaybackStatus status) {
        if (status == null) {
            return;
        }
        int right = horizontalOrigin() + NATIVE_WIDTH - RIGHT_MARGIN;
        int y = STATUS_TOP_Y;
        y = drawRightAligned(text, status.movieName(), right, y, DebugColor.LIGHT_GRAY);
        y = drawRightAligned(text, "Mode: " + status.mode(), right, y, DebugColor.GRAY);
        y = drawRightAligned(text,
                "Frame: " + status.frame() + "/" + status.frameCount(),
                right, y, DebugColor.GRAY);
        drawRightAligned(text, "Rate: " + status.rateLabel(), right, y, DebugColor.CYAN);
    }

    /** Draws one right-anchored line and returns the next line's y. */
    private int drawRightAligned(
            PixelFontTextRenderer text, String line, int right, int y, DebugColor color) {
        if (line == null || line.isBlank()) {
            return y;
        }
        text.drawShadowedText(line, right - text.measureWidth(line, SCALE), y, color, SCALE);
        return y + LINE_HEIGHT;
    }

    /**
     * Resolves the transport summary from the live playback session and the
     * active trace session's speed ladder. Static by the same pattern as
     * {@link #configuredPauseKeyLabel()} and {@link #isGameLoopPaused()};
     * tests substitute their own through
     * {@link #setPlaybackStatusSupplier(Supplier)}.
     */
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
