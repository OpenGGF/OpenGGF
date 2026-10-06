package slaytherobotnik.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ported reel cycle lands every reel squarely on the face it was pulled for. */
class SlotReelsTest {
    /** byte_4C8CC and the two strips after it, as the ROM holds them. */
    private static final int[][] STRIPS = {
            {0, 1, 2, 5, 4, 6, 5, 3},
            {0, 1, 2, 5, 4, 6, 1, 3},
            {0, 1, 2, 5, 4, 6, 3, 5}};

    @Test
    void everyFaceOnEveryStripLandsForAnySeed() {
        int longest = 0;
        for (int face = 0; face <= 6; face++) {
            for (int seed = 0; seed < 512; seed += 7) {
                SlotReels reels = new SlotReels(STRIPS, seed * 13);
                reels.pull(face, seed);
                int frames = 0;
                while (reels.running() && frames < 2000) {
                    reels.tick();
                    frames++;
                }
                assertFalse(reels.running(), "face " + face + " seed " + seed + " never stopped");
                longest = Math.max(longest, frames);
                for (int reel = 0; reel < 3; reel++) {
                    assertEquals(face, reels.face(reel), "face " + face + " seed " + seed + " reel " + reel);
                    assertEquals(0, reels.row(reel), "reel " + reel + " rests on a whole face");
                }
            }
        }
        // A spin should feel like the stage's: a second or two, not an age.
        assertTrue(longest < 600, "longest spin " + longest + " frames");
    }

    @Test
    void reelsStopInOrderAThenBThenC() {
        for (int seed = 0; seed < 64; seed++) {
            SlotReels reels = new SlotReels(STRIPS, seed);
            reels.pull(seed % 7, seed * 31);
            int[] lockedAt = {-1, -1, -1};
            for (int frame = 0; reels.running() && frame < 2000; frame++) {
                reels.tick();
                for (int reel = 0; reel < 3; reel++) {
                    if (lockedAt[reel] < 0 && reels.locked(reel)) {
                        lockedAt[reel] = frame;
                    }
                }
            }
            assertTrue(lockedAt[0] >= 0 && lockedAt[0] < lockedAt[1] && lockedAt[1] < lockedAt[2],
                    "seed " + seed + " locked at " + java.util.Arrays.toString(lockedAt));
            assertTrue(reels.sinceStop() >= 0);
        }
    }
}
