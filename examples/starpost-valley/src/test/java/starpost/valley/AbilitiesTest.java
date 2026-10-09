package starpost.valley;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The farmers' second moves on the ported controller, against a flat floor with one tall block. */
class AbilitiesTest {
    private static final int FLOOR = 200;
    private static final int WALL_X0 = 300;
    private static final int WALL_X1 = 400;
    private static final int WALL_TOP = 40;

    /** A flat floor at 200 and a solid block from x 300 to 400 standing up to row 40. */
    private final Runner.Ground ground = new Runner.Ground() {
        @Override
        public boolean solid(int x, int y) {
            return y >= FLOOR || x >= WALL_X0 && x < WALL_X1 && y >= WALL_TOP;
        }

        @Override
        public int floorBelow(int x, int fromY) {
            return Math.max(fromY, x >= WALL_X0 && x < WALL_X1 ? WALL_TOP : FLOOR);
        }

        @Override
        public int left() {
            return 0;
        }

        @Override
        public int right() {
            return 1000;
        }
    };

    private Runner runner(String character) {
        Runner runner = new Runner(200, FLOOR);
        runner.character = character;
        return runner;
    }

    /** Jumps, then presses jump again in the air and holds it (and right) for {@code frames}. */
    private int jumpAndHold(Runner r, int frames) {
        int tick = 0;
        r.step(ground, false, true, false, true, true, tick++);
        for (int i = 0; i < 6; i++) {
            r.step(ground, false, true, false, false, false, tick++);
        }
        r.step(ground, false, true, false, true, true, tick++);
        for (int i = 0; i < frames && !r.climbing; i++) {
            r.step(ground, false, true, false, false, true, tick++);
        }
        return tick;
    }

    @Test
    void knucklesGlidesIntoAWallClingsClimbsAndPullsHimselfOntoTheTop() {
        Runner knuckles = runner("knuckles");
        int tick = jumpAndHold(knuckles, 200);
        assertTrue(knuckles.climbing, "the glide grabbed the wall");
        assertFalse(knuckles.gliding);
        assertTrue(knuckles.x + Runner.HALF_WIDTH <= WALL_X0 + 1, "he clings to its face: " + knuckles.x);
        float clungAt = knuckles.y;
        knuckles.climbUp = true;
        for (int i = 0; i < 10; i++) {
            knuckles.step(ground, false, false, false, false, false, tick++);
        }
        assertEquals(clungAt - 10, knuckles.y, 0.01f, "up climbs a pixel a frame");
        for (int i = 0; i < 200 && knuckles.climbing; i++) {
            knuckles.step(ground, false, false, false, false, false, tick++);
        }
        assertFalse(knuckles.climbing);
        assertTrue(knuckles.onGround, "over the top he stands on the ledge");
        assertEquals(WALL_TOP, knuckles.y, 0.01f);
        assertTrue(knuckles.x > WALL_X0);
    }

    @Test
    void jumpKicksKnucklesOffTheWall() {
        Runner knuckles = runner("knuckles");
        int tick = jumpAndHold(knuckles, 200);
        assertTrue(knuckles.climbing);
        knuckles.step(ground, false, false, false, true, true, tick);
        assertFalse(knuckles.climbing);
        assertTrue(knuckles.speed < 0 && knuckles.ySpeed < 0, "away from the wall and up");
        assertTrue(knuckles.facingLeft);
    }

    @Test
    void sonicNeitherGlidesNorClimbs() {
        Runner sonic = runner("sonic");
        jumpAndHold(sonic, 60);
        assertFalse(sonic.gliding);
        assertFalse(sonic.climbing);
        assertFalse(sonic.flying);
    }

    @Test
    void tailsFliesUntilTiredThenSinks() {
        Runner tails = runner("tails");
        int tick = 0;
        tails.step(ground, false, false, false, true, true, tick++);
        for (int i = 0; i < 6; i++) {
            tails.step(ground, false, false, false, false, false, tick++);
        }
        tails.step(ground, false, false, false, true, true, tick++);
        assertTrue(tails.flying);
        float highest = tails.y;
        // Flapping every 16 frames keeps him up while the timer runs.
        for (int i = 0; i < Runner.FLY_TIME - 10; i++) {
            tails.step(ground, false, false, false, i % 16 == 0, true, tick++);
            highest = Math.min(highest, tails.y);
        }
        assertTrue(tails.flying && !tails.onGround, "still flying near the end of the timer");
        for (int i = 0; i < 400 && !tails.onGround; i++) {
            tails.step(ground, false, false, false, i % 16 == 0, true, tick++);
        }
        assertTrue(tails.onGround, "tired, he comes down whatever is pressed");
        assertFalse(tails.flying);
    }
}
