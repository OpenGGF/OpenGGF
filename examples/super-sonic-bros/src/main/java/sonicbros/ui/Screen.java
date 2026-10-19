package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;

/** One screen of the game: title, menus, select screens, a battle, results. */
public interface Screen {
    /** Called when the screen becomes current (after the fade). */
    default void enter() { }

    void update();

    void draw(SceneCanvas c);

    /** A short name for tests and debugging. */
    String name();
}
