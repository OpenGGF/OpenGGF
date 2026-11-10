package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Value controls only. Authored Java UNRUN; clear transforms confer no original authority. */
class TestCdBlockDefinitionStaging {
    @Test void unknownRejectsFirstByteAndWordInsteadOfReturningBackingZero() {
        var value = CdBlockDefinitionStaging.unknown();
        assertEquals(0, assertThrows(CdBlockDefinitionStaging.UnknownRead.class,
                () -> value.unsignedByte(0)).byteOffset());
        assertEquals(0, assertThrows(CdBlockDefinitionStaging.UnknownRead.class,
                () -> value.unsignedWord(0)).byteOffset());
        assertThrows(CdBlockDefinitionStaging.UnknownRead.class, () -> value.blockWord(1023, 3));
    }
    @Test void immutableCompletedRowsRetainUnknownSuffixAndCapturedPreimage() {
        byte[] row = {1, 2, 3, 4};
        var before = CdBlockDefinitionStaging.unknown();
        var after = before.withCompletedPrefix(CdAssetBytes.copyOf(row));
        row[0] = 99;
        assertEquals(0x0102, after.unsignedWord(0));
        assertEquals(0x0304, after.unsignedWord(2));
        assertThrows(CdBlockDefinitionStaging.UnknownRead.class, () -> before.unsignedByte(0));
        assertEquals(4, assertThrows(CdBlockDefinitionStaging.UnknownRead.class,
                () -> after.unsignedWord(4)).byteOffset());
        assertEquals(4, assertThrows(CdBlockDefinitionStaging.UnknownRead.class,
                () -> after.unsignedWord(3)).byteOffset());
        byte[] mask = after.knownMask().copy(); mask[0] = 0;
        assertEquals(1, after.unsignedByte(0));
        assertThrows(IllegalArgumentException.class,
                () -> after.withCompletedPrefix(CdAssetBytes.copyOf(new byte[3])));
    }
    @Test void fullClearAndReachedWritesRetainSuffixAndAnotherClearResetsDirtyData() {
        var cleared = CdBlockDefinitionStaging.afterFileBufferClear();
        for (int at = 0; at < 8192; at += 2) assertEquals(0, cleared.unsignedWord(at));
        byte[] full = new byte[8192]; full[8191] = 91;
        var dirty = cleared.withCompletedPrefix(CdAssetBytes.copyOf(full));
        var restarted = dirty.withCompletedPrefix(CdAssetBytes.copyOf(new byte[]{1,2,3,4}));
        assertEquals(91, restarted.unsignedByte(8191));
        assertEquals(0, CdBlockDefinitionStaging.afterFileBufferClear().unsignedByte(8191));
        assertEquals(91, dirty.unsignedByte(8191));
        assertEquals(0, cleared.unsignedByte(0));
    }
    @Test void byteAndWordBoundsAndBlockSelectionAreExplicit() {
        var value = CdBlockDefinitionStaging.unknown();
        assertThrows(IndexOutOfBoundsException.class, () -> value.unsignedByte(8192));
        assertThrows(IndexOutOfBoundsException.class, () -> value.unsignedWord(8191));
        assertThrows(IndexOutOfBoundsException.class, () -> value.blockWord(1024,0));
        assertThrows(IndexOutOfBoundsException.class, () -> value.blockWord(0,4));
        assertThrows(IllegalArgumentException.class,
                () -> value.withCompletedPrefix(CdAssetBytes.copyOf(new byte[8196])));
    }
}
