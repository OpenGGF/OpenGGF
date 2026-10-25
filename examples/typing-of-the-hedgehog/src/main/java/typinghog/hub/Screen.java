package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;

/**
 * One of the hub's screens. {@link #update} reads input and changes state at 60 ticks a
 * second; {@link #draw} only reads it (the host may skip or repeat draws).
 */
interface Screen {
    /** Called each time the screen becomes current. */
    default void open(HubScene hub) { }

    void update(HubScene hub);

    void draw(HubScene hub, SceneCanvas c);

    /** A stable name for tests and tools. */
    String name();
}
