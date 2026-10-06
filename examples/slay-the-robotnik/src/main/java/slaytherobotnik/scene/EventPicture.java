package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;

/**
 * One event's illustration in the event panel. Besides drawing itself, it plays what the event
 * asks it to show ({@code EventContext.illustrate(detail, then)}): {@link #show} starts it, the
 * event waits while {@link #busy}, and the picture keeps the result on screen afterwards. The
 * detail is a short word each event and its picture agree on (the slot machine's face, "3").
 * {@link EventPictures} makes the right one for an event's art key.
 */
abstract class EventPicture {
    /** The panel's size: pictures are drawn into a 126x146 window. */
    static final int WIDTH = 126;
    static final int HEIGHT = 146;

    /** Starts showing {@code detail}. Pictures that animate nothing ignore it. */
    void show(Shell shell, String detail) {
    }

    /** True while the event should wait for the picture to finish showing a detail. */
    boolean busy() {
        return false;
    }

    /** One frame (60 a second), for animation and sounds. */
    void tick(Shell shell) {
    }

    /** Draws the picture into the window at ({@code x}, {@code y}), {@code w} x {@code h}, already clipped. */
    abstract void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h);
}
