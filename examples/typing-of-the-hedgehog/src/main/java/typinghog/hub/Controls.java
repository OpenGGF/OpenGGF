package typinghog.hub;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

/**
 * The few verbs the hub's screens use, merged from the keyboard, the pad and typed text.
 *
 * <p>The hub claims the keyboard's text keys every tick ({@code captureKeyboardText}) so that
 * typing a menu word never fires a host shortcut. While claimed, keyboard bindings read as up
 * for the pad snapshot, so the arrows, Enter, Escape and Backspace are read here as keys (with
 * the engine menus' 24-then-every-4-tick repeat), and pads through the scene's buttons.
 */
final class Controls {
    private static final int REPEAT_DELAY = 24;
    private static final int REPEAT_EVERY = 4;
    private static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    private SceneContext ctx;
    private final int[] held = new int[4];
    private final boolean[] pulse = new boolean[4];
    private String typed = "";
    private boolean injectedAccept;

    /** Reads this tick's input; {@code injected} is debug text typed on the player's behalf. */
    void begin(SceneContext ctx, String injected, boolean acceptInjected) {
        this.ctx = ctx;
        ctx.captureKeyboardText();
        String text = ctx.typedText();
        typed = (text == null ? "" : text) + (injected == null ? "" : injected);
        injectedAccept = acceptInjected;
        for (int i = 0; i < 4; i++) {
            int key = switch (i) {
                case UP -> SceneKeys.UP;
                case DOWN -> SceneKeys.DOWN;
                case LEFT -> SceneKeys.LEFT;
                default -> SceneKeys.RIGHT;
            };
            boolean down = ctx.keyDown(key);
            held[i] = down ? held[i] + 1 : 0;
            boolean keyPulse = ctx.keyPressed(key)
                    || (held[i] > REPEAT_DELAY && (held[i] - REPEAT_DELAY) % REPEAT_EVERY == 0);
            int button = switch (i) {
                case UP -> SceneButtons.UP;
                case DOWN -> SceneButtons.DOWN;
                case LEFT -> SceneButtons.LEFT;
                default -> SceneButtons.RIGHT;
            };
            pulse[i] = keyPulse || ctx.buttonRepeated(button);
        }
    }

    boolean up() { return pulse[UP]; }
    boolean down() { return pulse[DOWN]; }
    boolean left() { return pulse[LEFT]; }
    boolean right() { return pulse[RIGHT]; }

    /** Enter (either), or A, C or Start on a pad. */
    boolean accept() {
        return injectedAccept || ctx.keyPressed(SceneKeys.ENTER) || ctx.keyPressed(SceneKeys.KP_ENTER)
                || ctx.input().menuAccept();
    }

    /** Escape, or B on a pad. */
    boolean back() {
        return ctx.keyPressed(SceneKeys.ESCAPE) || ctx.input().menuBack();
    }

    boolean backspace() { return ctx.keyPressed(SceneKeys.BACKSPACE); }

    boolean tab() { return ctx.keyPressed(SceneKeys.TAB); }

    /** The characters typed this tick. */
    String typed() { return typed; }

    /** Clears the tick's typed text once a screen change has used it. */
    void consumeTyped() { typed = ""; }

    /** True when the mouse moved or clicked over the rectangle this tick. */
    boolean hover(int x, int y, int w, int h) {
        var mouse = ctx.mouse();
        return mouse != null && mouse.moved() && mouse.over(x, y, w, h);
    }

    boolean click(int x, int y, int w, int h) {
        var mouse = ctx.mouse();
        return mouse != null && mouse.leftPressed() && mouse.over(x, y, w, h);
    }
}
