package com.openggf.game.soniccd.device.pcm;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class TestPcmNativeBlock {
    @Test void admitsCompleteDisabledHoldWithoutImportingAClockIntoTheChip() {
        var block = PcmNativeBlock.parse(hold());
        assertEquals(1, block.samples().size());
        assertEquals(new CdPcmOutput.Step(0, 0, 0, 0, false), block.samples().getFirst());
        assertEquals(block.before(), block.after());
        var chip = new CdPcmDevice();
        chip.restoreState(block.before());
        assertEquals(block.samples(), chip.advanceSamples(1).steps());
        assertEquals(block.after(), chip.captureState());
    }

    @Test void rejectsChangedExtentPhaseRegistersRamDeltasAndUnobservedFields() {
        int[][] mutations = {{0, 0}, {4, 2}, {16, 4097}, {20, 223}, {36, 2}, {44, 1},
                {68, 1}, {64 + 48 * 4, 1}, {64 + 55 * 4, 99},
                {288 + 50 * 4, 1}, {288 + 54 * 4, 1535},
                {66048, 1}, {131584 + 12, 1}, {131584 + 20, 1},
                {131584 + 24, 1}, {131584 + 28, 1}, {131584 + 32, 1}};
        for (int[] mutation : mutations) {
            byte[] data = hold();
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(mutation[0], mutation[1]);
            assertThrows(IllegalArgumentException.class, () -> PcmNativeBlock.parse(data),
                    "mutation at " + mutation[0]);
        }
        assertThrows(IllegalArgumentException.class, () -> PcmNativeBlock.parse(new byte[PcmNativeBlock.BYTES - 1]));
        assertThrows(IllegalArgumentException.class, () -> PcmNativeBlock.parse(new byte[PcmNativeBlock.BYTES + 1]));
    }

    private static byte[] hold() {
        byte[] data = new byte[PcmNativeBlock.BYTES];
        var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int[] header = {0x424d4350, 1, 1, 1, 1, 224, 65536, 32, 1, 1, 1};
        for (int value : header) { b.putInt(value); }
        b.putInt(64 + 55 * 4, 100);
        b.putInt(288 + 54 * 4, 1536);
        b.putInt(288 + 55 * 4, 100);
        return data;
    }
}
