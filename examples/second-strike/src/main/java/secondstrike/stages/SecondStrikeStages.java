package secondstrike.stages;

import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;

import java.util.Objects;

/** Native special/bonus stages with a small authored challenge and native rewards. */
public final class SecondStrikeStages implements BlueSphereLayoutPolicy, GumballSpringPolicy {
    private static final int GRID_SIZE = 32;
    private static final int GRID_BYTES = GRID_SIZE * GRID_SIZE;
    private static final int LAYOUT_BYTES = GRID_BYTES + 8;
    private static final int EMPTY = 0;
    private static final int RED = 1;

    /**
     * Copies the player's decoded ROM layout. This representative variant changes
     * only the first Chaos Emerald board; every other board remains ROM-native.
     * Native blue/ring cells, entry parameters, and the perfect ring target stay
     * intact, so stage/result/emerald/return ownership remains with the stock stage.
     */
    @Override
    public byte[] transformLayout(int stageIndex, byte[] romLayout) {
        Objects.requireNonNull(romLayout, "romLayout");
        if (romLayout.length != LAYOUT_BYTES) {
            throw new IllegalArgumentException("Expected a decoded 32x32 ROM layout and four trailing words");
        }
        byte[] result = romLayout.clone();
        if (stageIndex != 0) {
            return result;
        }
        // Invocation-local gates keep the creator class free of static arrays.
        // Existing blue loop geometry and four clear opening cells are preserved;
        // each approach now needs an early turn or a native timed jump.
        int[][] turnGates = {
                {2, 8}, {3, 11}, {2, 20}, {3, 23},
                {28, 8}, {29, 11}, {28, 20}, {29, 23},
                {10, 3}, {21, 2}, {10, 28}, {21, 29}
        };
        for (int[] gate : turnGates) {
            int index = index(gate[0], gate[1]);
            if ((romLayout[index] & 0xFF) != EMPTY) {
                throw new IllegalArgumentException("Turn gate requires an empty ROM cell at "
                        + gate[0] + "," + gate[1]);
            }
            result[index] = RED;
        }
        // The owner-bound native policy admission validates reachability, runway and edit bounds.
        return result;
    }

    /** Three consumable native-strength springs leave smaller recovery margins. */
    @Override
    public int[] springOffsets() {
        return new int[] {-32, 0, 32};
    }

    private static int index(int x, int y) {
        return ((y & 31) << 5) | (x & 31);
    }

}
