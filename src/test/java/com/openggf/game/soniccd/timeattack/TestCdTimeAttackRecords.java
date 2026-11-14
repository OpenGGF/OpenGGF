package com.openggf.game.soniccd.timeattack;

import com.openggf.game.soniccd.backupram.CdSaveRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The record arithmetic of Time Attack/Main.asm without the disc. */
class TestCdTimeAttackRecords {
    private static CdSaveRecord record() {
        byte[] bytes = new byte[CdSaveRecord.FILE_BYTES];
        CdSaveRecord r = CdSaveRecord.decode(bytes);
        for (int row = 0; row < CdSaveRecord.ROWS; row++) {
            for (int rank = 0; rank < 3; rank++) {
                r.setTime(row, rank, 0x00050000);
                r.setInitials(row, rank, 0x0B0B0B00);
            }
        }
        return r;
    }

    @Test
    void aFasterTimeTakesItsRankAndPushesTheRestDown() {
        CdSaveRecord r = record();
        r.setTime(3, 0, 0x00000F09);
        r.setTime(3, 1, 0x00051111);
        r.setInitials(3, 0, 0x1E0B1300);
        r.setInitials(3, 1, 0x161D0100);
        assertEquals(2, CdTimeAttackRecords.insert(r, 3, 0x00012010, 0x23191F00));
        assertEquals(0x00000F09, r.time(3, 0));
        assertEquals(0x00012010, r.time(3, 1));
        assertEquals(0x23191F00, r.initials(3, 1));
        assertEquals(0x00051111, r.time(3, 2));
        assertEquals(0x161D0100, r.initials(3, 2), "the old second place is now third");
    }

    @Test
    void anEqualTimeWinsNothing() {
        CdSaveRecord r = record();
        assertEquals(0, CdTimeAttackRecords.insert(r, 0, 0x00050000, 0), "cmp.l (a2),d0; bge: equal is not faster");
        assertEquals(1, CdTimeAttackRecords.insert(r, 0, 0x0004FFFF, 0));
    }

    @Test
    void framesBecomeHundredthsAndTotalsCarry() {
        assertEquals(0x0001234B, CdTimeAttackRecords.normalize(0x0001232D), "45 frames are 75 hundredths");
        // 1'35"75 + 0'69"13 (an out-of-range second count adds as stored) = 2'44"88.
        assertEquals(0x00022C58, CdTimeAttackRecords.add(0x0001234B, 0x0000450D));
        assertEquals(0x00690000, CdTimeAttackRecords.add(0x00640000, 0x00050000), "105 minutes");
    }

    @Test
    void totalsBelowTheThresholdsUnlockTitleOptions() {
        assertEquals(0x30, CdTimeAttackRecords.unlocks(0x30, 0x00690000));
        assertEquals(0x30 | 0x08, CdTimeAttackRecords.unlocks(0x30, 0x00251B38));
        assertEquals(0x30 | 0x08 | 0x02, CdTimeAttackRecords.unlocks(0x30, 0x001E1504));
        assertEquals(0x30 | 0x08 | 0x02 | 0x04, CdTimeAttackRecords.unlocks(0x30, 0x00192E0B));
    }
}
