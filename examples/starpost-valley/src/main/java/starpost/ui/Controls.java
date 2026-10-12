package starpost.ui;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

/**
 * One tick's input, read once from the pad and keyboard so every screen sees the same values.
 * The keyboard's default pad mapping binds only A (Space) and Start (Backspace), so the common
 * actions also have keys of their own:
 * <ul>
 *   <li>jump / confirm: pad A or C, Space, Z, Enter (menus);</li>
 *   <li>act: pad B, X;</li>
 *   <li>back: pad B, X, Backspace (menus);</li>
 *   <li>menu: pad Start, Tab, I, Enter (free play);</li>
 *   <li>hotbar: Q / E (previous, next), digits 1-9 and 0.</li>
 * </ul>
 */
public final class Controls {
    public boolean left, right, up, down;
    public boolean leftPressed, rightPressed, upPressed, downPressed;
    public boolean jump, jumpHeld, act, confirm, back, menu;
    public boolean prevTool, nextTool;
    public int hotbarKey = -1;
    private boolean consumed;

    public void read(SceneContext ctx) {
        consumed = false;
        left = ctx.buttonDown(SceneButtons.LEFT);
        right = ctx.buttonDown(SceneButtons.RIGHT);
        up = ctx.buttonDown(SceneButtons.UP);
        down = ctx.buttonDown(SceneButtons.DOWN);
        leftPressed = ctx.buttonRepeated(SceneButtons.LEFT);
        rightPressed = ctx.buttonRepeated(SceneButtons.RIGHT);
        upPressed = ctx.buttonRepeated(SceneButtons.UP);
        downPressed = ctx.buttonRepeated(SceneButtons.DOWN);
        jump = ctx.buttonPressed(SceneButtons.A | SceneButtons.C) || ctx.keyPressed(SceneKeys.Z);
        jumpHeld = ctx.buttonDown(SceneButtons.A | SceneButtons.C) || ctx.keyDown(SceneKeys.Z);
        act = ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.X);
        confirm = jump || ctx.keyPressed(SceneKeys.ENTER) || ctx.keyPressed(SceneKeys.KP_ENTER);
        back = act || ctx.keyPressed(SceneKeys.BACKSPACE) && !ctx.buttonPressed(SceneButtons.START);
        menu = ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.TAB) || ctx.keyPressed(SceneKeys.I)
                || ctx.keyPressed(SceneKeys.ENTER);
        prevTool = ctx.keyPressed(SceneKeys.Q);
        nextTool = ctx.keyPressed(SceneKeys.E);
        hotbarKey = -1;
        for (int i = 0; i < 10; i++) {
            if (ctx.keyPressed(SceneKeys.DIGIT_1 + i)) {
                hotbarKey = i;
            }
        }
        if (ctx.keyPressed(SceneKeys.DIGIT_0)) {
            hotbarKey = 9;
        }
        int wheel = (int) Math.signum(ctx.mouse().wheel());
        if (wheel < 0) {
            nextTool = true;
        } else if (wheel > 0) {
            prevTool = true;
        }
    }

    /** Forgets this tick's presses (after a screen change, so one press does one thing). */
    public void consume() {
        consumed = true;
        leftPressed = rightPressed = upPressed = downPressed = false;
        jump = act = confirm = back = menu = prevTool = nextTool = false;
        hotbarKey = -1;
    }

    public boolean consumed() {
        return consumed;
    }
}
