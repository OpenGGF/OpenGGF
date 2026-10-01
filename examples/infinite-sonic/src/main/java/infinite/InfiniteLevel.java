package infinite;

import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.*;
import java.util.List;

/** Borrows all art and collision data from the ROM; owns only a small layout. */
public final class InfiniteLevel implements Level {
    private final Level source;
    private final TerrainLibrary library;
    private final com.openggf.level.Map map;
    public InfiniteLevel(Level source, TerrainLibrary library) {
        this.source = source;
        this.library = library;
        if (library.blockCount() + source.getBlockCount() > 256) {
            throw new IllegalArgumentException("Combined foreground/background block budget exceeded");
        }
        map = new com.openggf.level.Map(2, TerrainLibrary.WIDTH, library.height());
        for (int x = 0; x < TerrainLibrary.WIDTH; x++) {
            for (int y = 0; y < library.height(); y++) {
                map.setValue(0, x, y, (byte) library.cell(x, y));
                map.setValue(1, x, y, (byte) (library.blockCount() + Byte.toUnsignedInt(source.getMap().getValue(1,
                        x % source.getLayerWidthBlocks(1), y % source.getLayerHeightBlocks(1)))));
            }
        }
    }
    @Override public com.openggf.level.Map getMap() { return map; }
    @Override public int getMinX() { return 0; }
    @Override public int getMaxX() { return 0x3800; }
    @Override public List<RingSpawn> getRings() { return List.of(); }
    @Override public List<ObjectSpawn> getObjects() {
        return List.of(new ObjectSpawn(128, 944, 0, 0, 0, false, 944, 0,
                "infinite-sonic", "infinite-sonic:controller"));
    }
    @Override public int getPaletteCount() { return source.getPaletteCount(); }
    @Override public Palette getPalette(int index) { return source.getPalette(index); }
    @Override public void setPalette(int index, Palette palette) { source.setPalette(index, palette); }
    @Override public int getPatternCount() { return source.getPatternCount(); }
    @Override public Pattern getPattern(int index) { return source.getPattern(index); }
    @Override public void ensurePatternCapacity(int minCount) { source.ensurePatternCapacity(minCount); }
    @Override public int getChunkCount() { return source.getChunkCount(); }
    @Override public Chunk getChunk(int index) { return source.getChunk(index); }
    @Override public int getBlockCount() { return library.blockCount() + source.getBlockCount(); }
    @Override public Block getBlock(int index) { return index < library.blockCount() ? library.block(index) : source.getBlock(index - library.blockCount()); }
    @Override public int getSolidTileCount() { return source.getSolidTileCount(); }
    @Override public SolidTile getSolidTile(int index) { return source.getSolidTile(index); }
    @Override public RingSpriteSheet getRingSpriteSheet() { return source.getRingSpriteSheet(); }
    @Override public int getMinY() { return source.getMinY(); }
    @Override public int getMaxY() { return 1536; }
    @Override public int getZoneIndex() { return source.getZoneIndex(); }
    @Override public int getBlockPixelSize() { return source.getBlockPixelSize(); }
    @Override public int getChunksPerBlockSide() { return source.getChunksPerBlockSide(); }
}
