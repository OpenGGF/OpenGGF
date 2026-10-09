package starpost.art;

import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * A character's animation, stepped as Sonic_Animate does: the script's frames in order, each held
 * for a delay the caller sets (speed-driven for walking and rolling).
 */
public final class Anim {
    // S3K player animation numbers (AniSonic / AniTails / AniKnuckles share them).
    public static final int WALK = 0x00;
    public static final int RUN = 0x01;
    public static final int ROLL = 0x02;
    public static final int PUSH = 0x04;
    public static final int WAIT = 0x05;
    public static final int LOOK_UP = 0x07;
    public static final int DUCK = 0x08;
    public static final int SPINDASH = 0x09;
    public static final int SPRING = 0x10;
    public static final int HURT = 0x1A;

    private int id = WAIT;
    private int cursor;
    private int timer;
    private int delay;

    public int id() {
        return id;
    }

    /** Switches script (restarting it) or keeps the current one with a new delay. */
    public void set(int next, int delay) {
        this.delay = delay;
        if (next != id) {
            id = next;
            cursor = 0;
            timer = delay;
        }
    }

    public void tick() {
        if (--timer < 0) {
            timer = delay;
            cursor++;
        }
    }

    /**
     * Tails's twin tails, drawn behind him at his body's origin: the tail object shares his
     * position in the ROM. Idle swish frames $22-$26 every 8 ticks, rolling $05-$08 every 3,
     * pushing $1E-$21 every 10 (Obj_Tails_Tail_AniSelection); none while walking or running.
     */
    public static void drawTails(com.openggf.mods.scene.SceneCanvas canvas, SceneSpriteSet tails, int anim, long ticks,
            float x, float originY, com.openggf.mods.scene.SceneDraw style) {
        if (tails == null) {
            return;
        }
        int frame = switch (anim) {
            case WAIT, DUCK, LOOK_UP -> 0x22 + (int) (ticks / 8 % 5);
            case ROLL, SPINDASH -> 5 + (int) (ticks / 3 % 4);
            case PUSH -> 0x1E + (int) (ticks / 10 % 4);
            default -> -1;
        };
        if (frame >= 0 && frame < tails.frameCount()) {
            canvas.draw(tails.frame(frame), x, originY, style);
        }
    }

    public SceneSprite pose(SceneSpriteSet set) {
        int[] frames = set.animationFrames(id);
        if (frames == null || frames.length == 0) {
            return set.frame(0);
        }
        return set.frame(frames[cursor % frames.length]);
    }
}
