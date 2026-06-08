package com.openggf.tests;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.Sonic1ObjectPlacement;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TestSonic1ObjectPlacementPlatformLists {
    @Test
    void lzPlatformChildrenLoadFromObjPosIndexRelativePointer() {
        byte[] rom = new byte[Sonic1Constants.OBJ_POS_INDEX_ADDR + 0x300];
        int listAddr = Sonic1Constants.OBJ_POS_INDEX_ADDR + 0x120;
        writeWord(rom, Sonic1Constants.OBJ_POS_LZ_PLATFORM_INDEX_ADDR + 4, 0x0120);
        writeWord(rom, listAddr, 1);
        writeEntry(rom, listAddr + 2, 0x0D22, 0x0483, 0x8021);
        writeEntry(rom, listAddr + 8, 0x0D9C, 0x0482, 0x0020);

        int[][] actual = new Sonic1ObjectPlacement(new RomByteReader(rom)).loadLzPlatformChildren(2);

        assertArrayEquals(new int[][] {
                {0x0D22, 0x0483, 0x21},
                {0x0D9C, 0x0482, 0x20}
        }, actual);
    }

    @Test
    void sbzPlatformChildrenLoadFromObjPosIndexRelativePointer() {
        byte[] rom = new byte[Sonic1Constants.OBJ_POS_INDEX_ADDR + 0x300];
        int listAddr = Sonic1Constants.OBJ_POS_INDEX_ADDR + 0x180;
        writeWord(rom, Sonic1Constants.OBJ_POS_SBZ_PLATFORM_INDEX_ADDR + 10, 0x0180);
        writeWord(rom, listAddr, 0);
        writeEntry(rom, listAddr + 2, 0x1C14, 0x05E0, 0x0050);

        int[][] actual = new Sonic1ObjectPlacement(new RomByteReader(rom)).loadSbzPlatformChildren(5);

        assertArrayEquals(new int[][] {
                {0x1C14, 0x05E0, 0x50}
        }, actual);
    }

    private static void writeEntry(byte[] rom, int offset, int x, int y, int subtypeWord) {
        writeWord(rom, offset, x);
        writeWord(rom, offset + 2, y);
        writeWord(rom, offset + 4, subtypeWord);
    }

    private static void writeWord(byte[] rom, int offset, int value) {
        rom[offset] = (byte) (value >>> 8);
        rom[offset + 1] = (byte) value;
    }
}
