package openggf.timeattack;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.LevelOverlayCanvas;

/**
 * Top-right HUD for solo time attack: current time, best time, split delta and a new-record
 * callout, drawn over the level through the run host's overlay canvas.
 */
public final class TimeAttackHud {
    private static final int MARGIN = 4;
    private static final int TOP_Y = 4;
    private static final int LINE_HEIGHT = 8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFC0C0C0;
    private static final int GREEN = 0xFF60FF60;
    private static final int RED = 0xFFFF6060;
    private static final int YELLOW = 0xFFFFFF40;
    private static final int SHADOW = 0xC0000000;

    private TimeAttackHud() {
    }

    public static void draw(LevelOverlayCanvas canvas, TimeAttackHudState state) {
        if (state == null || !state.active()) {
            return;
        }
        int y = TOP_Y;
        drawRight(canvas, TimeAttackTimeFormat.frames(state.elapsedDisplayFrames()), y, WHITE);
        y += LINE_HEIGHT;
        if (state.bestTimeFrames() >= 0) {
            drawRight(canvas, "BEST " + TimeAttackTimeFormat.frames(state.bestTimeFrames()), y, GREY);
            y += LINE_HEIGHT;
        }
        String delta = TimeAttackTimeFormat.delta(state.lastSplitDelta());
        if (!delta.isEmpty()) {
            drawRight(canvas, delta, y, state.lastSplitDelta() < 0 ? GREEN : RED);
            y += LINE_HEIGHT;
        }
        if (state.finished() && state.newBest()) {
            drawRight(canvas, "NEW RECORD", y, YELLOW);
        }
    }

    private static void drawRight(LevelOverlayCanvas canvas, String line, int y, int argb) {
        int x = canvas.width() - MARGIN - CompactFont.width(line, 1);
        CompactFont.shadowed(canvas, line, x, y, 1, argb, SHADOW);
    }
}
