package com.openggf.game;

import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that every sidekick gets a unique shifted VRAM bank,
 * regardless of whether its character name matches the main character.
 */
class TestSidekickArtBankAllocation {

    /**
     * All sidekicks should get unique offsets within SIDEKICK_PATTERN_BASE,
     * including characters that differ from the main (previously got slot 0).
     */
    @Test
    void allSidekicksGetUniqueOffsets() {
        List<Integer> offsets = LevelManager.computeSidekickBankOffsets(List.of(48));
        assertEquals(1, offsets.size());
        assertEquals(0, offsets.get(0));
    }

    @Test
    void multipleSidekicksGetNonOverlappingOffsets() {
        List<Integer> offsets = LevelManager.computeSidekickBankOffsets(List.of(32, 48));
        assertEquals(2, offsets.size());
        assertEquals(0, offsets.get(0));
        assertEquals(32, offsets.get(1));
    }

    @Test
    void duplicateCharactersGetSeparateOffsets() {
        List<Integer> offsets = LevelManager.computeSidekickBankOffsets(List.of(64, 64));
        assertEquals(2, offsets.size());
        assertEquals(0, offsets.get(0));
        assertEquals(64, offsets.get(1));
    }

    @Test
    void preflightRejectsOverflowBeforeReturningAnyAllocation() {
        int capacity = com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size();
        assertThrows(IllegalArgumentException.class,
                () -> LevelManager.computeSidekickBankOffsets(List.of(capacity, 1)));
    }

    @Test
    void preflightRejectsNegativeAndIntegerOverflowSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> LevelManager.computeSidekickBankOffsets(List.of(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> LevelManager.computeSidekickBankOffsets(List.of(Integer.MAX_VALUE, 1)));
    }
}

