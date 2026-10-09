package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;

/**
 * One state of the game: the title, free play, a menu over free play, the night's tally.
 * The {@link Shell} runs the current screen and any overlays pushed on top of it; only the top
 * one receives input, and the clock stops while an overlay is open.
 */
public interface Screen {
    default void enter(Shell shell) {
    }

    void update(Shell shell);

    /** Draws the screen; must not change game state (draw may be skipped or repeated). */
    void draw(Shell shell, SceneCanvas canvas);

    /** Whether the screens underneath should still be drawn (menus over gameplay). */
    default boolean overlay() {
        return false;
    }
}
