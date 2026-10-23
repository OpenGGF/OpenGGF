package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.soniccd.level.CdR11aTerrain.Plane.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdR11aTerrain {
    @Test
    void nativeWordsNibblesAndSignedCollisionSamplesRetainTheirMeaning() {
        Fixture f = new Fixture();
        word(f.blocks, 0, 0xFB1F); // pattern 799, both flips, palette 3, priority.
        f.patterns[799 * 32] = (byte) 0xA3;
        word(f.chunks, 0, 0x7B23); // block 803, both flips, both solidity bits.
        f.ids[803] = (byte) 255;
        f.angles[255] = (byte) 0xE1;
        f.heights[255 * 16] = (byte) 0xF0;
        f.widths[255 * 16 + 15] = (byte) 0xFF;
        var terrain = f.decode();
        var block = terrain.blockWord(0, 0, 0);
        assertEquals(0xFB1F, block.rawWord());
        assertEquals(799, block.patternIndex());
        assertTrue(block.flipX());
        assertTrue(block.flipY());
        assertTrue(block.priority());
        assertEquals(3, block.paletteLine());
        assertEquals(10, terrain.pixel(799, 0, 0));
        assertEquals(3, terrain.pixel(799, 1, 0));
        var chunk = terrain.chunkWord(1, 0, 0);
        assertEquals(803, chunk.renderBlockIndex());
        assertEquals(803, chunk.collisionBlockIndex());
        assertTrue(chunk.flipX());
        assertTrue(chunk.flipY());
        assertEquals(3, chunk.solidity());
        assertEquals(255, terrain.collision().idForBlock(803));
        assertEquals(0xE1, terrain.collision().angle(255));
        assertEquals(-16, terrain.collision().height(255, 0));
        assertEquals(-1, terrain.collision().width(255, 15));
        assertEquals(804, terrain.blockCount());
        assertEquals(800, terrain.patternCount());
        assertEquals(121, terrain.chunkCount());
    }

    @Test
    void layoutsMaskFlagsUseOneBasedChunksAndPackIntoClearedNativeRam() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9; // loop flag + native ID 121, the last real chunk.
        f.bg[2] = 1;
        f.fg[2 + 42] = 2;
        var terrain = f.decode();
        var layout = terrain.layout();
        assertEquals(121, layout.cell(FOREGROUND, 0, 0).chunkId());
        assertTrue(layout.cell(FOREGROUND, 0, 0).loopFlag());
        assertEquals(1, layout.cell(BACKGROUND, 0, 0).chunkId());
        assertEquals(0xF9, layout.ram().unsignedByte(0));
        assertEquals(1, layout.ram().unsignedByte(0x40));
        assertEquals(2, layout.ram().unsignedByte(0x80));
        assertEquals(0, layout.ram().unsignedByte(42));
        assertEquals(0, layout.ram().unsignedByte(0x46));
        assertEquals(0, layout.ram().unsignedByte(0x400));
        assertEquals(0, layout.nativeCell(BACKGROUND, 63, 15).chunkId());
        assertTrue(layout.cell(FOREGROUND, 1, 0).blank());
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.chunkWord(0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.chunkWord(122, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.cell(FOREGROUND, 42, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.nativeCell(FOREGROUND, 0, 16));
    }

    @Test
    void unreferencedChunkWordsRemainRawWithoutInventingUnloadedBlockData() {
        Fixture f = new Fixture();
        word(f.chunks, 120 * 512, 1020);
        var terrain = f.decode();
        assertEquals(1020, terrain.chunkWord(121, 0, 0).renderBlockIndex());
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.blockWord(1020, 0, 0));
        f.fg[2] = 121;
        assertThrows(IllegalArgumentException.class, f::decode);
    }

    @Test
    void invalidReferencesDimensionsAndPartialRecordsFailClosed() {
        Fixture f = new Fixture();
        f.fg[2] = 122;
        assertThrows(IllegalArgumentException.class, f::decode);
        f.fg[2] = 1;
        word(f.chunks, 0, 804);
        assertThrows(IllegalArgumentException.class, f::decode);
        word(f.chunks, 0, 0x400); // drawing masks to 0, collision would use ID 1024.
        assertThrows(IllegalArgumentException.class, f::decode);
        word(f.chunks, 0, 0);
        word(f.blocks, 0, 800);
        assertThrows(IllegalArgumentException.class, f::decode);
        word(f.blocks, 0, 0);
        f.bg[0] = 6;
        assertThrows(IllegalArgumentException.class, f::decode);
        f.bg[0] = 5;
        f.blocks = Arrays.copyOf(f.blocks, 6431);
        assertThrows(IllegalArgumentException.class, f::decode);
    }

    @Test
    void terrainOwnsAllNineInputArraysAndBoundsEveryCoordinate() {
        Fixture f = new Fixture();
        var terrain = f.decode();
        for (byte[] input : new byte[][]{f.blocks, f.patterns, f.chunks, f.fg, f.bg, f.ids, f.angles, f.heights, f.widths}) {
            Arrays.fill(input, (byte) 0xFF);
        }
        assertEquals(0, terrain.blockWord(0, 0, 0).rawWord());
        assertEquals(0, terrain.pixel(0, 0, 0));
        assertEquals(0, terrain.chunkWord(1, 0, 0).rawWord());
        assertEquals(0, terrain.layout().cell(FOREGROUND, 0, 0).rawByte());
        assertEquals(0, terrain.collision().idForBlock(0));
        assertEquals(0, terrain.collision().angle(0));
        assertEquals(0, terrain.collision().height(0, 0));
        assertEquals(0, terrain.collision().width(0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.blockWord(0, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.chunkWord(1, 16, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.pixel(800, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.collision().idForBlock(900));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.collision().height(0, 16));
    }

    static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    private static final class Fixture {
        byte[] blocks = new byte[6432];
        final byte[] patterns = new byte[25600];
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Fixture() {
            fg[0] = 41;
            fg[1] = 7;
            bg[0] = 5;
            bg[1] = 7;
        }

        CdR11aTerrain decode() {
            return CdR11aTerrain.decode(blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        }
    }
}
