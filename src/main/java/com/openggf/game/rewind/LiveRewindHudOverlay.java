package com.openggf.game.rewind;

import com.openggf.game.presentation.TransportHud;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Native-pixel transport plate above the original game's lives counter.
 *
 * <p>A finite rewind budget replaces the frame counter with the seconds left and a 2 px meter
 * that drains with them; the plate turns amber at a quarter. A drained budget (at the automatic
 * stop and when rewind is pressed with nothing left) shows an amber skip-to-start glyph, an amber
 * frame and a dashed empty track, so empty is told by shape as well as colour. An unlimited
 * budget keeps the frame counter without a meter.
 *
 * <p>This adapter owns the budget-to-message policy in {@link #message};
 * {@link LiveRewindManager} owns the allowance and {@link TransportHud} owns the plate's
 * layout and drawing. UI conventions live in {@code .agents/skills/ui-design-language/SKILL.md}.
 */
final class LiveRewindHudOverlay {

    private static final int FRAMES_PER_SECOND = 60;

    private final Supplier<String> statusSupplier;
    private final Supplier<LiveRewindManager.BudgetHudState> budgetSupplier;

    LiveRewindHudOverlay(Supplier<String> statusSupplier) {
        this(statusSupplier, () -> null);
    }

    LiveRewindHudOverlay(Supplier<String> statusSupplier,
                         Supplier<LiveRewindManager.BudgetHudState> budgetSupplier) {
        this.statusSupplier = Objects.requireNonNull(statusSupplier, "statusSupplier");
        this.budgetSupplier = Objects.requireNonNull(budgetSupplier, "budgetSupplier");
    }

    /** {@code graphics} draws the backing plate; null (logic-only hosts) draws the text alone. */
    public void render(PixelFontTextRenderer text, GraphicsManager graphics, int viewportWidth) {
        TransportHud.Message message = message(statusSupplier.get(), budgetSupplier.get());
        if (message != null) {
            TransportHud.render(text, graphics, viewportWidth, TransportHud.Placement.ABOVE_LIVES, message);
        }
    }

    /** The plate for this status and budget, or null when nothing is shown. */
    static TransportHud.Message message(String status, LiveRewindManager.BudgetHudState budget) {
        if (budget != null && budget.visible() && !budget.unlimited() && budget.maximumFrames() > 0) {
            int maximum = budget.maximumFrames();
            int remaining = budget.empty() ? 0 : Math.clamp(budget.remainingFrames(), 0, maximum);
            TransportHud.Status plateStatus;
            if (remaining == 0) {
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
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.startsWith("REWIND ") ? "F:" + status.substring(7) : status;
        return new TransportHud.Message(TransportHud.Status.REWIND, "REW", value, "", -1);
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
