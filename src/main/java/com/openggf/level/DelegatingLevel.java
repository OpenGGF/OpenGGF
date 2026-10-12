package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import java.util.List;
import java.util.Objects;

/** Complete forwarding base, including optional collision, layout and palette semantics. */
@com.openggf.game.ModApi
public class DelegatingLevel implements Level {
    protected final Level source;
    public DelegatingLevel(Level source) { this.source = Objects.requireNonNull(source, "source"); }
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
    @Override public Map getMap() { return source.getMap(); }
    @Override public List<ObjectSpawn> getObjects() { return source.getObjects(); }
    @Override public List<RingSpawn> getRings() { return source.getRings(); }
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
    @Override public int resolveCollisionBlockIndex(int blockIndex, int mapX, int mapY) { return source.resolveCollisionBlockIndex(blockIndex, mapX, mapY); }
}
