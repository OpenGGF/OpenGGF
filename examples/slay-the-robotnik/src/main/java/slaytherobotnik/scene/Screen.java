package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;

/** One full-screen state of the game (title, map, a fight...). The {@link Shell} runs the current one. */
public interface Screen {
    /** Called when the screen becomes current (after the fade in starts). */
    default void enter(Shell shell) {
    }

    /** One 60 Hz tick of logic and input. */
    void update(Shell shell);

    /** Draws the screen; must not change game state. */
    void draw(Shell shell, SceneCanvas canvas);
}
