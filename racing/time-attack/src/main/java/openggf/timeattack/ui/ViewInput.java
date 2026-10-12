package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

/**
 * One tick of menu input for the Time Attack views: repeated directions, confirm and back
 * (the scene convention: A, C, Start or Enter confirm; B or Escape go back), plus raw key state
 * for text entry. Views read this instead of the scene context so they can be driven in tests.
 */
public interface ViewInput {
    boolean up();

    boolean down();

    boolean left();

    boolean right();

    boolean accept();

    boolean back();

    /** True on the tick the key went down ({@link SceneKeys} code). */
    boolean keyPressed(int key);

    /** True while the key is held ({@link SceneKeys} code). */
    boolean keyDown(int key);

    /** This tick's input from a scene context. */
    static ViewInput of(SceneContext ctx) {
        return new ViewInput() {
            @Override public boolean up() { return ctx.buttonRepeated(SceneButtons.UP); }
            @Override public boolean down() { return ctx.buttonRepeated(SceneButtons.DOWN); }
            @Override public boolean left() { return ctx.buttonRepeated(SceneButtons.LEFT); }
            @Override public boolean right() { return ctx.buttonRepeated(SceneButtons.RIGHT); }
            @Override public boolean accept() {
                return ctx.buttonPressed(SceneButtons.START | SceneButtons.A | SceneButtons.C)
                        || ctx.keyPressed(SceneKeys.ENTER) || ctx.keyPressed(SceneKeys.KP_ENTER);
            }
            @Override public boolean back() {
                return ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.ESCAPE);
            }
            @Override public boolean keyPressed(int key) { return ctx.keyPressed(key); }
            @Override public boolean keyDown(int key) { return ctx.keyDown(key); }
        };
    }
}
