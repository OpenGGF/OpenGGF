package com.openggf.mods.scene;

/**
 * Player 1's controller buttons for {@link SceneContext#buttonDown}, {@link SceneContext#buttonPressed}
 * and {@link SceneContext#buttonRepeated}: the Mega Drive pad's own bit layout ({@code SACBRLDU}),
 * read from the gamepad and the keyboard keys the player mapped to it. Combine with {@code |} to
 * ask about several at once ("any of these").
 *
 * <p>Convention: by Genesis custom A or C (or Start) confirms and B cancels. Scenes follow it:
 * in a scene, {@code ctx.input().menuAccept()} is A, C or Start and {@code menuBack()} is B, so
 * the two never fire together. (The engine's own menus, outside scenes, use C as back.)
 *
 * <pre>{@code
 * if (ctx.buttonRepeated(SceneButtons.DOWN)) cursor++;          // repeats while held, like a menu
 * if (ctx.buttonPressed(SceneButtons.A | SceneButtons.C)) choose();
 * if (ctx.buttonPressed(SceneButtons.B)) goBack();
 * }</pre>
 */
@com.openggf.game.ModApi
public final class SceneButtons {
    public static final int UP = 0x01;
    public static final int DOWN = 0x02;
    public static final int LEFT = 0x04;
    public static final int RIGHT = 0x08;
    public static final int B = 0x10;
    public static final int C = 0x20;
    public static final int A = 0x40;
    public static final int START = 0x80;
    /** The four directions. */
    public static final int DIRECTIONS = UP | DOWN | LEFT | RIGHT;
    /** A, B and C. */
    public static final int ACTIONS = A | B | C;

    private SceneButtons() {
    }
}
