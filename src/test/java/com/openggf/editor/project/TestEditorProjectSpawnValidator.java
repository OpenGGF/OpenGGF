package com.openggf.editor.project;

import com.openggf.game.GameId;
import com.openggf.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorProjectSpawnValidator {
    @ParameterizedTest
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void occupiedBlockZeroRejectsEmbeddedStartButClearSpaceRemainsUsable(GameId game) {
        Level level = level(game, 0x3001);
        Arrays.fill(level.getSolidTile(1).heights, (byte) 16);
        assertTrue(EditorProjectSpawnValidator.insideTerrain(level, 8, 8));
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 32, 8));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> EditorProjectSpawnValidator.validate(level, 8, 8));
        assertTrue(failure.getMessage().contains("solid terrain"));
        assertDoesNotThrow(() -> EditorProjectSpawnValidator.validate(level, 64, 64));
    }

    @Test
    void signedProfilesAndBothDescriptorFlipsLocateOccupiedPixels() {
        Level level = level(GameId.S2, 0x3001);
        level.getSolidTile(1).heights[3] = 4;
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 3, 11));
        assertTrue(EditorProjectSpawnValidator.insideTerrain(level, 3, 12));
        level.getBlock(0).getChunkDesc(0, 0).set(0x3401);
        assertTrue(EditorProjectSpawnValidator.insideTerrain(level, 12, 12));
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 3, 12));
        level.getBlock(0).getChunkDesc(0, 0).set(0x3801);
        assertTrue(EditorProjectSpawnValidator.insideTerrain(level, 3, 3));
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 3, 4));
        level.getSolidTile(1).heights[3] = -4;
        level.getBlock(0).getChunkDesc(0, 0).set(0x3001);
        assertTrue(EditorProjectSpawnValidator.insideTerrain(level, 3, 3));
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 3, 4));
        level.getBlock(0).getChunkDesc(0, 0).set(0x3801);
        assertTrue(EditorProjectSpawnValidator.insideTerrain(level, 3, 12));
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 3, 11));
    }

    @Test
    void nonSolidArtDoesNotRejectAStart() {
        Level level = level(GameId.S2, 1);
        Arrays.fill(level.getSolidTile(1).heights, (byte) 16);
        assertFalse(EditorProjectSpawnValidator.insideTerrain(level, 8, 8));
        assertDoesNotThrow(() -> EditorProjectSpawnValidator.validate(level, 8, 8));
    }

    @Test
    void nativeCollisionDelegateAlsoControlsPlaytestBodyClearance() {
        Level nativeLevel = org.mockito.Mockito.spy(level(GameId.S1, 0x3001));
        Arrays.fill(nativeLevel.getSolidTile(1).heights, (byte) 16);
        assertTrue(EditorProjectSpawnValidator.insideTerrain(nativeLevel, 8, 8),
                "Rendered block 0 is solid before selecting the native collision remap");
        nativeLevel.getBlock(1).setChunkDesc(0, 0, new com.openggf.level.ChunkDesc(0));
        org.mockito.Mockito.doReturn(1).when(nativeLevel).resolveCollisionBlockIndex(0, 0, 0);
        assertFalse(EditorProjectSpawnValidator.insideTerrain(nativeLevel, 8, 8),
                "The native collision remap selects clear block 1 rather than rendered solid block 0");
        assertFalse(EditorProjectSpawnValidator.insideTerrain(
                com.openggf.level.MutableLevel.snapshot(nativeLevel), 8, 8));
    }

    @Test
    void nativeCameraOriginBoundsDoNotRestrictValidNativePlayerStarts() {
        Level nativeLevel = org.mockito.Mockito.spy(level(GameId.S1, 0));
        org.mockito.Mockito.doReturn(32).when(nativeLevel).getMaxY();
        assertThrows(IllegalArgumentException.class, () -> EditorProjectSpawnValidator.validate(nativeLevel, 64, 64));
        assertDoesNotThrow(() -> EditorProjectSpawnValidator.validateNative(nativeLevel, 64, 64));
        int width = nativeLevel.getMap().getWidth() * nativeLevel.getBlockPixelSize();
        var failure = assertThrows(IllegalArgumentException.class,
                () -> EditorProjectSpawnValidator.validateNative(nativeLevel, width, 64));
        assertTrue(failure.getMessage().contains("bounds"));
    }

    @Test
    void invalidBoundsFailBeforeRuntimeCreation() {
        Level level = level(GameId.S2, 1);
        for (int[] point : new int[][] {{-1, 64}, {64, -1}, {level.getMaxX() + 1, 64}, {64, level.getMaxY() + 1}}) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> EditorProjectSpawnValidator.validate(level, point[0], point[1]));
            assertTrue(failure.getMessage().contains("bounds"));
        }
    }

    private static Level level(GameId game, int descriptor) {
        var level = new TestEditorProjects.Fixture(game);
        level.getMap().setValue(0, 0, 0, (byte) 0);
        level.getBlock(0).setChunkDesc(0, 0, new com.openggf.level.ChunkDesc(descriptor));
        Arrays.fill(level.getSolidTile(1).heights, (byte) 0);
        return level;
    }
}
