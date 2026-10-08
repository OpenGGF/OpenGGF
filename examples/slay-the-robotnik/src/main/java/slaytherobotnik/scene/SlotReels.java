package slaytherobotnik.scene;

/**
 * The Slot Machine bonus stage's three reels, following the stage's reel cycle as the engine
 * models it ({@code S3kSlotOptionCycleSystem}) over the ROM's own reel strips. A reel word's high
 * byte is the position in its strip of eight faces and its low byte the row within a face, eight
 * per pixel row; a reel shows the 32 rows from its word down. Words fall as reels turn, so the
 * faces roll downwards. The machine moves one reel a frame, A, B then C.
 *
 * <p>A pull gives each reel a speed of $30 give or take four, runs two rounds, adds $30, then
 * counts $0C-$1B rounds before reel A starts looking for its target; B and C wait for the reel
 * before them. A reel that shows its target near the window's middle slows from $60 (by $0C a
 * turn down to $18, then by 2 while in a face's top half) until its target is about to come
 * round, snaps two rows short of it and creeps back to rest.
 */
final class SlotReels {
    private static final int SEARCH = 0;
    private static final int DECELERATE = 4;
    private static final int SETTLE = 8;
    private static final int LOCKED = 0x0C;

    private final int[][] strips;
    private final int[] words = new int[3];
    private final int[] speeds = new int[3];
    private final int[] stages = new int[3];
    /** The machine's reel pointer: 0, 4 or 8 for reels A, B, C. */
    private int active;
    private int countdown;
    private int target = -1;
    private boolean spinningUp;
    private boolean running;
    /** The pull's frame counter, which also sets how many rounds pass before reel A may stop. */
    private int seed;
    /** Frames since the last pull, and since the reels stopped; -1 before either. */
    private int sincePull = -1;
    private int sinceStop = -1;

    /** Reels at rest where {@code seed} puts them, as the stage's start does. */
    SlotReels(int[][] strips, int seed) {
        this.strips = strips;
        int b = rotateRight8(seed, 3);
        words[0] = ((seed & 0xFF) << 8) | 0x08;
        words[1] = (b << 8) | 0x08;
        words[2] = (rotateRight8(b, 3) << 8) | 0x08;
    }

    /** Spins all three reels to land on {@code face}; {@code seed} varies their speeds and timing. */
    void pull(int face, int seed) {
        target = face;
        speeds[0] = ((seed & 7) - 4 + 0x30) & 0xFF;
        speeds[1] = ((rotateRight8(seed, 4) & 7) - 4 + 0x30) & 0xFF;
        speeds[2] = (((seed >>> 8) & 7) - 4 + 0x30) & 0xFF;
        stages[0] = stages[1] = stages[2] = SEARCH;
        countdown = 2;
        active = 0;
        spinningUp = true;
        running = true;
        this.seed = seed;
        sincePull = 0;
        sinceStop = -1;
    }

    int sincePull() {
        return sincePull;
    }

    int sinceStop() {
        return sinceStop;
    }

    boolean running() {
        return running;
    }

    /** One frame of the machine. */
    void tick() {
        if (sincePull >= 0) {
            sincePull++;
        }
        if (!running) {
            if (sinceStop >= 0) {
                sinceStop++;
            }
            return;
        }
        advance();
        if (spinningUp) {
            if (countdown == 0) {
                for (int i = 0; i < 3; i++) {
                    speeds[i] = (speeds[i] + 0x30) & 0xFF;
                }
                countdown = 0x0C + (seed & 0x0F);
                spinningUp = false;
            }
            return;
        }
        if (stages[0] == LOCKED && stages[1] == LOCKED && stages[2] == LOCKED) {
            running = false;
            sinceStop = 0;
            return;
        }
        lock(active >> 2);
    }

    /** The face at the top of reel {@code reel}'s window. */
    int face(int reel) {
        return strips[reel][(words[reel] >>> 8) & 7];
    }

    /** The face below {@link #face}, coming into the window's bottom. */
    int nextFace(int reel) {
        return strips[reel][((words[reel] >>> 8) + 1) & 7];
    }

    /** True once reel {@code reel} has come to rest on the target. */
    boolean locked(int reel) {
        return stages[reel] == LOCKED;
    }

    /** Rows of the top face above the window. */
    int row(int reel) {
        return (words[reel] & 0xF8) >> 3;
    }

    private void advance() {
        int reel = active >> 2;
        words[reel] = (words[reel] - (byte) speeds[reel]) & 0xFFFF;
        if (active == 8) {
            active = 0;
            countdown = (countdown - 1) & 0xFF;
        } else {
            active += 4;
        }
    }

    private void lock(int reel) {
        int[] strip = strips[reel];
        int word = words[reel];
        switch (stages[reel]) {
            case SEARCH -> {
                if (reel == 0 ? (byte) countdown >= 0 : stages[reel - 1] < SETTLE) {
                    return;
                }
                if (strip[((word - 0xA0) >>> 8) & 7] == target) {
                    stages[reel] = DECELERATE;
                    speeds[reel] = 0x60;
                }
            }
            case DECELERATE -> {
                if (strip[((word + 0xF0) & 0x700) >>> 8] != target) {
                    int speed = speeds[reel];
                    if (speed > 0x20) {
                        speed = (speed - 0x0C) & 0xFF;
                    }
                    if (speed > 0x18 && (word & 0xFF) <= 0x80) {
                        speed = (speed - 2) & 0xFF;
                    }
                    speeds[reel] = speed;
                    return;
                }
                words[reel] = (((word + 0x80) & 0x700) - 0x10) & 0xFFFF;
                speeds[reel] = 0xF8;
                stages[reel] = SETTLE;
            }
            case SETTLE -> {
                if ((word & 0xFF) == 0) {
                    speeds[reel] = 0;
                    stages[reel] = LOCKED;
                }
            }
            default -> {
            }
        }
    }

    private static int rotateRight8(int value, int bits) {
        int b = value & 0xFF;
        return ((b >>> bits) | (b << (8 - bits))) & 0xFF;
    }
}
