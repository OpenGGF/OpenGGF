package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Loader boundaries and explicit component inputs, without a user ROM or fallback. */
class TestS3kSlotLayoutData {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @Test void loaderReadsBothWordsAndAllBytesFromItsBoundedRomSource() {
        byte[] source = S3kSlotRomTestData.image();
        source[0x1E4080] = 4; source[0x1E4081] = 0x50;
        source[0x1E4082] = 3; source[0x1E4083] = 0x70;
        for (int i = 0; i < 1024; i++) source[0x1E4084 + i] = (byte) (i % 9);
        var bank = S3kSlotResources.fromRom(new RomByteReader(source));
        assertEquals((short) 0x450, bank.playerStartX());
        assertEquals((short) 0x370, bank.playerStartY());
        for (int i = 0; i < 1024; i++) assertEquals((byte) (i % 9), bank.layout()[i]);
        source[0x1E4084] = 99;
        assertEquals(0, bank.layout()[0]);
    }

    @Test void bankAndEachRuntimeBufferOwnIndependentLayoutCopies() {
        var bank = S3kSlotRomTestData.resources();
        bank.layout()[1] = 8;
        var first = S3kSlotRenderBuffers.fromRomData(bank);
        var second = S3kSlotRenderBuffers.fromRomData(bank);
        first.layout()[2] = 8;
        first.expandedLayout()[0x20 * 0x80 + 0x22] = 8;
        assertArrayEquals(S3kSlotRomTestData.SLOT_BONUS_LAYOUT, bank.layout());
        assertArrayEquals(S3kSlotRomTestData.SLOT_BONUS_LAYOUT, second.layout());
        assertArrayEquals(S3kSlotRomTestData.buildExpandedLayoutBuffer(), second.expandedLayout());
    }

    @Test void missingOrTruncatedSourcesCannotBecomeAStockBoard() {
        assertThrows(NullPointerException.class, () -> S3kSlotResources.fromRom(null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> S3kSlotResources.fromRom(new RomByteReader(new byte[0x1E4483])));
    }
}
