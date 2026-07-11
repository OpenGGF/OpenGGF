package com.openggf.editor;

import com.openggf.editor.commands.CycleCellCollisionModeCommand;
import com.openggf.editor.commands.SetChunkSolidTileIndexCommand;
import com.openggf.editor.render.EditorCollisionOverlayBuilder;
import com.openggf.editor.render.EditorWorldOverlayRenderer;
import com.openggf.graphics.GLCommand;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.CollisionMode;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestEditorCollisionCommands {

    @Test
    void cycleCellCollisionModeTraversesAllFourPrimaryModesAndUndoIsExact() {
        MutableLevel level = level();
        int untouchedRaw = level.getBlock(0).getChunkDesc(1, 0).get();

        for (int expected = 1; expected <= 4; expected++) {
            CycleCellCollisionModeCommand command = new CycleCellCollisionModeCommand(
                    level, 0, 0, EditorCollisionPath.PRIMARY);
            int before = level.getBlock(0).getChunkDesc(0, 0).get();
            command.apply();
            assertEquals(expected & 3,
                    level.getBlock(0).getChunkDesc(0, 0).getPrimaryCollisionMode().getValue());
            assertEquals(untouchedRaw, level.getBlock(0).getChunkDesc(1, 0).get());
            command.undo();
            assertEquals(before, level.getBlock(0).getChunkDesc(0, 0).get());
            command.apply();
        }
    }

    @Test
    void secondaryModeUsesHighBitsAndMarksBlockMapAndSaveState() {
        MutableLevel level = level();
        level.markSaved();
        CycleCellCollisionModeCommand command = new CycleCellCollisionModeCommand(
                level, 0, 0, EditorCollisionPath.SECONDARY);

        command.apply();

        ChunkDesc changed = level.getBlock(0).getChunkDesc(0, 0);
        assertEquals(CollisionMode.TOP_SOLID, changed.getSecondaryCollisionMode());
        assertEquals(CollisionMode.NO_COLLISION, changed.getPrimaryCollisionMode());
        assertTrue(level.consumeDirtyBlocks().get(0));
        assertFalse(level.consumeDirtyMapCells().isEmpty());
        assertTrue(level.modifiedBlocksSinceBaseline().get(0));
        assertTrue(level.isModifiedSinceLastSave());
    }

    @Test
    void solidTileIndexRoundTripsBothPathsAndPropagatesDirtyState() {
        MutableLevel level = level();
        level.markSaved();
        SetChunkSolidTileIndexCommand primary = new SetChunkSolidTileIndexCommand(
                level, 0, EditorCollisionPath.PRIMARY, 55);
        primary.apply();
        SetChunkSolidTileIndexCommand secondary = new SetChunkSolidTileIndexCommand(
                level, 0, EditorCollisionPath.SECONDARY, 60);
        secondary.apply();
        assertEquals(55, level.getChunk(0).getSolidTileIndex());
        assertEquals(60, level.getChunk(0).getSolidTileAltIndex());
        assertTrue(level.consumeDirtyChunks().get(0));
        assertTrue(level.consumeDirtyBlocks().get(0));
        assertFalse(level.consumeDirtyMapCells().isEmpty());
        assertTrue(level.modifiedChunksSinceBaseline().get(0));
        assertTrue(level.isModifiedSinceLastSave());

        secondary.undo();
        primary.undo();
        assertEquals(11, level.getChunk(0).getSolidTileIndex());
        assertEquals(22, level.getChunk(0).getSolidTileAltIndex());
    }

    @Test
    void solidTileCommandRejectsIndexesOutsideLoadedLevelTable() {
        MutableLevel level = level();

        assertThrows(IllegalArgumentException.class, () -> new SetChunkSolidTileIndexCommand(
                level, 0, EditorCollisionPath.PRIMARY, 64));
        assertThrows(IllegalArgumentException.class, () -> new SetChunkSolidTileIndexCommand(
                level, 0, EditorCollisionPath.PRIMARY, -1));
    }

    @Test
    void commandsLeaveSnapshotHeldBlockAndChunkReferencesUnchanged() {
        MutableLevel level = level();
        Block snapshotBlock = level.getBlock(0);
        Chunk snapshotChunk = level.getChunk(0);
        int[] blockState = snapshotBlock.saveState();
        int[] chunkState = snapshotChunk.saveState();
        level.bumpEpoch();

        new CycleCellCollisionModeCommand(level, 0, 0, EditorCollisionPath.PRIMARY).apply();
        new SetChunkSolidTileIndexCommand(level, 0, EditorCollisionPath.PRIMARY, 63).apply();

        assertNotSame(snapshotBlock, level.getBlock(0));
        assertNotSame(snapshotChunk, level.getChunk(0));
        assertEquals(List.of(blockState[0], blockState[1], blockState[2], blockState[3]),
                java.util.Arrays.stream(snapshotBlock.saveState()).boxed().toList());
        assertEquals(List.of(chunkState[0], chunkState[1], chunkState[2], chunkState[3],
                        chunkState[4], chunkState[5]),
                java.util.Arrays.stream(snapshotChunk.saveState()).boxed().toList());
    }

    @Test
    void controllerInputActionsDriveCollisionCommandsAndOverlayToggle() {
        MutableLevel level = level();
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.selectBlock(0);
        controller.selectChunk(0);
        controller.descend();
        EditorInputHandler input = new EditorInputHandler(controller);

        input.handleAction(EditorInputHandler.Action.TOGGLE_COLLISION_OVERLAY);
        input.handleAction(EditorInputHandler.Action.CYCLE_COLLISION_MODE);
        input.handleAction(EditorInputHandler.Action.INCREMENT_SOLID_TILE_INDEX);

        assertTrue(controller.isCollisionOverlayEnabled());
        assertEquals(CollisionMode.TOP_SOLID,
                level.getBlock(0).getChunkDesc(0, 0).getPrimaryCollisionMode());
        assertEquals(12, level.getChunk(0).getSolidTileIndex());
        controller.undo();
        assertEquals(11, level.getChunk(0).getSolidTileIndex());
    }

    @Test
    void solidIndexInputIsNoOpAtWorldDepthAndAtPathBoundary() {
        MutableLevel level = level();
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.selectBlock(0);
        controller.selectChunk(0);
        EditorInputHandler input = new EditorInputHandler(controller);
        level.markSaved();

        input.handleAction(EditorInputHandler.Action.INCREMENT_SOLID_TILE_INDEX);
        assertEquals(11, level.getChunk(0).getSolidTileIndex());
        assertFalse(controller.hasUndoHistory());
        assertFalse(level.isModifiedSinceLastSave());

        controller.selectChunk(2);
        controller.descend();
        input.handleAction(EditorInputHandler.Action.DECREMENT_SOLID_TILE_INDEX);
        assertEquals(0, level.getChunk(2).getSolidTileIndex());
        assertFalse(controller.hasUndoHistory());
        assertFalse(level.isModifiedSinceLastSave());
        assertTrue(level.consumeDirtyChunks().isEmpty());
    }

    @Test
    void collisionOverlayBuilderUsesExplicitVisibilityAndToggleInputs() {
        MutableLevel level = level();
        EditorCollisionOverlayBuilder builder = new EditorCollisionOverlayBuilder();

        List<EditorCollisionOverlayBuilder.Cell> cells = builder.build(
                level, EditorCollisionPath.PRIMARY, 0, 0, 31, 16, true);

        assertEquals(2, cells.size());
        assertEquals(new EditorCollisionOverlayBuilder.Cell(
                0, 0, CollisionMode.NO_COLLISION, 11), cells.get(0));
        assertEquals(new EditorCollisionOverlayBuilder.Cell(
                16, 0, CollisionMode.TOP_SOLID, 33), cells.get(1));
        assertTrue(builder.build(level, EditorCollisionPath.PRIMARY,
                0, 0, 31, 16, false).isEmpty());
        assertEquals(1, builder.build(level, EditorCollisionPath.PRIMARY,
                16, 0, 16, 16, true).size());
        assertEquals(1, builder.build(level, EditorCollisionPath.PRIMARY,
                8, 0, 8, 16, true).size(), "partially visible cells remain in the overlay");
    }

    @Test
    void collisionOverlayBuilderUsesUnsignedLookupAndCameraDomainAcross8000() {
        MutableLevel level = level();
        EditorCollisionOverlayBuilder builder = new EditorCollisionOverlayBuilder();

        List<EditorCollisionOverlayBuilder.Cell> beforeBoundary = builder.build(
                level, EditorCollisionPath.PRIMARY, 0x7FF0, 0, 48, 16, true);
        List<EditorCollisionOverlayBuilder.Cell> afterBoundary = builder.build(
                level, EditorCollisionPath.PRIMARY, (short) 0x8000, 0, 32, 16, true);

        assertEquals(List.of(0x7FF0, 0x8000, 0x8010),
                beforeBoundary.stream().map(EditorCollisionOverlayBuilder.Cell::worldX).toList());
        assertEquals(List.of(-0x8000, -0x7FF0),
                afterBoundary.stream().map(EditorCollisionOverlayBuilder.Cell::worldX).toList());
        assertEquals(List.of(33, 11, 33),
                beforeBoundary.stream().map(EditorCollisionOverlayBuilder.Cell::solidTileIndex).toList());
        assertEquals(List.of(11, 33),
                afterBoundary.stream().map(EditorCollisionOverlayBuilder.Cell::solidTileIndex).toList());
        List<GLCommand> rendered = new InspectableWorldOverlayRenderer().commands(afterBoundary);
        assertTrue(rendered.stream().anyMatch(command -> (int) command.getX1() == -0x8000));
    }

    private static final class InspectableWorldOverlayRenderer extends EditorWorldOverlayRenderer {
        List<GLCommand> commands(List<EditorCollisionOverlayBuilder.Cell> cells) {
            List<GLCommand> commands = new java.util.ArrayList<>();
            appendCollisionOverlayCommands(commands, cells);
            return commands;
        }
    }

    private static MutableLevel level() {
        return MutableLevel.snapshot(new CollisionLevel());
    }

    private static final class CollisionLevel extends AbstractLevel {
        CollisionLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] { new Pattern() };
            chunkCount = 4;
            chunks = new Chunk[chunkCount];
            for (int i = 0; i < chunkCount; i++) {
                chunks[i] = new Chunk();
            }
            chunks[0].restoreState(new int[] {0, 0, 0, 0, 11, 22});
            chunks[1].restoreState(new int[] {0, 0, 0, 0, 33, 44});

            blockCount = 1;
            blocks = new Block[] { new Block(2) };
            blocks[0].setChunkDesc(0, 0, new ChunkDesc(0));
            blocks[0].setChunkDesc(1, 0, new ChunkDesc(1 | 0x1000));
            blocks[0].setChunkDesc(0, 1, new ChunkDesc(2));
            blocks[0].setChunkDesc(1, 1, new ChunkDesc(3));

            solidTileCount = 64;
            solidTiles = new SolidTile[solidTileCount];
            for (int i = 0; i < solidTiles.length; i++) {
                solidTiles[i] = new SolidTile(i, new byte[SolidTile.TILE_SIZE_IN_ROM],
                        new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0);
            }
            map = new Map(1, 1025, 1);
            map.setValue(0, 0, 0, (byte) 0);
            map.setValue(0, 1023, 0, (byte) 0);
            map.setValue(0, 1024, 0, (byte) 0);
            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < palettes.length; i++) palettes[i] = new Palette();
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 31;
            minY = 0;
            maxY = 31;
        }

        @Override public int getChunksPerBlockSide() { return 2; }
        @Override public int getBlockPixelSize() { return 32; }
    }
}
