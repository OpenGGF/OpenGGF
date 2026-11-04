package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.level.resources.LevelResourcePlan;
import com.openggf.level.resources.LoadOp;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real ROM-pipeline decode remains behind the native terrain-ready continuation. */
class TestSonic3kDeferredTerrainMaterialization {
    private static final int PATTERNS = 0x100;
    private static final int CHUNKS = 0x200;
    private static final int BLOCKS = 0x300;
    private static final int ROM_SIZE = 0xA0000;

    @Test
    void deferredPrefixHasNoGeometryAndMaterializesFromOriginalCartridgeAfterClose() throws Exception {
        Sonic3kLevel deferred;
        Sonic3kLevel immediate;
        try (Rom rom = cartridge()) {
            immediate = level(rom, BLOCKS, false);
            deferred = level(rom, BLOCKS, true);
            assertFalse(immediate.hasPendingTerrainMaterialization());
            assertTrue(deferred.hasPendingTerrainMaterialization());
            assertEquals(0, deferred.getChunkCount());
            assertEquals(0, deferred.getBlockCount());
            assertNull(deferred.getMap());
            assertEquals(immediate.getPatternCount(), deferred.getPatternCount());
            assertEquals(immediate.getSolidTileCount(), deferred.getSolidTileCount());
        }
        deferred.materializeTerrain();
        assertFalse(deferred.hasPendingTerrainMaterialization());
        assertEquals(immediate.getChunkCount(), deferred.getChunkCount());
        assertEquals(immediate.getBlockCount(), deferred.getBlockCount());
        assertArrayEquals(immediate.getChunk(0).saveState(), deferred.getChunk(0).saveState());
        assertArrayEquals(immediate.getBlock(0).saveState(), deferred.getBlock(0).saveState());
        assertEquals(immediate.getMap().getWidth(), deferred.getMap().getWidth());
        assertEquals(immediate.getMap().getHeight(), deferred.getMap().getHeight());
        var map = deferred.getMap();
        var chunk = deferred.getChunk(0);
        deferred.materializeTerrain();
        assertSame(map, deferred.getMap(), "successful materialization must not decode again");
        assertSame(chunk, deferred.getChunk(0));
    }

    @Test
    void lateDecodeFailureCannotPublishPartialGeometryOrBeRetried() throws Exception {
        try (Rom rom = cartridge()) {
            Sonic3kLevel deferred = level(rom, ROM_SIZE + 8, true);
            assertThrows(IOException.class, deferred::materializeTerrain);
            assertTrue(deferred.hasPendingTerrainMaterialization(), "failure is never terrain readiness");
            assertEquals(0, deferred.getChunkCount(), "the earlier successful chunk decode is discarded");
            assertEquals(0, deferred.getBlockCount());
            assertNull(deferred.getMap());
            assertFalse(deferred.hasEncodedCollisionLayout());
            assertThrows(IllegalStateException.class, deferred::materializeTerrain);
        }
    }

    @Test
    void existingConstructorStillDecodesBeforeReturning() throws Exception {
        try (Rom rom = cartridge()) {
            var level = new Sonic3kLevel(rom, 0, plan(BLOCKS), 0, 0, false,
                    0, 0, 0, 0, null, List.of(), List.of(), null, false);
            assertFalse(level.hasPendingTerrainMaterialization());
            assertEquals(1, level.getChunkCount());
            assertEquals(1, level.getBlockCount());
            assertNotNull(level.getMap());
        }
    }

    private static Sonic3kLevel level(Rom rom, int blocks, boolean deferred) throws Exception {
        return new Sonic3kLevel(rom, 0, plan(blocks), 0, 0, false, 0, 0, 0, 0,
                null, List.of(), List.of(), null, false,
                new Sonic3kLevel.TerrainArtSources(PATTERNS, PATTERNS),
                new byte[0], new byte[0], Sonic3kLevel.ResidentChunkPatterns.EMPTY, deferred);
    }

    private static LevelResourcePlan plan(int blocks) {
        return LevelResourcePlan.builder()
                .addPatternOp(LoadOp.kosinskiBase(PATTERNS))
                .addChunkOp(LoadOp.kosinskiBase(CHUNKS))
                .addBlockOp(LoadOp.kosinskiBase(blocks)).build();
    }

    private static Rom cartridge() {
        byte[] bytes = new byte[ROM_SIZE];
        zeroArchive(bytes, PATTERNS, 32);
        zeroArchive(bytes, CHUNKS, 8);
        zeroArchive(bytes, BLOCKS, 128);
        return Rom.fromReader(RomByteReader.fromBytes(bytes), "deferred terrain regression");
    }

    private static void zeroArchive(byte[] rom, int address, int outputLength) {
        // One literal zero, a distance-one extended copy, then the native terminator.
        byte[] archive = {0x15, 0, 0, (byte) 0xff, (byte) 0xf8,
                (byte) (outputLength - 2), 0, 0, 0};
        System.arraycopy(archive, 0, rom, address, archive.length);
    }
}
