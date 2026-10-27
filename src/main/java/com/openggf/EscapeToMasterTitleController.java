package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.MenuChrome;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * Hold Escape to leave a running game for the master title. A tap shows the hold
 * toast so the shortcut is discoverable; holding fills it, and a full hold fades out
 * to the game shelf. The master title owns its own Escape (Back, then the Quit
 * dialog), so this controller is inactive there.
 *
 * <p>Mod scenes and standalone mod games read a short Escape press themselves (often to
 * open their own pause menu), so while a mod owns the screen a tap shows nothing: the
 * toast appears only once the key has been held for {@link #MOD_TAP_FRAMES}. The full
 * hold still takes {@link #HOLD_FRAMES} from the press.
 */
public final class EscapeToMasterTitleController {
    public static final String MESSAGE = "Hold to return to games";
    public static final String LEAVING_MESSAGE = "Returning to games";
    /** How long the toast lingers after Escape is released. */
    public static final int PROMPT_MINIMUM_FRAMES = 120;
    public static final int HOLD_FRAMES = 60;
    /** Frames the toast takes to slide in, and to slide back out at the end of its linger. */
    public static final int SLIDE_FRAMES = 10;
    /** How long Escape must be held before the toast covers a mod's own tap handling. */
    public static final int MOD_TAP_FRAMES = 15;

    private final BooleanSupplier transitionBlocked;
    private final Runnable returnToMasterTitleStarter;
    private final BooleanSupplier modOwnsEscapeTap;

    private int promptFramesRemaining;
    private boolean quietTap;
    private int heldFrames;
    private int shownFrames;
    private boolean transitionStarted;

    public EscapeToMasterTitleController(BooleanSupplier transitionBlocked, Runnable returnToMasterTitleStarter) {
        this(transitionBlocked, returnToMasterTitleStarter, () -> false);
    }

    public EscapeToMasterTitleController(BooleanSupplier transitionBlocked, Runnable returnToMasterTitleStarter,
                                         BooleanSupplier modOwnsEscapeTap) {
        this.transitionBlocked = Objects.requireNonNull(transitionBlocked, "transitionBlocked");
        this.returnToMasterTitleStarter =
                Objects.requireNonNull(returnToMasterTitleStarter, "returnToMasterTitleStarter");
        this.modOwnsEscapeTap = Objects.requireNonNull(modOwnsEscapeTap, "modOwnsEscapeTap");
    }

    public void update(GameMode mode, InputHandler input) {
        if (!eligible(mode) || input == null) {
            reset();
            return;
        }

        boolean escapeDown = input.isKeyDown(GLFW_KEY_ESCAPE);
        boolean escapePressed = input.isKeyPressed(GLFW_KEY_ESCAPE);
        quietTap = modOwnsEscapeTap.getAsBoolean();
        if (escapePressed && !quietTap) {
            promptFramesRemaining = PROMPT_MINIMUM_FRAMES;
        } else if (promptFramesRemaining > 0 && !escapeDown) {
            promptFramesRemaining--;
        }

        if (escapeDown) {
            heldFrames = Math.min(HOLD_FRAMES, heldFrames + 1);
            // Once a held key is clearly a hold, the toast lingers after release as it does for a tap.
            if (quietTap && heldFrames >= MOD_TAP_FRAMES) promptFramesRemaining = PROMPT_MINIMUM_FRAMES;
        } else if (!transitionStarted) {
            heldFrames = 0;
        }

        if (!transitionStarted && heldFrames >= HOLD_FRAMES && !transitionBlocked.getAsBoolean()) {
            transitionStarted = true;
            returnToMasterTitleStarter.run();
        }
        shownFrames = visible() ? shownFrames + 1 : 0;
    }

    public boolean visible() {
        return promptFramesRemaining > 0 || heldFrames > (quietTap ? MOD_TAP_FRAMES - 1 : 0) || transitionStarted;
    }

    public String message() {
        return transitionStarted ? LEAVING_MESSAGE : MESSAGE;
    }

    public double progress() {
        return Math.min(1.0, heldFrames / (double) HOLD_FRAMES);
    }

    /**
     * How far the toast has slid onto the screen, 0..1: eased in when it appears,
     * and eased back out over the last frames of its linger once Escape is released.
     */
    public float presence() {
        if (!visible()) return 0f;
        float in = MenuChrome.easeOut(shownFrames / (float) SLIDE_FRAMES);
        if (heldFrames > 0 || transitionStarted) return in;
        return Math.min(in, MenuChrome.easeOut(promptFramesRemaining / (float) SLIDE_FRAMES));
    }

    /** Frames the toast has been on screen; drives its focus pulse. */
    public int shownFrames() {
        return shownFrames;
    }

    boolean transitionStarted() {
        return transitionStarted;
    }

    public void reset() {
        quietTap = false;
        promptFramesRemaining = 0;
        heldFrames = 0;
        shownFrames = 0;
        transitionStarted = false;
    }

    private static boolean eligible(GameMode mode) {
        return mode != null
                && mode != GameMode.LEGAL_DISCLAIMER
                && mode != GameMode.EDITOR
                && mode != GameMode.MASTER_TITLE_SCREEN;
    }
}
