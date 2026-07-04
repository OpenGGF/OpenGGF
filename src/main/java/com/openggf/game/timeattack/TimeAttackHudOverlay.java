package com.openggf.game.timeattack;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Top-right HUD for solo time-attack: current time, best time, split delta,
 * and a new-record callout.
 */
public final class TimeAttackHudOverlay {

    private static final float SCALE = 0.5f;
    private static final int MARGIN = 4;
    private static final int TOP_Y = 4;
    private static final int LINE_HEIGHT = 6;

    private final Supplier<TimeAttackHudState> stateSupplier;
    private final SonicConfigurationService config;

    public TimeAttackHudOverlay(Supplier<TimeAttackHudState> stateSupplier, SonicConfigurationService config) {
        this.stateSupplier = Objects.requireNonNull(stateSupplier, "stateSupplier");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void render(PixelFontTextRenderer text) {
        TimeAttackHudState state = stateSupplier.get();
        if (state == null || !state.active()) {
            return;
        }
        int screenWidth = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        text.beginBatch();
        try {
            int y = TOP_Y;
            drawRight(text, TimeAttackTimeFormat.frames(state.elapsedDisplayFrames()), screenWidth, y,
                    DebugColor.WHITE);
            y += LINE_HEIGHT;
            if (state.bestTimeFrames() >= 0) {
                drawRight(text, "BEST " + TimeAttackTimeFormat.frames(state.bestTimeFrames()), screenWidth, y,
                        DebugColor.LIGHT_GRAY);
                y += LINE_HEIGHT;
            }
            String delta = TimeAttackTimeFormat.delta(state.lastSplitDelta());
            if (!delta.isEmpty()) {
                DebugColor deltaColor = state.lastSplitDelta() < 0 ? DebugColor.GREEN : DebugColor.RED;
                drawRight(text, delta, screenWidth, y, deltaColor);
                y += LINE_HEIGHT;
            }
            if (state.finished() && state.newBest()) {
                drawRight(text, "NEW RECORD", screenWidth, y, DebugColor.YELLOW);
            }
        } finally {
            text.endBatch();
        }
    }

    private static void drawRight(PixelFontTextRenderer text, String line, int screenWidth, int y,
            DebugColor color) {
        int x = screenWidth - MARGIN - text.measureWidth(line, SCALE);
        text.drawShadowedText(line, x, y, color, SCALE);
    }
}
