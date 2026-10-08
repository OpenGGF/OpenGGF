package eggsky.core;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

/**
 * The few verbs the game uses, read once per update from the pad (and the keys the player
 * mapped to it), fixed keyboard keys and the mouse. Stock keyboard setups map only A (Space),
 * so every action also has its own key:
 *
 * <pre>
 * fly         arrows / WASD / d-pad
 * beam        Space, Z, pad A, left mouse (aims at the pointer)
 * scan        X, pad B, right mouse  (tap: pulse; hold: analysis visor)
 * boost       C, Shift, pad C        (with up: launch)
 * menu        Enter, Tab, I, pad Start
 * back        Backspace, X, pad B
 * </pre>
 */
public final class Controls {
    public boolean left;
    public boolean right;
    public boolean up;
    public boolean down;
    public boolean beam;
    public boolean beamPressed;
    public boolean scan;
    public boolean scanPressed;
    public boolean scanReleased;
    public boolean boost;
    public boolean boostPressed;
    public boolean menuPressed;
    public boolean backPressed;
    public boolean confirmPressed;
    public boolean leftPressed;
    public boolean rightPressed;
    public boolean upPressed;
    public boolean downPressed;
    public boolean leftRepeat;
    public boolean rightRepeat;
    public boolean upRepeat;
    public boolean downRepeat;
    public boolean mouseAim;
    public int mouseX;
    public int mouseY;
    public boolean mouseLeftPressed;
    public boolean mouseRightPressed;
    public boolean mouseMoved;
    public int wheel;
    /** Any key or button went down this update (for "press any key" screens). */
    public boolean anyPressed;
    private boolean lastScan;
    private boolean lastBeam;
    private boolean lastBoost;

    public void read(SceneContext ctx) {
        left = ctx.buttonDown(SceneButtons.LEFT) || ctx.keyDown(SceneKeys.A) || ctx.keyDown(SceneKeys.LEFT);
        right = ctx.buttonDown(SceneButtons.RIGHT) || ctx.keyDown(SceneKeys.D) || ctx.keyDown(SceneKeys.RIGHT);
        up = ctx.buttonDown(SceneButtons.UP) || ctx.keyDown(SceneKeys.W) || ctx.keyDown(SceneKeys.UP);
        down = ctx.buttonDown(SceneButtons.DOWN) || ctx.keyDown(SceneKeys.S) || ctx.keyDown(SceneKeys.DOWN);
        leftPressed = ctx.buttonPressed(SceneButtons.LEFT) || ctx.keyPressed(SceneKeys.A)
                || ctx.keyPressed(SceneKeys.LEFT);
        rightPressed = ctx.buttonPressed(SceneButtons.RIGHT) || ctx.keyPressed(SceneKeys.D)
                || ctx.keyPressed(SceneKeys.RIGHT);
        upPressed = ctx.buttonPressed(SceneButtons.UP) || ctx.keyPressed(SceneKeys.W) || ctx.keyPressed(SceneKeys.UP);
        downPressed = ctx.buttonPressed(SceneButtons.DOWN) || ctx.keyPressed(SceneKeys.S)
                || ctx.keyPressed(SceneKeys.DOWN);
        leftRepeat = ctx.buttonRepeated(SceneButtons.LEFT) || leftPressed;
        rightRepeat = ctx.buttonRepeated(SceneButtons.RIGHT) || rightPressed;
        upRepeat = ctx.buttonRepeated(SceneButtons.UP) || upPressed;
        downRepeat = ctx.buttonRepeated(SceneButtons.DOWN) || downPressed;
        var mouse = ctx.mouse();
        mouseX = mouse.x();
        mouseY = mouse.y();
        mouseMoved = mouse.moved();
        mouseLeftPressed = mouse.leftPressed();
        mouseRightPressed = mouse.rightPressed();
        wheel = mouse.wheel();
        mouseAim = mouse.leftDown() && mouse.inside();
        boolean keyBeam = ctx.buttonDown(SceneButtons.A) || ctx.keyDown(SceneKeys.SPACE) || ctx.keyDown(SceneKeys.Z);
        beam = keyBeam || mouseAim;
        beamPressed = beam && !lastBeam;
        lastBeam = beam;
        scan = ctx.buttonDown(SceneButtons.B) || ctx.keyDown(SceneKeys.X) || (mouse.rightDown() && mouse.inside());
        scanPressed = scan && !lastScan;
        scanReleased = !scan && lastScan;
        lastScan = scan;
        boost = ctx.buttonDown(SceneButtons.C) || ctx.keyDown(SceneKeys.C) || ctx.keyDown(SceneKeys.LEFT_SHIFT)
                || ctx.keyDown(SceneKeys.RIGHT_SHIFT);
        boostPressed = boost && !lastBoost;
        lastBoost = boost;
        menuPressed = ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.ENTER)
                || ctx.keyPressed(SceneKeys.KP_ENTER) || ctx.keyPressed(SceneKeys.TAB) || ctx.keyPressed(SceneKeys.I);
        backPressed = ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.BACKSPACE)
                || ctx.keyPressed(SceneKeys.X) || mouse.rightPressed();
        confirmPressed = ctx.buttonPressed(SceneButtons.A) || ctx.buttonPressed(SceneButtons.C)
                || ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.ENTER)
                || ctx.keyPressed(SceneKeys.KP_ENTER) || ctx.keyPressed(SceneKeys.SPACE)
                || ctx.keyPressed(SceneKeys.Z) || ctx.keyPressed(SceneKeys.C);
        anyPressed = confirmPressed || backPressed || menuPressed || mouseLeftPressed || leftPressed || rightPressed
                || upPressed || downPressed;
    }

    /** Horizontal stick: -1, 0 or 1. */
    public int dx() {
        return (right ? 1 : 0) - (left ? 1 : 0);
    }

    /** Vertical stick: -1 (up), 0 or 1 (down). */
    public int dy() {
        return (down ? 1 : 0) - (up ? 1 : 0);
    }
}
