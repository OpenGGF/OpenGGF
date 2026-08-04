package com.openggf.testmode;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.debug.DebugColor;
import com.openggf.configuration.GlfwKeyNameResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
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
    private final Supplier<String> pauseKeyLabelSupplier;
    private final BooleanSupplier pausedSupplier;
    private final Supplier<String> focusLabelSupplier;
    private final Supplier<String> rewindStatusSupplier;
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
    }

    TraceHudOverlay(TraceHudModel model,
                    Supplier<String> pauseKeyLabelSupplier,
                    BooleanSupplier pausedSupplier,
                    Supplier<String> focusLabelSupplier,
                    Supplier<String> rewindStatusSupplier) {
        this.model = model;
        this.pauseKeyLabelSupplier = pauseKeyLabelSupplier;
        this.pausedSupplier = pausedSupplier;
        this.focusLabelSupplier = focusLabelSupplier;
        this.rewindStatusSupplier = rewindStatusSupplier;
    }

    TraceHudOverlay(LiveTraceComparator comparator,
                    Supplier<String> pauseKeyLabelSupplier,
                    BooleanSupplier pausedSupplier,
                    Supplier<String> focusLabelSupplier) {
        this(comparator, pauseKeyLabelSupplier, pausedSupplier, focusLabelSupplier, () -> null);
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

    @Override
    public void render(PixelFontTextRenderer text) {
        text.beginBatch();
        try {
            int y = TOP_Y;
            boolean paused = pausedSupplier.getAsBoolean();
            if (desyncPauseMessageShown && !paused) {
                desyncPauseMessageDismissed = true;
            }
            if (model.hasRecordingDesync() && paused && !desyncPauseMessageDismissed) {
                desyncPauseMessageShown = true;
                text.drawShadowedText("Game Paused due to recording desync. Press "
                                + pauseKeyLabelSupplier.get() + " to resume",
                        X, TOP_Y - LINE_HEIGHT, DebugColor.RED, SCALE);
            }

            text.drawShadowedText(String.format("ERRORS %4d", model.errorCount()),
                    X, y, DebugColor.RED, SCALE);
            y += LINE_HEIGHT;
            text.drawShadowedText(String.format("WARN   %4d", model.warningCount()),
                    X, y, DebugColor.ORANGE, SCALE);
            y += LINE_HEIGHT;
            text.drawShadowedText(String.format("LAG    %4d", model.laggedFrames()),
                    X, y, DebugColor.GRAY, SCALE);
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
            text.drawShadowedText(active.toString(), X, y, DebugColor.GREEN, SCALE);
            y += SECTION_GAP;

            String rewindStatus = rewindStatusSupplier.get();
            if (rewindStatus != null && !rewindStatus.isBlank()) {
                text.drawShadowedText(rewindStatus, X, y, DebugColor.CYAN, SCALE);
                y += SECTION_GAP;
            }

            text.drawShadowedText("Last mismatches:", X, y, DebugColor.LIGHT_GRAY, SCALE);
            y += LINE_HEIGHT;
            List<MismatchEntry> recent = model.recentMismatches();
            for (MismatchEntry m : recent) {
                String line = String.format("f %04X %s rom=%s eng=%s \u0394%s%s",
                        m.frame(), m.field(), m.romValue(),
                        m.engineValue(), m.delta(),
                        m.repeatCount() > 1 ? (" \u00D7" + m.repeatCount()) : "");
                DebugColor color = m.severity() == Severity.ERROR
                        ? DebugColor.RED : DebugColor.ORANGE;
                text.drawShadowedText(line, X, y, color, SCALE);
                y += LINE_HEIGHT;
            }

            if (model.isComplete()) {
                text.drawShadowedText("TRACE COMPLETE", X, COMPLETE_BANNER_Y,
                        DebugColor.YELLOW, SCALE);
            }

            if (paused) {
                String focusLabel = focusLabelSupplier.get();
                if (focusLabel != null) {
                    String main = "Camera: " + focusLabel;
                    String hint = "<- -> Cycle Cameras";
                    int mainW = text.measureWidth(main, SCALE);
                    int hintW = text.measureWidth(hint, SCALE);
                    // Right-anchor the camera-focus block against the actual projection width
                    // so it stays in the top-right corner at any viewport width.
                    // Falls back to NATIVE_WIDTH (320) when GraphicsManager is unavailable
                    // (e.g. headless tests). At native 320 the value is unchanged.
                    int projW = projectionWidth();
                    int mainX = projW - RIGHT_MARGIN - mainW;
                    int hintX = projW - RIGHT_MARGIN - hintW;
                    text.drawShadowedText(main, mainX, TOP_Y, DebugColor.LIGHT_GRAY, SCALE);
                    text.drawShadowedText(hint, hintX, TOP_Y + LINE_HEIGHT, DebugColor.GRAY, SCALE);
                }
            }
        } finally {
            text.endBatch();
        }
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
