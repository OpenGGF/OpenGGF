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
 */
public final class EscapeToMasterTitleController {
    public static final String MESSAGE = "HOLD TO RETURN TO GAMES";
    public static final String LEAVING_MESSAGE = "RETURNING TO GAMES";
    /** How long the toast lingers after Escape is released. */
    public static final int PROMPT_MINIMUM_FRAMES = 120;
    public static final int HOLD_FRAMES = 60;
    /** Frames the toast takes to slide in, and to slide back out at the end of its linger. */
    public static final int SLIDE_FRAMES = 10;

    private final BooleanSupplier transitionBlocked;
    private final Runnable returnToMasterTitleStarter;

    private int promptFramesRemaining;
    private int heldFrames;
    private int shownFrames;
    private boolean transitionStarted;

    public EscapeToMasterTitleController(BooleanSupplier transitionBlocked, Runnable returnToMasterTitleStarter) {
        this.transitionBlocked = Objects.requireNonNull(transitionBlocked, "transitionBlocked");
        this.returnToMasterTitleStarter =
                Objects.requireNonNull(returnToMasterTitleStarter, "returnToMasterTitleStarter");
    }

    public void update(GameMode mode, InputHandler input) {
        if (!eligible(mode) || input == null) {
            reset();
            return;
        }

        boolean escapeDown = input.isKeyDown(GLFW_KEY_ESCAPE);
        boolean escapePressed = input.isKeyPressed(GLFW_KEY_ESCAPE);
        if (escapePressed) {
            promptFramesRemaining = PROMPT_MINIMUM_FRAMES;
        } else if (promptFramesRemaining > 0 && !escapeDown) {
            promptFramesRemaining--;
        }

        if (escapeDown) {
            heldFrames = Math.min(HOLD_FRAMES, heldFrames + 1);
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
        return promptFramesRemaining > 0 || heldFrames > 0 || transitionStarted;
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
