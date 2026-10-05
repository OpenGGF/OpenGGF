package slaytherobotnik.ui;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneMouse;

/**
 * One tick of player input, gathered from the gamepad, the keyboard and the mouse into the
 * few verbs the game needs. Directions repeat while held, like a console menu.
 *
 * <p>Pad mapping (Genesis 3-button): A or C confirms, B goes back, Start ends the turn.
 * Keyboard: arrows move, Enter/Space confirm, Backspace/Escape go back, E ends the turn,
 * D opens the deck, M the map, 1-5 use potion slots. The mouse hovers and clicks.
 */
public final class Controls {
    // Direction bits in PlayerInputState masks (AbstractPlayableSprite.INPUT_*).
    private static final int UP = 0x01;
    private static final int DOWN = 0x02;
    private static final int LEFT = 0x04;
    private static final int RIGHT = 0x08;
    // Action bits (InputActionMasks.ACTION_*).
    private static final int ACTION_A = 0x01;
    private static final int ACTION_B = 0x02;
    private static final int ACTION_C = 0x04;
    // GLFW key codes.
    private static final int KEY_ENTER = 257;
    private static final int KEY_SPACE = 32;
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_E = 69;
    private static final int KEY_D = 68;
    private static final int KEY_M = 77;
    private static final int KEY_1 = 49;
    private static final int REPEAT_DELAY = 16;
    private static final int REPEAT_RATE = 4;

    private int heldTicks;
    private int lastDirections;

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
        LogicalInputSnapshot in = ctx.input();
        int held = in.player1().heldMask() & (UP | DOWN | LEFT | RIGHT);
        int pressed = in.player1().pressedMask() & (UP | DOWN | LEFT | RIGHT);
        int repeat = 0;
        if (held != 0 && held == lastDirections) {
            heldTicks++;
            if (heldTicks >= REPEAT_DELAY && (heldTicks - REPEAT_DELAY) % REPEAT_RATE == 0) {
                repeat = held;
            }
        } else {
            heldTicks = 0;
        }
        lastDirections = held;
        int dirs = pressed | repeat;
        up = (dirs & UP) != 0;
        down = (dirs & DOWN) != 0;
        left = (dirs & LEFT) != 0;
        right = (dirs & RIGHT) != 0;
        int actions = in.player1().actionPressedMask();
        accept = (actions & (ACTION_A | ACTION_C)) != 0 || ctx.keyPressed(KEY_ENTER) || ctx.keyPressed(KEY_SPACE);
        back = (actions & ACTION_B) != 0 || ctx.keyPressed(KEY_BACKSPACE) || ctx.keyPressed(KEY_ESCAPE);
        endTurn = in.player1().startPressed() || ctx.keyPressed(KEY_E);
        deck = ctx.keyPressed(KEY_D);
        map = ctx.keyPressed(KEY_M);
        potionKey = -1;
        for (int i = 0; i < 5; i++) {
            if (ctx.keyPressed(KEY_1 + i)) {
                potionKey = i;
            }
        }
        mouse = ctx.mouse();
        if (mouse.moved() || mouse.leftPressed() || mouse.rightPressed() || mouse.wheel() != 0) {
            usingMouse = true;
        }
        if (dirs != 0 || accept || back) {
            usingMouse = false;
        }
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
