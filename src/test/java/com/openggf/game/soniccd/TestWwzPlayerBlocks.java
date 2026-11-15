package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.level.CdStageLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stage bytes {@code WwzPlayerProfile}'s {@code GetLevelBlock} relies on, for all ten
 * Wacky Workbench stages. {@code GetLevelBlock} writes only the low byte (blank chunk) or
 * low word (other chunks) of {@code #LevelChunks} (Collision Floor.asm:481-511), so the
 * chunk table must start on a $10000 boundary for the profile's table-relative reads to be
 * the ROM's; and the pole ($103), bouncy-floor ($21F) and Bad Future spark ($287) blocks the checks
 * test must exist in the chunk data of the stages the native fixtures show using them.
 */
class TestWwzPlayerBlocks {
    @Test
    void chunkTablesAreAlignedAndHoldTheSpecialBlocks() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdStageLoader loader = new CdStageLoader(disc);
            int stages = 0;
            for (CdStagePayload payload : CdStagePayload.values()) {
                if (payload.stage().zone() != CdZone.WACKY_WORKBENCH) continue;
                CdStageImage image = loader.readStage(payload);
                int pointer = image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8);
                assertEquals(0, pointer & 0xFFFF, payload + " LevelChunks at " + Integer.toHexString(pointer));
                stages++;
            }
            assertEquals(10, stages);
            assertTrue(holdsBlock(loader, CdStagePayload.R62D, 0x103), "R62D pole block $103");
            assertTrue(holdsBlock(loader, CdStagePayload.R63C, 0x21F), "R63C bouncy floor block $21F");
            assertTrue(holdsBlock(loader, CdStagePayload.R63D, 0x287), "R63D Bad Future spark block $287");
        }
    }

    private static boolean holdsBlock(CdStageLoader loader, CdStagePayload payload, int block) throws Exception {
        CdStageImage image = loader.readStage(payload);
        int table = image.offsetOf(image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8));
        for (int at = 0; at < 0x80 * 0x200; at += 2) {
            if (table + at + 1 >= image.size()) break;
            if ((image.u16(table + at) & 0x7FF) == block) return true;
        }
        return false;
    }
}
