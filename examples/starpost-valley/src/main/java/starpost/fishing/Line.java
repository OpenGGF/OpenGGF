package starpost.fishing;

import com.openggf.mods.state.SnapshotRandom;

/**
 * A line in the water, engine-free: the cast's flight, the wait with its nibbles, the bite and
 * the short window to strike. The pond and the lake both drive one; positions are whatever
 * coordinates the view uses (the bobber flies from {@code from} to {@code to}).
 */
public final class Line {
    public static final int IDLE = 0;
    public static final int FLYING = 1;
    public static final int WAITING = 2;
    public static final int BITE = 3;
    /** The Bubble Bar is open. */
    public static final int FIGHT = 4;

    /** Events a step reports. */
    public static final int NONE = 0;
    public static final int SPLASH = 1;
    public static final int NIBBLE = 2;
    public static final int BITE_NOW = 3;
    public static final int MISSED = 4;

    public static final int FLY_TICKS = 22;
    /** Frames to strike once the bobber goes under (a little under a second). */
    public static final int BITE_WINDOW = 42;
    public static final int NIBBLE_TICKS = 10;

    public int state = IDLE;
    public int timer;
    public int age;
    public float fromX;
    public float fromY;
    public float toX;
    public float toY;
    /** The cast's depth, 0-100 (the pond is always 50). */
    public int depth;
    /** Ticks left of a nibble's dip. */
    public int nibble;
    private int level;

    /** Frames to wait for a bite: one and a half to six seconds, shorter for a skilled hand. */
    public static int waitTicks(int fishingLevel, SnapshotRandom rng) {
        return Math.max(45, 90 + rng.nextInt(270) - fishingLevel * 8);
    }

    public void cast(float fx, float fy, float tx, float ty, int depth, int fishingLevel) {
        state = FLYING;
        timer = FLY_TICKS;
        fromX = fx;
        fromY = fy;
        toX = tx;
        toY = ty;
        this.depth = Math.max(0, Math.min(100, depth));
        level = fishingLevel;
        nibble = 0;
        age = 0;
    }

    public void reelIn() {
        state = IDLE;
        nibble = 0;
    }

    public boolean out() {
        return state != IDLE;
    }

    /** The bobber's position now (x, y). */
    public float bobberX() {
        if (state == FLYING) {
            float t = 1 - timer / (float) FLY_TICKS;
            return fromX + (toX - fromX) * t;
        }
        return toX;
    }

    /** The bobber's height: an arc in flight, a gentle bob afloat, under during a bite. */
    public float bobberY() {
        if (state == FLYING) {
            float t = 1 - timer / (float) FLY_TICKS;
            return fromY + (toY - fromY) * t - (float) Math.sin(t * Math.PI) * 26;
        }
        if (state == BITE || state == FIGHT) {
            return toY + 3;
        }
        return toY + (nibble > 0 ? 2 : 0) + (float) Math.sin(age / 14.0) * 0.8f;
    }

    /** One frame. */
    public int step(SnapshotRandom rng) {
        age++;
        switch (state) {
            case FLYING -> {
                if (--timer <= 0) {
                    state = WAITING;
                    timer = waitTicks(level, rng);
                    return SPLASH;
                }
            }
            case WAITING -> {
                if (nibble > 0) {
                    nibble--;
                }
                if (--timer <= 0) {
                    state = BITE;
                    timer = BITE_WINDOW;
                    return BITE_NOW;
                }
                if (nibble == 0 && timer > 30 && rng.nextInt(150) == 0) {
                    nibble = NIBBLE_TICKS;
                    return NIBBLE;
                }
            }
            case BITE -> {
                if (--timer <= 0) {
                    state = WAITING;
                    timer = waitTicks(level, rng);
                    return MISSED;
                }
            }
            default -> {
            }
        }
        return NONE;
    }

    /** The farmer strikes: true when it was in time (the fight begins). */
    public boolean strike() {
        if (state == BITE) {
            state = FIGHT;
            return true;
        }
        return false;
    }
}
