package threeislands.view;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

/**
 * The few verbs the game uses, following the Genesis convention: A or C confirms, B goes back
 * (and is held to run in the field), Start opens the menu. The default keyboard maps only A
 * (Space) and Start (Backspace) to the pad, so Enter and Z also confirm, X and Escape go back,
 * Shift or X runs and M opens the menu. Cursor directions repeat when held.
 */
public final class Controls {
    private SceneContext ctx;

    public void bind(SceneContext context) {
        this.ctx = context;
    }

    public boolean up() { return ctx.buttonRepeated(SceneButtons.UP); }
    public boolean down() { return ctx.buttonRepeated(SceneButtons.DOWN); }
    public boolean left() { return ctx.buttonRepeated(SceneButtons.LEFT); }
    public boolean right() { return ctx.buttonRepeated(SceneButtons.RIGHT); }

    public boolean holdLeft() { return ctx.buttonDown(SceneButtons.LEFT); }
    public boolean holdRight() { return ctx.buttonDown(SceneButtons.RIGHT); }

    public boolean holdRun() {
        return ctx.buttonDown(SceneButtons.B) || ctx.keyDown(SceneKeys.LEFT_SHIFT) || ctx.keyDown(SceneKeys.X);
    }

    /** Held confirm, used to hurry battle animations and text. */
    public boolean holdAccept() {
        return ctx.buttonDown(SceneButtons.A | SceneButtons.C) || ctx.keyDown(SceneKeys.ENTER) || ctx.keyDown(SceneKeys.Z);
    }

    public boolean accept() {
        return ctx.buttonPressed(SceneButtons.A | SceneButtons.C) || ctx.keyPressed(SceneKeys.ENTER)
                || ctx.keyPressed(SceneKeys.Z);
    }

    public boolean back() {
        return ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.X) || ctx.keyPressed(SceneKeys.ESCAPE);
    }

    public boolean menu() {
        return ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.M);
    }

    /** Horizontal direction held: -1, 0 or 1. */
    public int horizontal() {
        return (holdRight() ? 1 : 0) - (holdLeft() ? 1 : 0);
    }
}
