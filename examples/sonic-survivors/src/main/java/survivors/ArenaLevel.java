package survivors;

import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.*;
import java.util.List;

/**
 * The stock act with its terrain, art and collision untouched, but no stock objects or rings:
 * its only object is the arena's {@link Stage} controller. Without the signpost, capsule,
 * boss triggers and level events the act cannot be finished in the usual way.
 */
final class ArenaLevel implements Level {
    private final Level source;
    private final Arena arena;

    ArenaLevel(Level source, Arena arena) {
        this.source = source;
        this.arena = arena;
    }

    @Override public List<ObjectSpawn> getObjects() {
        int y = arena.floorTop() - 64;
        return List.of(Stage.spawnAt(arena.centreX(), y));
    }
    @Override public List<RingSpawn> getRings() { return List.of(); }

    @Override public int getPaletteCount() { return source.getPaletteCount(); }
    @Override public Palette getPalette(int index) { return source.getPalette(index); }
    @Override public void setPalette(int index, Palette palette) { source.setPalette(index, palette); }
    @Override public int getPatternCount() { return source.getPatternCount(); }
    @Override public Pattern getPattern(int index) { return source.getPattern(index); }
    @Override public void ensurePatternCapacity(int minCount) { source.ensurePatternCapacity(minCount); }
    @Override public int getChunkCount() { return source.getChunkCount(); }
    @Override public Chunk getChunk(int index) { return source.getChunk(index); }
    @Override public int getBlockCount() { return source.getBlockCount(); }
    @Override public Block getBlock(int index) { return source.getBlock(index); }
    @Override public int getSolidTileCount() { return source.getSolidTileCount(); }
    @Override public SolidTile getSolidTile(int index) { return source.getSolidTile(index); }
    @Override public com.openggf.level.Map getMap() { return source.getMap(); }
    @Override public RingSpriteSheet getRingSpriteSheet() { return source.getRingSpriteSheet(); }
    @Override public int getMinX() { return source.getMinX(); }
    @Override public int getMaxX() { return source.getMaxX(); }
    @Override public int getMinY() { return source.getMinY(); }
    @Override public int getMaxY() { return source.getMaxY(); }
    @Override public int getZoneIndex() { return source.getZoneIndex(); }
    @Override public int getBlockPixelSize() { return source.getBlockPixelSize(); }
    @Override public int getChunksPerBlockSide() { return source.getChunksPerBlockSide(); }
    @Override public int getLayerWidthBlocks(int layer) { return source.getLayerWidthBlocks(layer); }
    @Override public int getLayerHeightBlocks(int layer) { return source.getLayerHeightBlocks(layer); }
    @Override public boolean hasBackgroundCollisionRowAt(int y) { return source.hasBackgroundCollisionRowAt(y); }
    @Override public Palette.Color getBackdropColor() { return source.getBackdropColor(); }
    @Override public int getBackdropPaletteLine() { return source.getBackdropPaletteLine(); }
    @Override public int resolveCollisionBlockIndex(int blockIndex, int mapX, int mapY) {
        return source.resolveCollisionBlockIndex(blockIndex, mapX, mapY);
    }
}
