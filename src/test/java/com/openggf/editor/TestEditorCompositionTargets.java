package com.openggf.editor;

import com.openggf.level.ChunkDesc;
import com.openggf.level.MutableLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Composition tools follow the selected canvas cell rather than a stale target or brush. */
class TestEditorCompositionTargets {
    @Test
    void changingLayerAtBlockDepthEditsTheNewLayerAndKeepsTheTerrainBrush() {
        MutableLevel level = TestEditorInteractionSafety.level();
        level.setBlockInMap(1, 0, 0, 1);
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.selectBlock(0);
        controller.descend();

        controller.toggleActiveLayer();
        controller.toggleDescriptorHFlip();

        int edited = Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0));
        assertNotEquals(1, edited, "The BG placement must receive a local composition definition");
        assertTrue(level.getBlock(edited).getChunkDesc(0, 0).getHFlip());
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
        assertFalse(level.getBlock(0).getChunkDesc(0, 0).getHFlip());
        assertFalse(level.getBlock(1).getChunkDesc(0, 0).getHFlip());
        assertEquals(0, controller.selectedBlockIndex(), "Changing editing targets preserves the world brush");
        controller.undo();
        assertEquals(1, Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0)));
        controller.redo();
        assertEquals(edited, Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0)));
    }

    @Test
    void shapeIncrementStartsFromTheTargetCellProfileInsteadOfTheChunkBrush() {
        MutableLevel level = TestEditorInteractionSafety.level();
        level.setChunkInBlock(0, 1, 0, new ChunkDesc(1));
        int[] state = level.getChunk(1).saveState();
        state[4] = 1;
        level.restoreChunkState(1, state);
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.selectBlock(0);
        controller.selectChunk(0);
        controller.descend();
        controller.moveActiveSelection(1, 0);

        controller.adjustSelectedChunkSolidTileIndex(1);

        int editedBlock = Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0));
        int editedChunk = level.getBlock(editedBlock).getChunkDesc(1, 0).getChunkIndex();
        assertEquals(2, level.getChunk(editedChunk).getSolidTileIndex(),
                "The selected cell's shape 1 increments to 2, independent of brush shape 0");
        assertEquals(0, controller.chunkBrushIndex());
        assertEquals(1, level.getChunk(1).getSolidTileIndex(), "Shared source chunk stays intact");
        assertEquals(0, level.getChunk(0).getSolidTileIndex(), "Brush definition stays intact");
        controller.undo();
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
        controller.redo();
        assertEquals(2, level.getChunk(editedChunk).getSolidTileIndex());
    }
}
