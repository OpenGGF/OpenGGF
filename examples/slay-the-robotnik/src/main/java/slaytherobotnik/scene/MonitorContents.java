package slaytherobotnik.scene;

/**
 * A broken monitor's icon and explosion, as {@code Obj_MonitorBreak} spawns them. The icon
 * ({@code Obj_MonitorContents}) leaves the monitor at y_vel -$300, slowing by $18 a frame until it
 * stops (loc_1D7CE / sub_1D820), gives its power-up there ({@link #effectNow}), stays 30 frames
 * and goes. The explosion ({@code Obj_Explosion} from loc_1E61A, which plays sfx_Break) shows
 * Map_Explosion frame 0 for 4 frames and frames 1-4 for 8 each.
 */
final class MonitorContents {
    /** Icon launch speed and its slowing (loc_1D7CE, sub_1D820). */
    private static final int LAUNCH = -0x300;
    private static final int SLOWING = 0x18;
    /** loc_1D850: anim_frame_timer = 30-1 after the power-up; the icon goes when it runs out. */
    private static final int HOLD = 30 - 1;

    private int y;
    private int yVel = LAUNCH;
    private boolean risen;
    private boolean effectNow;
    private int hold = -1;
    private boolean gone;
    private int explosionFrame;
    private int explosionTimer = 3;
    private int age;

    /** One frame. */
    void tick() {
        effectNow = false;
        age++;
        if (explosionFrame < 5) {
            if (--explosionTimer < 0) {
                explosionTimer = 7;
                explosionFrame++;
            }
        }
        if (gone) {
            return;
        }
        if (!risen) {
            if (yVel < 0) {
                y += yVel;
                yVel += SLOWING;
                return;
            }
            risen = true;
            effectNow = true;
            hold = HOLD;
            return;
        }
        if (--hold < 0) {
            gone = true;
        }
    }

    /** How far the icon has risen above the monitor's centre, in pixels (negative is up). */
    float iconOffset() {
        return y / 256f;
    }

    /** True on the frame the icon stops and the monitor's power-up is given. */
    boolean effectNow() {
        return effectNow;
    }

    /** True once the power-up has been given. */
    boolean risen() {
        return risen;
    }

    /** True once the icon has gone. */
    boolean gone() {
        return gone;
    }

    /** The Map_Explosion frame showing, or -1 once the explosion has finished. */
    int explosionFrame() {
        return explosionFrame < 5 ? explosionFrame : -1;
    }

    /** Frames since the monitor broke. */
    int age() {
        return age;
    }
}
