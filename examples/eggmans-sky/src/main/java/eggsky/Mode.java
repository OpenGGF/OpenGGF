package eggsky;

import com.openggf.mods.scene.SceneCanvas;

/** One screen of the game (title, surface, space, station, galaxy map, a menu...). */
public interface Mode {
    default void enter(Game g) {
    }

    void update(Game g);

    void draw(Game g, SceneCanvas c);

    default void exit(Game g) {
    }

    /** Whether gameplay time (vitals, day cycle) runs while this mode is on top. */
    default boolean live() {
        return true;
    }
}
