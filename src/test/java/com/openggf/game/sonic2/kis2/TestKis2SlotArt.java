package com.openggf.game.sonic2.kis2;

import com.openggf.game.sonic2.slotmachine.CNZSlotMachineRenderer;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestKis2SlotArt {
    @Test void chipReplacesOnlySonicPictureAndKeepsRewardsAndOtherFaces() {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        // Verify the owning routine points at this asset, independently of the constant.
        assertArrayEquals(new byte[]{0x45, (byte)0xF9, 0, 0x33, (byte)0xB1, (byte)0xF0},
                dump.slice(0x32598C, 6));
        byte[] stock = dump.slice(0x24EEFE, 6 * 512);
        byte[] original = stock.clone();
        var presentation = new Kis2SlotArt(dump);
        byte[] patched = presentation.apply(stock);
        assertArrayEquals(original, stock);
        assertFalse(Arrays.equals(Arrays.copyOf(stock, 512), Arrays.copyOf(patched, 512)));
        assertArrayEquals(dump.slice(0x33B1F0, 512), Arrays.copyOf(patched, 512));
        assertArrayEquals(Arrays.copyOfRange(stock, 512, stock.length),
                Arrays.copyOfRange(patched, 512, patched.length));
        assertEquals("Knuckles", new CNZSlotMachineRenderer(presentation).getDebugFrameName(0));
        assertEquals("Sonic", new CNZSlotMachineRenderer().getDebugFrameName(0));
        assertEquals("30 rings", CNZSlotMachineRenderer.getFaceReward(0));
        assertThrows(IllegalArgumentException.class, () -> presentation.apply(new byte[512]));
    }
}
