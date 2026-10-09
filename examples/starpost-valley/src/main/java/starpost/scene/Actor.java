package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;

/**
 * Something that lives in the world besides the farmer: a villager, an animal, a pest, a
 * Flicky. Each actor belongs to one view. On the farm, {@link #x()} is the world x and
 * {@link #y()} the feet's screen row (actors are drawn back to front with the crops); in the
 * valley they are world coordinates of the feet.
 */
public interface Actor {
    int FARM = 0;
    int VALLEY = 1;

    int view();

    float x();

    float y();

    void update(Shell shell, PlayScreen play);

    /** Draws at the camera offset: screen x = x - cx; valley screen y = y - cy (cy is 0 on the farm). */
    void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint);

    /** Names and bubbles, drawn after everything else in the view so crops and weeds never cover them. */
    default void drawOver(Shell shell, SceneCanvas canvas, int cx, int cy) {
    }

    /** The action button pressed within {@link #reach()} pixels: true when the actor used it. */
    default boolean interact(Shell shell, PlayScreen play) {
        return false;
    }

    default float reach() {
        return 16;
    }
}
