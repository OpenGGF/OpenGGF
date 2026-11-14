package com.openggf.game.soniccd.zones;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.player.CdWaterCurrent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GetLevelBlock}'s loop-chunk branches for the player (scddisasm src/Level/Collision Floor.asm:
 * 557-650) and the water-current box test ({@code CheckWaterCurrents}, src/r4/main.asm:792-804).
 */
class TestCdPlayerLoopChunks {
    @Test
    void standardBranchSwapsOnlyOnTheLoopPath() {
        var rule = CdLevel.PlayerLoopChunkRule.STANDARD;
        assertEquals(0x20, rule.chunk(0x20, false));
        assertEquals(0x21, rule.chunk(0x20, true));
        assertEquals(0x51, rule.chunk(0x28, true), "$29 becomes $51");
    }

    @Test
    void stardustSpeedwaySwapsByPathForFourAndSixAndByLayerForItsFive() {
        assertEquals(5, ssz(4, true, false));
        assertEquals(4, ssz(4, false, true), "chunks 4 and 6 ignore the layer");
        assertEquals(7, ssz(6, true, false));
        for (int chunk : new int[] {0x28, 0x3C, 0x37, 0x2F, 0x16}) {
            assertEquals(chunk, ssz(chunk, true, false), "no layer: unchanged even on the loop path");
            assertEquals(chunk + 1, ssz(chunk, false, true));
        }
        assertEquals(0x29, ssz(0x29, true, true), "other loop chunks read as themselves");
    }

    @Test
    void metallicMadnessRemapsSixChunksByLayerOnly() {
        int[][] map = {{0x46, 0x6A}, {0x48, 0x6B}, {0x4A, 0x6C}, {0x10, 0x6D}, {0x63, 0x6E}, {0x43, 0x6F}};
        for (int[] pair : map) {
            assertEquals(pair[0], com.openggf.game.soniccd.zones.mmz.MmzLoopChunksAccess.loopChunk(pair[0], 0));
            assertEquals(pair[1], com.openggf.game.soniccd.zones.mmz.MmzLoopChunksAccess.loopChunk(pair[0], 1));
        }
        assertEquals(0x28, com.openggf.game.soniccd.zones.mmz.MmzLoopChunksAccess.loopChunk(0x28, 1));
    }

    @Test
    void currentBoxIsHalfOpen() {
        var current = new CdWaterCurrent(0x220, 0x2F0, 0x240, 0x440, 0, 0x400, 0, 0);
        assertTrue(current.contains(0x220, 0x2F0));
        assertTrue(current.contains(0x23F, 0x43F));
        assertFalse(current.contains(0x240, 0x300), "right edge excluded");
        assertFalse(current.contains(0x230, 0x2EF), "above the top");
        assertFalse(current.contains(0x230, 0x440), "bottom edge excluded");
    }

    private static int ssz(int chunk, boolean path, boolean layer) {
        return com.openggf.game.soniccd.zones.ssz.SszLoopChunksAccess.loopChunk(chunk, path, layer);
    }
}
