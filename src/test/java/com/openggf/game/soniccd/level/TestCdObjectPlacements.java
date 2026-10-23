package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestCdObjectPlacements {
    @Test
    void preservesEqualXOrderingAllFlagsAndAllFourPeriodSelections() {
        byte[] records = {
                0, 64, (byte) 0xF7, 0x6B, (byte) 0x87, 12, 0x60, 33,
                0, 64, 0, 4, 64, 22, (byte) 0xC0, 44,
                0, 65, 0, 5, 9, 11, 0x20, 55,
                (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0
        };
        var placements = CdObjectPlacements.decodeRecords(records);
        assertEquals(3, placements.records().size());
        var first = placements.records().getFirst();
        assertEquals(0, first.index());
        assertEquals(64, first.centreX());
        assertEquals(0x76B, first.centreY());
        assertEquals(0xF76B, first.packedY());
        assertEquals(0xF000, first.nativeYFlags());
        assertTrue(first.flipX());
        assertTrue(first.flipY());
        assertEquals(7, first.objectId());
        assertTrue(first.hasSavedFlag());
        assertEquals(0x87, first.encodedId());
        assertEquals(12, first.subtype());
        assertEquals(0x60, first.periodFlags());
        assertEquals(33, first.subtype2());
        assertEquals(2, placements.forPeriod(CdPeriod.PRESENT).size());
        assertEquals(7, placements.forPeriod(CdPeriod.PRESENT).get(0).objectId());
        assertEquals(64, placements.forPeriod(CdPeriod.PRESENT).get(1).objectId());
        assertEquals(2, placements.forPeriod(CdPeriod.PAST).size());
        assertEquals(1, placements.forPeriod(CdPeriod.GOOD_FUTURE).size());
        assertEquals(placements.forPeriod(CdPeriod.GOOD_FUTURE), placements.forPeriod(CdPeriod.BAD_FUTURE));
        Arrays.fill(records, (byte) 0);
        assertEquals(64, first.centreX());
        assertEquals(0xFF, placements.forwardSentinel().unsignedByte(0));
        assertThrows(UnsupportedOperationException.class, () -> placements.records().clear());
        assertThrows(UnsupportedOperationException.class, () -> placements.forPeriod(CdPeriod.PRESENT).clear());
        assertThrows(ReadOnlyBufferException.class, () -> placements.forwardSentinel().buffer().put(0, (byte) 0));
    }

    @Test
    void layoutRequiresItsBackwardSentinelAndIndependentNullList() {
        byte[] body = layout();
        var decoded = CdObjectPlacements.decodeLayout(body);
        assertEquals(1, decoded.records().size());
        assertEquals(8, decoded.backwardSentinel().orElseThrow().length());
        assertEquals(6, decoded.nullList().orElseThrow().length());
        assertEquals(0xFFFF, decoded.backwardSentinel().orElseThrow().unsignedWord(0));
        assertEquals(0xFFFF, decoded.nullList().orElseThrow().unsignedWord(0));
        body[4] = 0;
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeLayout(body));
        body[4] = (byte) 0xFF;
        body[3]++;
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeLayout(body));
        body[3]--;
        body[28] = 0;
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeLayout(body));
    }

    @Test
    void wrongIdsUnsupportedPeriodBitsOrderingAndSentinelsAreRejected() {
        byte[] valid = {0, 1, 0, 2, 1, 3, 0x40, 4, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0};
        for (int id : new int[]{0, 65, 127, 128}) {
            byte[] bad = valid.clone();
            bad[4] = (byte) id;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeRecords(bad));
        }
        for (int periods : new int[]{0, 1, 0x41, 0xFF}) {
            byte[] bad = valid.clone();
            bad[6] = (byte) periods;
            assertThrows(UnsupportedOperationException.class, () -> CdObjectPlacements.decodeRecords(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeRecords(Arrays.copyOf(valid, 15)));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeRecords(Arrays.copyOf(valid, 8)));
        byte[] trailing = Arrays.copyOf(valid, 24);
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeRecords(trailing));
        byte[] unordered = new byte[24];
        System.arraycopy(valid, 0, unordered, 0, 8);
        System.arraycopy(valid, 0, unordered, 8, 16);
        unordered[9] = 0;
        assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeRecords(unordered));
    }

    private static byte[] layout() {
        return new byte[]{
                0, 12, 0, 28, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0,
                0, 1, 0, 2, 1, 3, 0x40, 4,
                (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0,
                (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0
        };
    }
}
