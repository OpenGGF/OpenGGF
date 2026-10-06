package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;

/**
 * An event picture set on the act's real level ({@link EventArt#level}), which remembers where
 * the floor runs so its animations can stand things on it. Coordinates inside are relative to
 * the picture window.
 */
abstract class StagedPicture extends EventPicture {
    /** The floor row without a stage: 22 rows above the window's bottom, as the drawn outdoors has it. */
    static final int GROUND = HEIGHT - 22;

    private LevelStages.Placement placement;
    private boolean placed;

    /** Draws the act's level into the window and remembers its floor. */
    void drawLevel(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        placement = EventArt.level(shell, c, x, y, w, h, y + GROUND);
        placed = true;
    }

    /** The window row of the floor under window column {@code col}. */
    int floor(int col) {
        if (!placed || placement == null) {
            return GROUND;
        }
        return placement.feet(Math.max(0, Math.min(WIDTH - 1, col)));
    }
}
