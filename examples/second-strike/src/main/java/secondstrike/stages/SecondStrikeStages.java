package secondstrike.stages;

import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;

/** Native special/bonus stages with a small authored challenge and native rewards. */
public final class SecondStrikeStages implements BlueSphereLayoutPolicy, GumballSpringPolicy {
    private static final int GRID_SIZE = 32;
    private static final int GRID_BYTES = GRID_SIZE * GRID_SIZE;
    private static final int LAYOUT_BYTES = GRID_BYTES + 8;
    private static final int EMPTY = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int BUMPER = 3;

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
        validateVariant(romLayout, result);
        return result;
    }

    /** Three consumable native-strength springs leave smaller recovery margins. */
    @Override
    public int[] springOffsets() {
        return new int[] {-32, 0, 32};
    }

    private static void validateVariant(byte[] source, byte[] variant) {
        if (!Arrays.equals(Arrays.copyOfRange(source, GRID_BYTES, LAYOUT_BYTES),
                Arrays.copyOfRange(variant, GRID_BYTES, LAYOUT_BYTES))) {
            throw new IllegalArgumentException("A turn-gate variant must preserve the native entry and ring target");
        }
        boolean[] before = reachable(source);
        boolean[] after = reachable(variant);
        for (int i = 0; i < GRID_BYTES; i++) {
            if ((source[i] & 0xFF) == BLUE && before[i] && !after[i]) {
                throw new IllegalArgumentException("Turn gate closes a native blue-sphere route");
            }
        }
        int startX = word(source, GRID_BYTES + 2) >> 8;
        int startY = word(source, GRID_BYTES + 4) >> 8;
        int angle = word(source, GRID_BYTES) >> 8;
        int dx = angle == 0x40 ? -1 : angle == 0xC0 ? 1 : 0;
        int dy = angle == 0 ? -1 : angle == 0x80 ? 1 : 0;
        for (int step = 0; step <= 4; step++) {
            int cell = index(startX + step * dx, startY + step * dy);
            if (source[cell] != variant[cell]) {
                throw new IllegalArgumentException("Turn gate changes the native opening runway");
            }
        }
    }

    private static boolean[] reachable(byte[] layout) {
        boolean[] visited = new boolean[GRID_BYTES];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        int start = index(word(layout, GRID_BYTES + 2) >> 8, word(layout, GRID_BYTES + 4) >> 8);
        visited[start] = true;
        pending.add(start);
        while (!pending.isEmpty()) {
            int cell = pending.removeFirst();
            int x = cell & 31;
            int y = cell >> 5;
            for (int neighbor : new int[] {index(x - 1, y), index(x + 1, y),
                    index(x, y - 1), index(x, y + 1)}) {
                int type = layout[neighbor] & 0xFF;
                if (!visited[neighbor] && type != RED && type != BUMPER) {
                    visited[neighbor] = true;
                    pending.addLast(neighbor);
                }
            }
        }
        return visited;
    }

    private static int index(int x, int y) {
        return ((y & 31) << 5) | (x & 31);
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
