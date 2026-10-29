package com.openggf;

import com.openggf.game.MenuChrome;
import com.openggf.game.presentation.TransportHud;

import java.util.List;

/**
 * The hold-Escape toast's resting row while a pause or window video plate appears or disappears
 * under it. A plate that would touch the toast moves it at once (any slide would cross the plate);
 * a row change whose whole vertical path is clear of every plate on screen eases over
 * {@link #EASE_FRAMES} frames instead of jumping. Presentation only: timed by the toast's own
 * shown-frame count, it never changes when the hold starts, completes or is released.
 */
final class EscapeToastMotion {
    static final int EASE_FRAMES = 6;

    private int fromY = Integer.MIN_VALUE;
    private int toY;
    private int startFrame;

    /** Forget the previous row (the toast left the screen). */
    void reset() { fromY = Integer.MIN_VALUE; }

    /**
     * The row to draw this frame for a toast spanning {@code [left, right)} whose resting row is now
     * {@code target}, given the plates on screen.
     */
    int restY(int target, int frame, int left, int right, List<TransportHud.Bounds> plates) {
        if (fromY == Integer.MIN_VALUE) { fromY = toY = target; startFrame = frame - EASE_FRAMES; }
        int current = shownY(frame);
        if (target != toY) {
            boolean clear = pathClear(current, target, left, right, plates);
            fromY = clear ? current : target;
            toY = target;
            startFrame = frame;
            current = shownY(frame);
        }
        return current;
    }

    private int shownY(int frame) {
        float t = Math.clamp((frame - startFrame) / (float) EASE_FRAMES, 0f, 1f);
        float eased = 1f - (1f - t) * (1f - t);
        return Math.round(fromY + (toY - fromY) * eased);
    }

    /** True when the toast can travel every row from {@code from} to {@code to} without touching a plate. */
    static boolean pathClear(int from, int to, int left, int right, List<TransportHud.Bounds> plates) {
        int step = Integer.signum(to - from);
        for (int y = from; ; y += step) {
            for (TransportHud.Bounds plate : plates) {
                boolean across = left < plate.right() + 3 && plate.x() < right + 3;
                boolean down = y < plate.bottom() + 2 && plate.y() < y + MenuChrome.HOLD_TOAST_H + 1;
                if (across && down) return false;
            }
            if (y == to || step == 0) return true;
        }
    }
}
