package com.openggf.editor.render;

import com.openggf.editor.EditorCollisionPath;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.CollisionMode;
import com.openggf.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds editor collision overlay data without touching global debug-overlay state. */
public final class EditorCollisionOverlayBuilder {
    private static final int CELL_SIZE = 16;

    public record Cell(int worldX, int worldY, CollisionMode mode, int solidTileIndex) {}

    public List<Cell> build(Level level,
                            EditorCollisionPath path,
                            int cameraX,
                            int cameraY,
                            int cameraWidth,
                            int cameraHeight,
                            boolean enabled) {
        if (!enabled || level == null || cameraWidth <= 0 || cameraHeight <= 0) {
            return List.of();
        }
        Objects.requireNonNull(path, "path");
        int blockSize = level.getBlockPixelSize();
        if (blockSize <= 0 || level.getMap().getWidth() <= 0 || level.getMap().getHeight() <= 0) {
            return List.of();
        }

        int cameraUnsignedX = cameraX & 0xFFFF;
        int cameraUnsignedY = cameraY & 0xFFFF;
        int firstDeltaX = (short) ((cameraUnsignedX & ~(CELL_SIZE - 1)) - cameraX);
        int firstDeltaY = (short) ((cameraUnsignedY & ~(CELL_SIZE - 1)) - cameraY);
        List<Cell> cells = new ArrayList<>();
        for (int deltaY = firstDeltaY; deltaY < cameraHeight; deltaY += CELL_SIZE) {
            for (int deltaX = firstDeltaX; deltaX < cameraWidth; deltaX += CELL_SIZE) {
                int lookupX = (cameraUnsignedX + deltaX) & 0xFFFF;
                int lookupY = (cameraUnsignedY + deltaY) & 0xFFFF;
                int mapX = lookupX / blockSize;
                int mapY = lookupY / blockSize;
                if (mapX >= level.getMap().getWidth() || mapY >= level.getMap().getHeight()) {
                    continue;
                }
                int rawBlockIndex = Byte.toUnsignedInt(level.getMap().getValue(0, mapX, mapY));
                int blockIndex = level.resolveCollisionBlockIndex(rawBlockIndex, mapX, mapY);
                if (blockIndex < 0 || blockIndex >= level.getBlockCount()) {
                    continue;
                }
                Block block = level.getBlock(blockIndex);
                int cellX = (lookupX % blockSize) / CELL_SIZE;
                int cellY = (lookupY % blockSize) / CELL_SIZE;
                if (cellX >= block.getGridSide() || cellY >= block.getGridSide()) {
                    continue;
                }
                ChunkDesc desc = block.getChunkDesc(cellX, cellY);
                if (desc.getChunkIndex() < 0 || desc.getChunkIndex() >= level.getChunkCount()) {
                    continue;
                }
                Chunk chunk = level.getChunk(desc.getChunkIndex());
                CollisionMode mode = path == EditorCollisionPath.PRIMARY
                        ? desc.getPrimaryCollisionMode() : desc.getSecondaryCollisionMode();
                int solidIndex = path == EditorCollisionPath.PRIMARY
                        ? chunk.getSolidTileIndex() : chunk.getSolidTileAltIndex();
                cells.add(new Cell(cameraX + deltaX, cameraY + deltaY, mode, solidIndex));
            }
        }
        return List.copyOf(cells);
    }
}
