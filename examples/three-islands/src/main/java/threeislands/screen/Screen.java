package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import threeislands.Game;

/** One full-screen state: updated 60 times a second, drawn once per presented frame. */
public interface Screen {
    void update(Game game);

    /** Must not change game state: frames may be skipped or repeated. */
    void draw(Game game, SceneCanvas canvas);

    /** A short name for tests and debugging. */
    String name();
}
