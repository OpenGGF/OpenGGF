package starpost.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A small made-up level kit for testing generation without a ROM: 256-pixel blocks of flat
 * floors at three heights, a platform, a pillar, two slopes, a ceiling, solid brick and a lava
 * pool (an invisible floor over a bed, as Marble Zone's kit has), laid out in three rows.
 */
final class TestKit implements Kit {
    static final int SIZE = 256;
    static final int AIR = 9;
    static final int BRICK = 10;
    static final int LAVA = 11;

    private final List<byte[]> masks = new ArrayList<>();
    private final List<boolean[]> visible = new ArrayList<>();
    private final int columns;
    private final int[] layout;

    TestKit(long seed, int columns) {
        this.columns = columns;
        masks.add(new byte[SIZE * SIZE]);
        visible.add(new boolean[SIZE * SIZE]);
        add(floor(192));                                     // 1 flat
        add(floor(160));                                     // 2 higher
        add(floor(224));                                     // 3 lower
        add(with(floor(192), 64, 192, 112, 120, Chamber.TOP_SOLID)); // 4 a platform
        add(with(floor(192), 112, 144, 136, 192, Chamber.SOLID));    // 5 a pillar
        add(slope(224, 160));                                // 6 up
        add(slope(160, 224));                                // 7 down
        add(with(floor(192), 0, 256, 0, 40, Chamber.SOLID));  // 8 a ceiling
        add(new byte[SIZE * SIZE]);                          // 9 air
        add(with(new byte[SIZE * SIZE], 0, 256, 0, 256, Chamber.SOLID)); // 10 brick
        byte[] pool = with(with(floor(192), 64, 192, 192, 240, Chamber.EMPTY), 64, 192, 200, 206, Chamber.TOP_SOLID);
        boolean[] seen = seen(pool);
        for (int y = 200; y < 206; y++) {
            for (int x = 64; x < 192; x++) {
                seen[y * SIZE + x] = false;                  // the lava surface has no picture
            }
        }
        masks.add(pool);                                     // 11 lava
        visible.add(seen);
        layout = new int[columns * 3];
        long z = seed;
        for (int c = 0; c < columns; c++) {
            z = z * 6364136223846793005L + 1442695040888963407L;
            int ground = 1 + (int) ((z >>> 33) % 8);         // 1..8
            if ((z >>> 40) % 7 == 0) {
                ground = LAVA;
            }
            layout[c] = (z >>> 50) % 5 == 0 ? 8 : AIR;
            layout[c + columns] = ground;
            layout[c + 2 * columns] = BRICK;
        }
    }

    private void add(byte[] mask) {
        masks.add(mask);
        visible.add(seen(mask));
    }

    private static boolean[] seen(byte[] mask) {
        boolean[] out = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) {
            out[i] = mask[i] != Chamber.EMPTY;
        }
        return out;
    }

    static byte[] floor(int y) {
        return with(new byte[SIZE * SIZE], 0, 256, y, 256, Chamber.SOLID);
    }

    static byte[] slope(int left, int right) {
        byte[] m = new byte[SIZE * SIZE];
        for (int x = 0; x < SIZE; x++) {
            int top = left + (right - left) * x / (SIZE - 1);
            for (int y = top; y < SIZE; y++) {
                m[y * SIZE + x] = Chamber.SOLID;
            }
        }
        return m;
    }

    static byte[] with(byte[] m, int x0, int x1, int y0, int y1, byte value) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                m[y * SIZE + x] = value;
            }
        }
        return m;
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public int columns() {
        return columns;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public int block(int column, int row) {
        return column < 0 || row < 0 || column >= columns || row >= 3 ? 0 : layout[column + row * columns];
    }

    @Override
    public int blockCount() {
        return masks.size();
    }

    @Override
    public byte[] solidity(int block) {
        return block <= 0 || block >= masks.size() ? null : masks.get(block);
    }

    @Override
    public int[] area() {
        return new int[] {0, 0, columns * SIZE, 3 * SIZE};
    }

    @Override
    public boolean opaque(int block, int x, int y) {
        return block > 0 && block < visible.size() && visible.get(block)[y * SIZE + x];
    }
}
