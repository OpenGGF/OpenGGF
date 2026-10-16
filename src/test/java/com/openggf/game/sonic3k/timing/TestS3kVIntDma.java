package com.openggf.game.sonic3k.timing;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.timing.S3kVIntDma.Target;
import com.openggf.game.sonic3k.timing.S3kVIntDma.Transfer;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GPGX 051d430d DMA freeze rules and the ROM-decoded VInt_8 transfers. The per-V-int totals in
 * {@link #matchesRetainedNativeFreezeCounters} are comparison-only values of the observation-only stall counter
 * {@code dma_freeze_mclk} (closure-v9 counters.jsonl, native-stall-counters lane, 2026-10-16); they are never inputs.
 */
class TestS3kVIntDma {
    private static byte[] rom() throws IOException {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file != null, "S3K ROM not available (set -Ds3k.rom.path)");
        try (Rom r = new Rom()) {
            assertTrue(r.open(file.getAbsolutePath()));
            return r.readAllBytes();
        }
    }

    @Test
    void decodesTheShippedVInt8TransfersFromRomImmediates() throws IOException {
        var vint8 = S3kVIntDma.VInt8Transfers.decode(rom());
        assertEquals(new Transfer(0x40, Target.CRAM, 0), vint8.palette(), "Normal_palette, $80 bytes");
        assertEquals(new Transfer(0x1C0, Target.VRAM, 0xF000), vint8.horizontalScroll(), "H_scroll_buffer, $380 bytes");
        assertEquals(new Transfer(0x140, Target.VRAM, 0xF800), vint8.spriteTable(), "Sprite_table, $280 bytes");
        assertTrue(vint8.h40(), "Level writes $8C81");
        assertEquals(List.of(vint8.horizontalScroll(), vint8.spriteTable()), vint8.inOrder(true));
    }

    @Test
    void refusesARomWhoseDmaInstructionsDiffer() throws IOException {
        byte[] image = rom();
        image[0x894] = 0x00;    // H_scroll_buffer length: $9401 becomes $0001, no longer a register-20 write
        assertThrows(IllegalArgumentException.class, () -> S3kVIntDma.VInt8Transfers.decode(image));
    }

    @Test
    void gpgxRatesFollowTypeWidthAndRefreshSlots() {
        assertEquals(102, S3kVIntDma.blankWordsPerLine(Target.VRAM, true), "204 >> 1");
        assertEquals(83, S3kVIntDma.blankWordsPerLine(Target.VRAM, false), "166 >> 1");
        assertEquals(198, S3kVIntDma.blankWordsPerLine(Target.CRAM, true), "204 less 6 refresh slots");
        assertEquals(161, S3kVIntDma.blankWordsPerLine(Target.CRAM, false), "166 less 5 refresh slots");
        assertEquals(198, S3kVIntDma.blankWordsPerLine(Target.VSRAM, true));
        // per-transfer integer division, not a rounded sum
        assertEquals(64L * 3420 / 198, S3kVIntDma.blankFreeze(new Transfer(0x40, Target.CRAM, 0), true));
        Transfer one = new Transfer(1, Target.VRAM, 0);
        assertEquals(33, S3kVIntDma.blankFreeze(one, true), "3420 / 102 floors per transfer");
        assertEquals(66, S3kVIntDma.blankFreeze(List.of(one, one), true));
        assertEquals(67, S3kVIntDma.blankFreeze(new Transfer(2, Target.VRAM, 0), true));
    }

    @Test
    void onlyATransferEndingStrictlyInsideVBlankHasTheFlooredFreeze() {
        Transfer fits = new Transfer(101, Target.VRAM, 0);
        assertEquals(101L * 3420 / 102, S3kVIntDma.freezeEndingInVBlank(fits, true, 3420));
        // 102 words fit exactly in one remaining line: GPGX keeps dma_cycles = the remaining VBLANK, not the floor
        assertThrows(IllegalArgumentException.class,
                () -> S3kVIntDma.freezeEndingInVBlank(new Transfer(102, Target.VRAM, 0), true, 3420));
        assertThrows(IllegalArgumentException.class,
                () -> S3kVIntDma.freezeEndingInVBlank(new Transfer(500, Target.VRAM, 0), true, 3420));
        assertEquals(37L * 3420, S3kVIntDma.vblankMclk(false));
        assertEquals(88L * 3420, S3kVIntDma.vblankMclk(true));
    }

    @Test
    void matchesRetainedNativeFreezeCounters() throws IOException {
        var vint8 = S3kVIntDma.VInt8Transfers.decode(rom());
        List<Transfer> fixed = vint8.inOrder(false);
        long base = S3kVIntDma.blankFreeze(fixed, vint8.h40());
        // every product, NTSC and PAL, LF 4400-4406 without queue entries
        assertEquals(26_855, base);
        // Tails/+2 NTSC and PAL LF 4403: frame-dma entry 93609400951096B9971956000083 = 96 words to VRAM $D600
        assertEquals(30_073, base + S3kVIntDma.blankFreeze(new Transfer(96, Target.VRAM, 0xD600), true));
        // Tails/+2 NTSC LF 4411 (144 words) and LF 4413 (the $800-word KosM module completion)
        assertEquals(31_683, base + S3kVIntDma.blankFreeze(new Transfer(144, Target.VRAM, 0), true));
        assertEquals(95_523, base + S3kVIntDma.blankFreeze(new Transfer(0x800, Target.VRAM, 0), true));
        // all of it ends far inside NTSC VBLANK even before the handler's own instructions
        List<Transfer> heaviest = new ArrayList<>(fixed);
        heaviest.add(new Transfer(0x800, Target.VRAM, 0));
        assertTrue(S3kVIntDma.blankFreeze(heaviest, true) < S3kVIntDma.vblankMclk(false));
    }
}
