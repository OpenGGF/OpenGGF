package slaytherobotnik.ui;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneMouse;

/**
 * One tick of player input, gathered from the gamepad, the keyboard and the mouse into the
 * few verbs the game needs. Directions repeat while held, as the engine's own menus do
 * ({@code SceneContext.buttonRepeated}).
 *
 * <p>Pad mapping (Genesis 3-button): A or C confirms, B goes back (the Genesis convention mod
 * scenes follow), Start ends the turn. Keyboard: arrows move, Enter/Space confirm,
 * Backspace/Escape go back, E ends the turn, D opens the deck, M the map, 1-5 use potion slots.
 * The mouse hovers and clicks.
 */
public final class Controls {
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean accept;
    public boolean back;
    public boolean endTurn;
    public boolean deck;
    public boolean map;
    /** Potion slot hotkey 0-4, or -1. */
    public int potionKey = -1;
    public SceneMouse mouse = SceneMouse.none();
    /** True when the last thing the player touched was the mouse. */
    public boolean usingMouse;

    /** Reads this tick's input. */
    public void read(SceneContext ctx) {
        up = ctx.buttonRepeated(SceneButtons.UP);
        down = ctx.buttonRepeated(SceneButtons.DOWN);
        left = ctx.buttonRepeated(SceneButtons.LEFT);
        right = ctx.buttonRepeated(SceneButtons.RIGHT);
        accept = ctx.buttonPressed(SceneButtons.A | SceneButtons.C) || ctx.keyPressed(SceneKeys.ENTER)
                || ctx.keyPressed(SceneKeys.SPACE);
        back = ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.BACKSPACE)
                || ctx.keyPressed(SceneKeys.ESCAPE);
        endTurn = ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.E);
        deck = ctx.keyPressed(SceneKeys.D);
        map = ctx.keyPressed(SceneKeys.M);
        potionKey = -1;
        for (int i = 0; i < 5; i++) {
            if (ctx.keyPressed(SceneKeys.DIGIT_1 + i)) {
                potionKey = i;
            }
        }
        mouse = ctx.mouse();
        usingMouse = mouse.lastInputWasMouse();
    }

    public boolean anyDirection() {
        return up || down || left || right;
    }

    /** Clears everything (after a screen change, so one press doesn't act twice). */
    public void consume() {
        up = down = left = right = accept = back = endTurn = deck = map = false;
        potionKey = -1;
    }
}
