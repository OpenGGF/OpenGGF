package com.openggf.level.resources;

import com.openggf.io.ModAssetRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLoadOpSources {

    @Test
    void romFactoriesProduceRomAddressSourcesAndCompatAccessor() {
        LoadOp op = LoadOp.kosinskiBase(0x123456);
        assertEquals(new LoadSource.RomAddress(0x123456), op.source());
        assertEquals(0x123456, op.romAddr());
        assertEquals(CompressionType.KOSINSKI, op.compressionType());
    }

    @Test
    void appendSentinelBehaviorIsUnchanged() {
        assertTrue(LoadOp.kosinskiAppend(0x10).appendsToPrevious());
        assertFalse(LoadOp.kosinskiBase(0x10).appendsToPrevious());
    }

    @Test
    void modAssetFactoriesAreUncompressedAndRejectRomAccessor() throws Exception {
        try (ModAssetRoot root = ModAssetRoot.forTests("m")) {
            LoadOp op = LoadOp.modAssetBase(root, "assets/patterns.bin");
            assertEquals(CompressionType.UNCOMPRESSED, op.compressionType());
            assertInstanceOf(LoadSource.ModAsset.class, op.source());
            assertThrows(IllegalStateException.class, op::romAddr);
            assertEquals(0x80, LoadOp.modAssetOverlay(root, "a.bin", 0x80).destOffsetBytes());
            assertTrue(LoadOp.modAssetAppend(root, "a.bin").appendsToPrevious());
        }
    }

    @Test
    void canonicalConstructorRejectsInvalidOperations() throws Exception {
        try (ModAssetRoot root = ModAssetRoot.forTests("m")) {
            assertThrows(NullPointerException.class,
                    () -> new LoadOp(null, CompressionType.UNCOMPRESSED, 0));
            assertThrows(NullPointerException.class,
                    () -> new LoadOp(new LoadSource.RomAddress(1), null, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new LoadOp(new LoadSource.RomAddress(1), CompressionType.UNCOMPRESSED, -2));
            assertThrows(IllegalArgumentException.class,
                    () -> new LoadOp(new LoadSource.ModAsset(root, "a.bin"), CompressionType.KOSINSKI, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new LoadSource.ModAsset(root, "../escape.bin"));
        }
    }

    @Test
    void planRejectsOverlayOrAppendBeforeBase() {
        assertThrows(IllegalStateException.class, () -> LevelResourcePlan.builder()
                .addPatternOp(LoadOp.kosinskiOverlay(1, 32))
                .addBlockOp(LoadOp.kosinskiBase(2))
                .addChunkOp(LoadOp.kosinskiBase(3))
                .build());
        assertThrows(IllegalStateException.class, () -> LevelResourcePlan.builder()
                .addPatternOp(LoadOp.kosinskiAppend(1))
                .addBlockOp(LoadOp.kosinskiBase(2))
                .addChunkOp(LoadOp.kosinskiBase(3))
                .build());
    }

    @Test
    void planRejectsSecondBaseAfterCompositionHasStarted() {
        assertThrows(IllegalStateException.class, () -> LevelResourcePlan.builder()
                .addPatternOp(LoadOp.kosinskiBase(1))
                .addPatternOp(LoadOp.kosinskiOverlay(2, 32))
                .addPatternOp(LoadOp.kosinskiBase(3))
                .addBlockOp(LoadOp.kosinskiBase(4))
                .addChunkOp(LoadOp.kosinskiBase(5))
                .build());
    }

}
