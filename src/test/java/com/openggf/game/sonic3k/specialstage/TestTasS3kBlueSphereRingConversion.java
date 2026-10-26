package com.openggf.game.sonic3k.specialstage;

import org.junit.jupiter.api.Test;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TASVideos 3521M / 5639S, "Turning spheres into rings without surrounding them".
 * Synthetic closed-path component fixtures, not a reconstruction of the movie's layouts.
 * Oracle: Red_Loop_Processes_Loop (sonic3k.asm:13204-13242), including
 * Red_Loop_Direction_Changed; Blue_To_Ring_Next_Ring_B (sonic3k.asm:12952-12995),
 * including the subsequent red-neighbor pass. Pinned skdisasm 8b2cf3c4, FixBugs=0.
 */
class TestTasS3kBlueSphereRingConversion {
    @Test
    void concaveFirstTurnSeedsOutsideAndFloodsDiagonalBlueNeighbors() {
        var grid = notchedLoop();

        var result = new Sonic3kSpecialStageRingConverter().convert(grid, index(7, 6));

        assertTrue(result.converted, "the closed red path must actually reach conversion");
        assertEquals(4, result.blueSpheresConverted,
                "only the four connected OUTSIDE blues are decremented, including the seed");
        for (int[] point : OUTSIDE_BLUES) {
            assertEquals(CELL_RING, grid.getCell(point[0], point[1]),
                    "outside blue at " + point[0] + "," + point[1]);
        }
        assertEquals(CELL_RING, grid.getCell(6, 6), "red neighbor of outside seed converts too");
        assertEquals(CELL_RED, grid.getCell(3, 9), "distant red border does not flood");
        assertEquals(CELL_BLUE, grid.getCell(7, 8), "geometric interior is not the chosen seed");
        assertEquals(CELL_BLUE, grid.getCell(15, 5), "disconnected outside blue is not converted");
    }

    @Test
    void openingTheSameRedPathPreventsOutsideFill() {
        var grid = notchedLoop();
        grid.setCell(3, 7, CELL_EMPTY);

        var result = new Sonic3kSpecialStageRingConverter().convert(grid, index(7, 6));

        assertFalse(result.converted, "blue adjacency and spans alone cannot close the path");
        assertEquals(0, result.blueSpheresConverted);
        for (int[] point : OUTSIDE_BLUES) {
            assertEquals(CELL_BLUE, grid.getCell(point[0], point[1]));
        }
        assertEquals(CELL_RED, grid.getCell(6, 6));
    }

    @Test
    void convexLoopConvertsItsInteriorButDoesNotJumpAnEmptyGap() {
        var grid = new Sonic3kSpecialStageGrid();
        horizontal(grid, 5, 7, 5);
        horizontal(grid, 5, 7, 7);
        grid.setCell(5, 6, CELL_RED);
        grid.setCell(7, 6, CELL_RED);
        grid.setCell(5, 5, CELL_TOUCHED);
        grid.setCell(6, 6, CELL_BLUE);
        grid.setCell(9, 6, CELL_BLUE);

        var result = new Sonic3kSpecialStageRingConverter().convert(grid, index(5, 5));

        assertTrue(result.converted);
        assertEquals(1, result.blueSpheresConverted);
        assertEquals(CELL_RING, grid.getCell(6, 6));
        assertEquals(CELL_RING, grid.getCell(7, 6));
        assertEquals(CELL_BLUE, grid.getCell(9, 6));
    }

    private static final int[][] OUTSIDE_BLUES = {{7, 5}, {7, 4}, {6, 3}, {6, 2}};

    private static Sonic3kSpecialStageGrid notchedLoop() {
        var grid = new Sonic3kSpecialStageGrid();
        // Top-open notch in a single red loop, T=(7,6):
        //   RRR...RRR   y=3
        //   R.R...R.R
        //   R.R...R.R
        //   R.RRTRR.R   y=6
        //   R.......R
        //   R.......R
        //   RRRRRRRRR   y=9
        // From T the DFS first walks right, then UP at (9,6). The closing
        // direction is also right, so the ROM checks T+UP=(7,5), outside.
        // The opposite traversal also first turns UP. Neither is polygon fill.
        horizontal(grid, 3, 5, 3);
        horizontal(grid, 9, 11, 3);
        horizontal(grid, 5, 9, 6);
        horizontal(grid, 3, 11, 9);
        for (int y = 3; y <= 9; y++) {
            grid.setCell(3, y, CELL_RED);
            grid.setCell(11, y, CELL_RED);
        }
        for (int y = 3; y <= 6; y++) {
            grid.setCell(5, y, CELL_RED);
            grid.setCell(9, y, CELL_RED);
        }
        grid.setCell(7, 6, CELL_TOUCHED);
        for (int[] point : OUTSIDE_BLUES) {
            grid.setCell(point[0], point[1], CELL_BLUE);
        }
        grid.setCell(7, 8, CELL_BLUE);
        grid.setCell(15, 5, CELL_BLUE);
        return grid;
    }

    private static void horizontal(Sonic3kSpecialStageGrid grid, int left, int right, int y) {
        for (int x = left; x <= right; x++) {
            grid.setCell(x, y, CELL_RED);
        }
    }

    private static int index(int x, int y) {
        return y * 32 + x;
    }
}
