package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.game.soniccd.player.CdPlayerStageTables;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The generated player tables read the bytes the disassembly assembles: ObjSonic_Handle3DRamp's
 * .ChunkList (Sonic/Main.asm:825-831) and each stage's SpecChunks (Palmtree Panic
 * Scroll (Act 1 Present).asm:128-129, (Act 1 Past).asm SpecChunks, (Act 2 Present).asm SpecChunks).
 */
class TestCdPlayerStageTables {
    @Test
    void tablesMatchTheAssembledPalmtreePanicBytes() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdStageLoader loader = new CdStageLoader(disc);
            CdStageImage r11a = loader.readStage(CdStagePayload.R11A);
            assertArrayEquals(new int[] {6, 7, 8, 0x49, 0x4C}, CdPlayerStageTables.ramp3dChunks(CdStagePayload.R11A, r11a));
            assertArrayEquals(new int[] {0x7F, 0x7F, 0x7F, 0x7F}, CdPlayerStageTables.specialChunks(CdStagePayload.R11A, r11a));
            assertArrayEquals(new int[] {0x8C, 0x7F, 0x1E, 0x1E},
                    CdPlayerStageTables.specialChunks(CdStagePayload.R11B, loader.readStage(CdStagePayload.R11B)));
            assertArrayEquals(new int[] {0x91, 0xB6, 0x7F, 0x7F},
                    CdPlayerStageTables.specialChunks(CdStagePayload.R12A, loader.readStage(CdStagePayload.R12A)));
        }
    }

    /**
     * Quartz Quadrant's block check lists by time zone: count word + 1 entries, which
     * sub_2066BA (src/r5/player_block_check.asm:46-60) reaches through the word offsets
     * of PlayerBlocks (src/r5/player_block_check.asm:78-304):
     * Present (A) $2C, Past (B) $31, Good Future (C) $33, Bad Future (D) $31. Stardust
     * Speedway's block check has no such table.
     */
    @Test
    void blockListsMatchTheAssembledQuartzQuadrantBytes() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdStageLoader loader = new CdStageLoader(disc);
            CdStageImage r52a = loader.readStage(CdStagePayload.R52A);
            int[] past = CdPlayerStageTables.playerBlocks(CdStagePayload.R52A, r52a, 0);
            int[] present = CdPlayerStageTables.playerBlocks(CdStagePayload.R52A, r52a, 1);
            int[] bad = CdPlayerStageTables.playerBlocks(CdStagePayload.R52A, r52a, 2);
            int[] good = CdPlayerStageTables.playerBlocks(CdStagePayload.R52A, r52a, 3);
            assertEquals(0x31 + 1, past.length);
            assertEquals(0x2C + 1, present.length);
            assertEquals(0x31 + 1, bad.length);
            assertEquals(0x33 + 1, good.length);
            assertArrayEquals(new int[] {1, 2, 3}, java.util.Arrays.copyOf(present, 3));
            assertEquals(0x1C2, present[present.length - 1]);
            assertEquals(0x1E5, past[past.length - 1]);
            assertEquals(0x1F4, bad[bad.length - 1]);
            assertEquals(0x1ED, good[good.length - 1]);
            assertNull(CdPlayerStageTables.playerBlocks(CdStagePayload.R71A, loader.readStage(CdStagePayload.R71A), 1));
        }
    }
}
