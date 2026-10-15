package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;

import java.util.Objects;

/**
 * Fourteen individual approach recipes over the player's decoded native boards.
 * Only empty approach cells change: blue loops, rings, springs, entry words and
 * the perfect target remain ROM-owned. These recipes are not full-clear proof.
 */
public final class CampaignBlueSphereBoards implements BlueSphereLayoutPolicy {
    @Override public byte[] transformLayout(int stageIndex, byte[] romLayout) {
        Objects.requireNonNull(romLayout, "romLayout");
        if (romLayout.length != 0x408) {
            throw new IllegalArgumentException("Expected a native 32x32 board and four trailing words");
        }
        byte[] result = romLayout.clone();
        placeGates(result, romLayout, stageIndex, gates(stageIndex), 1);
        // A red sphere here would join the completed blue rectangle to the
        // grid chamber's red wall network. A native bumper keeps that graph
        // separate while still requiring a different approach to this room.
        if (stageIndex == 2) {
            placeGates(result, romLayout, stageIndex, new int[] {22, 26}, 3);
        }
        return result;
    }

    private static void placeGates(byte[] result, byte[] original, int stage,
                                   int[] gates, int cell) {
        for (int i = 0; i < gates.length; i += 2) {
            int index = gates[i + 1] * 32 + gates[i];
            if (original[index] != 0) {
                throw new IllegalArgumentException("Board " + stage
                        + " needs its original empty approach at " + gates[i] + "," + gates[i + 1]);
            }
            result[index] = (byte) cell;
        }
    }

    /** Native slots 7 and 15 are not campaign emerald boards and remain stock. */
    private static int[] gates(int stage) {
        return switch (stage) {
            // Four corner loops: retain the existing taught opening language.
            case 0 -> new int[] {2, 8, 3, 11, 2, 20, 3, 23, 28, 8, 29, 11,
                    28, 20, 29, 23, 10, 3, 21, 2, 10, 28, 21, 29};
            // Connected rooms: stagger the exit and the next room's approach.
            case 1 -> new int[] {9, 24, 20, 28, 12, 16, 21, 18, 23, 5, 29, 8};
            // Grid chambers: one corner in each selected room, never a portal.
            case 2 -> new int[] {6, 4, 9, 10, 14, 18};
            // Central cross and four alcoves: alternate the side of the turn.
            case 3 -> new int[] {11, 7, 20, 25, 4, 13, 27, 18, 9, 20, 22, 11};
            // Bumper transfers: leave the other column of each runway open.
            case 4 -> new int[] {14, 9, 16, 25, 2, 15, 29, 19, 19, 12, 11, 22};
            // Repeated blue squares: two staggered slaloms in the wide links.
            case 5 -> new int[] {10, 7, 11, 8, 26, 23, 27, 24, 31, 11, 15, 27};
            // Bumper maze: pressure the broad rooms, preserve narrow throats.
            case 6 -> new int[] {10, 14, 21, 15, 7, 29, 24, 28, 16, 12};
            // Diagonal islands: gates sit outside the spring transfer line.
            case 8 -> new int[] {10, 12, 15, 12, 15, 24, 25, 26, 20, 8, 29, 6};
            // One-cell passages: challenge the rooms, leave all portals intact.
            case 9 -> new int[] {10, 4, 12, 4, 28, 3, 29, 6, 2, 19, 14, 21, 21, 25, 1, 27};
            // Spring hub: alternating outer-room entries, no hub modification.
            case 10 -> new int[] {7, 6, 24, 6, 4, 12, 27, 19, 9, 25, 22, 25};
            // Spring-bordered loops have very little spare floor: only two gates.
            case 11 -> new int[] {23, 1, 23, 8};
            // Three separated bands: teach each gate on a two-column approach.
            case 12 -> new int[] {12, 0, 19, 1, 6, 2, 25, 6, 4, 12, 27, 19, 10, 25, 19, 29};
            // Repeated spring lattice: one gate per two-cell transfer. The
            // other row stays open; diagonal pairs would seal a native route.
            case 13 -> new int[] {6, 3, 14, 11, 22, 19, 30, 27};
            // Narrow final network: keep the compulsory shafts and opening clear.
            case 14 -> new int[] {13, 0, 19, 7, 4, 19, 10, 27, 19, 19, 26, 27};
            default -> new int[0];
        };
    }
}
