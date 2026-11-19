package com.openggf.game.sonic3k.specialstage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the sphere-to-ring conversion algorithm.
 * No ROM or OpenGL required.
 */
class TestSonic3kSpecialStageRingConverter {
    private Sonic3kSpecialStageGrid grid;
    private Sonic3kSpecialStageRingConverter converter;

    @BeforeEach
    void setUp() {
        grid = new Sonic3kSpecialStageGrid();
        converter = new Sonic3kSpecialStageRingConverter();
    }

    @Test
    void testNoConversionWhenNoBlueNeighbors() {
        // Single isolated touched sphere with no blue neighbors
        grid.setCellByIndex(0x88, CELL_TOUCHED);
        var result = converter.convert(grid, 0x88);
        assertFalse(result.converted, "Should not convert when no blue neighbors");
    }

    @Test
    void testNoConversionForLineOfSpheres() {
        // A line of spheres (horizontal span < 3 vertically)
        grid.setCell(5, 5, CELL_TOUCHED);
        grid.setCell(6, 5, CELL_BLUE);
        grid.setCell(7, 5, CELL_BLUE);
        grid.setCell(8, 5, CELL_BLUE);
        var result = converter.convert(grid, 0xA5);
        assertFalse(result.converted,
                "Line of spheres has no vertical span - should not convert");
    }

    @Test
    void testPlayerPositionToGridIndex() {
        // Verify the index calculation matches ROM formula
        // Position (0x500, 0x300) -> grid cell (5, 3)
        // ((0x300+0x80)>>8 & 0x1F) * 0x20 + ((0x500+0x80)>>8 & 0x1F)
        // = (3 & 0x1F) * 0x20 + (5 & 0x1F) = 0x60 + 5 = 0x65
        assertEquals(0x65, Sonic3kSpecialStageGrid.positionToIndex(0x500, 0x300));
    }

    @Test
    void testDirectionTableValues() {
        assertArrayEquals(new int[] {-0x21, -0x20, -0x1F, -1, 1, 0x1F, 0x20, 0x21}, DIRECTIONS_8);
        assertArrayEquals(new int[] {-1, -0x20, 1, 0x20, -1, -0x20}, DIRECTIONS_4);
    }
}
