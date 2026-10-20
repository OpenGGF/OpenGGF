package sonicbros.sim;

/**
 * One fighter's controls for one frame: what is held and what went down this frame. Humans,
 * the CPU and tests all drive a fighter through these bits, so the simulation never knows who
 * is playing.
 */
public record Pad(int held, int pressed) {
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int UP = 4;
    public static final int DOWN = 8;
    public static final int ATTACK = 16;
    public static final int SPECIAL = 32;
    public static final int JUMP = 64;
    public static final int SHIELD = 128;
    public static final int START = 256;
    public static final int DIRECTIONS = LEFT | RIGHT | UP | DOWN;

    public static Pad none() { return new Pad(0, 0); }

    public boolean down(int bits) { return (held & bits) != 0; }

    public boolean hit(int bits) { return (pressed & bits) != 0; }

    /** -1, 0 or 1 for the horizontal direction held. */
    public int x() { return (down(RIGHT) ? 1 : 0) - (down(LEFT) ? 1 : 0); }

    /** -1 (up), 0 or 1 (down). */
    public int y() { return (down(DOWN) ? 1 : 0) - (down(UP) ? 1 : 0); }

    /** The pad for the next frame when {@code next} bits are held: presses are the new bits. */
    public Pad then(int next) { return new Pad(next, next & ~held); }
}
