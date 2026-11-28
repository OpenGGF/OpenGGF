package com.openggf.game.rewind;

import com.openggf.game.presentation.TransportHud;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

/**
 * Native-pixel transport plate above the original game's lives counter.
 *
 * <p>A finite rewind budget replaces the frame counter with the seconds left and a 2 px meter
 * that drains with them; the plate turns amber at a quarter. A drained budget (at the automatic
 * stop and when rewind is pressed with nothing left) shows an amber skip-to-start glyph, an amber
 * frame and a dashed empty track, so empty is told by shape as well as colour. An unlimited
 * budget shows a full meter with an authored infinity mark in place of the seconds; while the
 * rewind key is held its edge line pulses slowly toward the status colour.
 *
 * <p>When a refill restores spent time (a newly reached checkpoint, act or zone boundary, or a
 * lost life with refill enabled), the plate appears on its normal status and the meter and the
 * seconds count up from the pre-refill amount to the maximum, hold full, then hide.
 *
 * <p>This adapter owns the budget-to-message policy in {@link #message};
 * {@link LiveRewindManager} owns the allowance and {@link TransportHud} owns the plate's
 * layout and drawing. UI conventions live in {@code .agents/skills/ui-design-language/SKILL.md}.
 */
final class LiveRewindHudOverlay {

    private static final int FRAMES_PER_SECOND = 60;

    /*
     * UI design constants owned by OpenGGF, not ROM timing: the refill fills for half a second and
     * holds full for one, long enough to read, never a flash; the unlimited edge pulse takes one
     * second per cycle and peaks at a low-contrast mix toward the status colour.
     */
    private static final int REFILL_FILL_DIVISOR = 2;
    private static final int REFILL_HOLD_SECONDS = 1;
    static final int PULSE_PEAK_PERCENT = 40;

    private LiveRewindHudOverlay() { }

    /** Frames over which the meter fills during a refill. */
    static int refillFillFrames(int framesPerSecond) {
        return Math.max(1, framesPerSecond / REFILL_FILL_DIVISOR);
    }

    /** Whole refill presentation: the fill, then the hold at full; the plate hides after it. */
    static int refillAnimationFrames(int framesPerSecond) {
        return refillFillFrames(framesPerSecond) + REFILL_HOLD_SECONDS * Math.max(1, framesPerSecond);
    }

    /** Edge mix toward the status colour at {@code pulseFrame} frames into a held unlimited rewind. */
    static int pulsePercent(int pulseFrame, int framesPerSecond) {
        if (pulseFrame < 0) return 0;
        int period = Math.max(1, framesPerSecond);
        double phase = (pulseFrame % period) / (double) period;
        // Starts at the resting edge and eases up and back down: a breath, not a blink.
        return (int) Math.round(PULSE_PEAK_PERCENT * (1 - Math.cos(2 * Math.PI * phase)) / 2);
    }

    /** {@code graphics} draws the backing plate; null (logic-only hosts) draws the text alone. */
    static void render(PixelFontTextRenderer text, GraphicsManager graphics, int viewportWidth,
                       Integer rewindFrame, LiveRewindManager.BudgetHudState budget) {
        TransportHud.Message message = message(rewindFrame, budget);
        if (message != null) {
            TransportHud.render(text, graphics, viewportWidth, TransportHud.Placement.ABOVE_LIVES, message);
        }
    }

    /** The plate for the eligible frame and budget, or null when neither should be shown. */
    static TransportHud.Message message(Integer rewindFrame, LiveRewindManager.BudgetHudState budget) {
        if (budget != null && budget.visible() && budget.unlimited()) {
            return new TransportHud.Message(TransportHud.Status.REWIND, "REW", "", "", 100, "",
                    TransportHud.ValueMark.INFINITY, pulsePercent(budget.pulseFrame(), budget.framesPerSecond()));
        }
        if (budget != null && budget.visible() && !budget.unlimited() && budget.maximumFrames() > 0) {
            int maximum = budget.maximumFrames();
            int remaining = budget.empty() ? 0 : Math.clamp(budget.remainingFrames(), 0, maximum);
            boolean refilling = budget.refillFrame() >= 0;
            if (refilling) {
                int from = Math.clamp(budget.refillFromFrames(), 0, maximum);
                int fill = refillFillFrames(budget.framesPerSecond());
                int elapsed = Math.min(budget.refillFrame(), fill);
                remaining = (int) (from + (long) (maximum - from) * elapsed / fill);
            }
            TransportHud.Status plateStatus;
            if (refilling) {
                // A restored allowance reads on the normal status, never the low-budget amber.
                plateStatus = TransportHud.Status.REWIND;
            } else if (remaining == 0) {
                plateStatus = TransportHud.Status.REWIND_EMPTY;
            } else if (remaining * 4L <= maximum) {
                plateStatus = TransportHud.Status.REWIND_LOW;
            } else {
                plateStatus = TransportHud.Status.REWIND;
            }

            // Round up so the meter and the counter reach zero together, at the stop.
            int progressPercent = (int) ((remaining * 100L + maximum - 1) / maximum);
            return new TransportHud.Message(
                    plateStatus,
                    "REW",
                    seconds(remaining, budget.framesPerSecond()),
                    "",
                    progressPercent,
                    seconds(maximum, budget.framesPerSecond()));
        }
        if (rewindFrame == null) {
            return null;
        }
        return new TransportHud.Message(TransportHud.Status.REWIND, "REW", "F:" + rewindFrame, "", -1);
    }

    /** Tenths of a second, rounded up: {@code 0.0s} appears only when nothing is left. */
    static String seconds(int frames) {
        return seconds(frames, FRAMES_PER_SECOND);
    }

    static String seconds(int frames, int framesPerSecond) {
        long tenths = (Math.max(0, frames) * 10L + framesPerSecond - 1) / framesPerSecond;
        return tenths / 10 + "." + tenths % 10 + "s";
    }
}
