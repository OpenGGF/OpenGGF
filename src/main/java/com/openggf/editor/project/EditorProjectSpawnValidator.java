package com.openggf.editor.project;

import com.openggf.level.Level;

/** Rejects an authored spawn embedded in terrain; never modifies runtime physics or project data. */
public final class EditorProjectSpawnValidator {
    private EditorProjectSpawnValidator() { }

    public static void validate(Level level, int x, int y) {
        if (x < level.getMinX() || x > level.getMaxX() || y < level.getMinY() || y > level.getMaxY())
            throw new IllegalArgumentException("Playtest start must lie inside the authored bounds");
        validateBody(level, x, y);
    }

    /** Native limits name camera origins; player starts instead belong to the loaded terrain domain. */
    public static void validateNative(Level level, int x, int y) {
        long width = (long) level.getMap().getWidth() * level.getBlockPixelSize();
        long height = (long) level.getMap().getHeight() * level.getBlockPixelSize();
        if (x < 0 || x >= width || x > 0xFFFE || y < 0 || y >= height || y > 0x7FFF)
            throw new IllegalArgumentException("Playtest start must lie inside the native terrain bounds");
        validateBody(level, x, y);
    }

    private static void validateBody(Level level, int x, int y) {
        // A compact standing body catches an embedded spawn without treating contact beneath the feet as penetration.
        for (int dx : new int[]{-8, 0, 8}) for (int dy : new int[]{-16, 0, 16}) {
            if (insideTerrain(level, x + dx, y + dy))
                throw new IllegalArgumentException("Playtest start overlaps solid terrain. Move the start or cursor into clear space.");
        }
    }

    static boolean insideTerrain(Level level, int x, int y) {
        int size = level.getBlockPixelSize();
        var map = level.getMap();
        if (x < 0 || y < 0 || x / size >= map.getWidth() || y / size >= map.getHeight()) return false;
        int block = level.resolveCollisionBlockIndex(
                Byte.toUnsignedInt(map.getValue(0, x / size, y / size)), x / size, y / size);
        var descriptor = level.getBlock(block).getChunkDesc((x % size) / 16, (y % size) / 16);
        if (!descriptor.hasPrimarySolidity()) return false;
        var chunk = level.getChunk(descriptor.getChunkIndex());
        var solid = level.getSolidTile(chunk.getSolidTileIndex());
        int px = descriptor.getHFlip() ? 15 - x % 16 : x % 16;
        int py = descriptor.getVFlip() ? 15 - y % 16 : y % 16;
        int height = solid.heights[px];
        return height > 0 ? py >= 16 - height : height < 0 && py < -height;
    }
}
