package starpost.festivals;

import starpost.art.Art;
import starpost.valley.Runner;

/**
 * The Great Valley Race's circuit: Green Hill act 1's own blocks laid end to end with their own
 * collision, chosen so each one's floor meets the next (flats at row 192, the climb to the
 * plateau at 128 and back down, the totem ledge's spring, two loops), and joined end to start so
 * the track goes round. Positions grow without bound; the ground repeats every {@link #length()}.
 */
final class RaceTrack implements Runner.Ground {
    /** Green Hill blocks: 60 start/finish, 45 palms, 12 rolling, 3 the spring and totem ledge, 53 the loop... */
    final int[] blocks = {60, 45, 12, 3, 45, 53, 38, 1, 16, 17, 37, 21, 49, 34, 2, 45, 53, 60};
    private final Art art;

    RaceTrack(Art art) {
        this.art = art;
    }

    int length() {
        return blocks.length * Art.BLOCK;
    }

    /** The block under an x (any lap). */
    int blockAt(int x) {
        return blocks[Math.floorMod(Math.floorDiv(x, Art.BLOCK), blocks.length)];
    }

    @Override
    public boolean solid(int x, int y) {
        return art.solid(blockAt(x), Math.floorMod(x, Art.BLOCK), y);
    }

    @Override
    public int floorBelow(int x, int fromY) {
        for (int y = Math.max(0, fromY); y < Art.BLOCK; y++) {
            if (solid(x, y)) {
                return y;
            }
        }
        return Art.BLOCK * 2;
    }

    @Override
    public int left() {
        return -1_000_000;
    }

    @Override
    public int right() {
        return 1_000_000;
    }

    /** The springs' x within a lap (block 3's spring stands 20 pixels into it, as in the valley). */
    int springOffset() {
        return indexOf(3) * Art.BLOCK + 20;
    }

    /** Whether x (any lap) is a loop block's entry window: the scripted loop starts there at speed. */
    boolean loopEntry(float x) {
        int lapX = Math.floorMod(Math.round(x), length());
        int column = lapX / Art.BLOCK;
        int within = lapX % Art.BLOCK;
        return blocks[column] == 53 && within >= 96 && within <= 124;
    }

    /** The loop's block start for an x inside its entry window. */
    int loopStart(float x) {
        return Math.floorDiv(Math.round(x), Art.BLOCK) * Art.BLOCK;
    }

    private int indexOf(int block) {
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == block) {
                return i;
            }
        }
        return 0;
    }
}
