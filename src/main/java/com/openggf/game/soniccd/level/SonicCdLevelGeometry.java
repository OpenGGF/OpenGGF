package com.openggf.game.soniccd.level;

import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;

import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR11aTerrain.Plane.*;

/** Pure native-to-engine component conversion; authentication belongs to CdR11aAssetLoader. */
class SonicCdLevelGeometry extends AbstractLevel {
    private static final int GRID_SIDE = 16;
    private static final int NATIVE_COLUMNS = 64;
    private static final int NATIVE_ROWS = 16;
    private final int[] layerWidths;
    private final int[] layerHeights;

    SonicCdLevelGeometry(CdR11aTerrain terrain, CdAssetBytes initialPalette, CdR11aLoadPlan.Bounds bounds) {
        // Palmtree Panic is native zone 0: CD Initial Program/IPX.asm:143-145.
        super(0);
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(bounds, "bounds");
        loadPalettes(Objects.requireNonNull(initialPalette, "initialPalette"));
        loadPatterns(terrain);
        loadCollisionShapes(terrain.collision());
        loadChunks(terrain);
        loadBlocks(terrain);

        var layout = terrain.layout();
        layerWidths = new int[]{layout.columns(FOREGROUND), layout.columns(BACKGROUND)};
        layerHeights = new int[]{layout.rows(FOREGROUND), layout.rows(BACKGROUND)};
        // LoadLevelLayout clears $800 bytes, with FG/BG at +0/+$40 and stride $80.
        // Load Level Data.asm:57-96. Preserve cleared cells; do not repeat BG data.
        byte[] cells = new byte[NATIVE_COLUMNS * NATIVE_ROWS * 2];
        for (var plane : CdR11aTerrain.Plane.values()) {
            int layer = plane == FOREGROUND ? 0 : 1;
            for (int y = 0; y < NATIVE_ROWS; y++) {
                for (int x = 0; x < NATIVE_COLUMNS; x++) {
                    cells[y * NATIVE_COLUMNS * 2 + layer * NATIVE_COLUMNS + x] =
                            (byte) layout.nativeCell(plane, x, y).chunkId();
                }
            }
        }
        // The mutable engine map contains drawing IDs. Raw loop flags stay in native provenance;
        // there is no native collision/loop consumer in this static adapter.
        map = new Map(2, NATIVE_COLUMNS, NATIVE_ROWS, cells);
        // These are installed executable registrations, not a lossy native-placement projection.
        objects = List.of();
        rings = List.of();
        ringSpriteSheet = null;
        // LevelSizeLoad, Scroll (Act 1 Present).asm:19-33: camera bounds, not layout extents.
        minX = bounds.left();
        maxX = bounds.right();
        minY = bounds.top();
        maxY = bounds.bottom();
    }

    private void loadPalettes(CdAssetBytes initial) {
        if (initial.length() != PALETTE_COUNT * Palette.PALETTE_SIZE_IN_ROM) {
            throw new IllegalArgumentException("R11A initial palette must supply all 64 CRAM words");
        }
        // PaletteTable: IDs 3/5 load 16 Sonic + 48 level colours at palette +0/+$20.
        // Palette Data (Act 1 Present).asm:17-25; LoadPalette, Palette Load.asm:14-25.
        palettes = new Palette[PALETTE_COUNT];
        for (int line = 0; line < PALETTE_COUNT; line++) {
            palettes[line] = new Palette();
            palettes[line].fromSegaFormat(initial.slice(line * 32, 32).copy());
        }
    }

    private void loadPatterns(CdR11aTerrain terrain) {
        patternCount = terrain.patternCount();
        patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(terrain.patterns().slice(i * Pattern.PATTERN_SIZE_IN_ROM,
                    Pattern.PATTERN_SIZE_IN_ROM).copy());
        }
    }

    private void loadCollisionShapes(CdR11aTerrain.Collision nativeCollision) {
        solidTileCount = nativeCollision.angles().length();
        solidTiles = new SolidTile[solidTileCount];
        for (int id = 0; id < solidTileCount; id++) {
            byte[] heights = nativeCollision.heights().slice(id * 16, 16).copy();
            byte[] widths = nativeCollision.widths().slice(id * 16, 16).copy();
            // FindLevelFloor/FindLevelWall sign-extend samples, preserve unsigned angle bits.
            // Collision Floor.asm:704-737,893-926. This copies data, not the native solver.
            solidTiles[id] = new SolidTile(id, heights, widths, (byte) nativeCollision.angle(id));
        }
    }

    private void loadChunks(CdR11aTerrain terrain) {
        chunkCount = terrain.blockCount();
        chunks = new Chunk[chunkCount];
        for (int id = 0; id < chunkCount; id++) {
            Chunk chunk = new Chunk();
            byte[] encoded = new byte[Chunk.CHUNK_SIZE_IN_ROM];
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    int word = terrain.blockWord(id, x, y).rawWord();
                    int offset = (y * 2 + x) * 2;
                    encoded[offset] = (byte) (word >>> 8);
                    encoded[offset + 1] = (byte) word;
                }
            }
            // FindLevelFloor rejects block ID 0 before indexing collisionPtr (Collision Floor.asm:690-708).
            int collisionId = id == 0 ? 0 : terrain.collision().idForBlock(id);
            chunk.fromSegaFormat(encoded, collisionId, collisionId);
            chunks[id] = chunk;
        }
    }

    private void loadBlocks(CdR11aTerrain terrain) {
        blockCount = terrain.chunkCount() + 1;
        blocks = new Block[blockCount];
        // Blank layout 0: drawing returns blockBuffer's first 16px block, collision is blank.
        // GetBlockDataAbsXY (Block Draw.asm:202-235); GetLevelBlock (Collision Floor.asm:481-515).
        // R11A's actual block 0 contains pattern 0; the supplied pattern is transparent.
        blocks[0] = new Block(GRID_SIDE);
        // Decode independent zero descriptors rather than sharing mutable ChunkDesc.EMPTY.
        blocks[0].fromSegaFormat(new byte[GRID_SIDE * GRID_SIDE * 2], GRID_SIDE * GRID_SIDE);
        for (int id = 1; id < blockCount; id++) {
            blocks[id] = new ResidentBoundedBlock(terrain, id);
        }
    }

    @Override
    public int getBlockPixelSize() { return 256; }

    @Override
    public int getChunksPerBlockSide() { return GRID_SIDE; }

    /** Declared file dimensions; getMap() also retains the native cleared RAM cells beyond them. */
    @Override
    public int getLayerWidthBlocks(int layer) { return layerWidths[Objects.checkIndex(layer, 2)]; }

    @Override
    public int getLayerHeightBlocks(int layer) { return layerHeights[Objects.checkIndex(layer, 2)]; }

    /** Main.asm:233 writes VDP register $8720. */
    @Override
    public int getBackdropPaletteLine() { return 2; }

    /** Native GetLevelBlock's object-dependent loop remapping is not a static drawing operation. */
    @Override
    public int resolveCollisionBlockIndex(int blockIndex, int mapX, int mapY) {
        throw new UnsupportedOperationException("R11A static view does not implement native collision/loop lookup");
    }

    /** There is no reviewed PLC/resident-art owner for tiles beyond this decoded static extent. */
    @Override
    public void ensurePatternCapacity(int minCount) {
        if (minCount > patternCount) {
            throw new UnsupportedOperationException("R11A static view cannot invent unloaded pattern/resident data");
        }
    }

    /**
     * Keep all 121 native chunks addressable, including known cells in unused chunks.
     * Null internal cells explicitly mean unavailable drawing data, never blank geometry.
     * No subclass state participates in mutation/rewind: actual descriptors live in Block.
     */
    private static final class ResidentBoundedBlock extends Block {
        private final CdR11aTerrain terrain;
        private final int nativeChunkId;

        ResidentBoundedBlock(CdR11aTerrain terrain, int nativeChunkId) {
            super(GRID_SIDE);
            this.terrain = terrain;
            this.nativeChunkId = nativeChunkId;
            for (int y = 0; y < GRID_SIDE; y++) {
                for (int x = 0; x < GRID_SIDE; x++) {
                    var word = terrain.chunkWord(nativeChunkId, x, y);
                    int drawIndex = word.renderBlockIndex();
                    if (drawIndex >= terrain.blockCount()) {
                        setChunkDesc(x, y, null);
                        continue;
                    }
                    // GetBlockDataAbsXY masks $3FF (Block Draw.asm:227-230); DrawBlock tests
                    // native bits 11/12 (117-179). The engine moves flips to bits 10/11.
                    // Solidity 13/14 is represented on both engine paths; this does NOT widen
                    // ChunkDesc's index to the separate $7FF native collision index.
                    int engineWord = drawIndex | (word.flipX() ? 0x400 : 0) | (word.flipY() ? 0x800 : 0)
                            | (word.solidity() << 12) | (word.solidity() << 14);
                    setChunkDesc(x, y, new ChunkDesc(engineWord));
                }
            }
        }

        @Override
        public ChunkDesc getChunkDesc(int x, int y) {
            ChunkDesc desc = super.getChunkDesc(x, y);
            if (desc == null) {
                var word = terrain.chunkWord(nativeChunkId, x, y);
                throw new UnsupportedOperationException("R11A native chunk " + nativeChunkId + " cell " + x + "," + y
                        + " requires unloaded drawing block " + word.renderBlockIndex()
                        + "; raw word=0x" + Integer.toHexString(word.rawWord())
                        + ", native collision index=" + word.collisionBlockIndex());
            }
            return desc;
        }

        /** Block's int[] state cannot encode unavailable cells; reference snapshots remain supported. */
        @Override
        public int[] saveState() {
            for (int y = 0; y < GRID_SIDE; y++) {
                for (int x = 0; x < GRID_SIDE; x++) {
                    getChunkDesc(x, y);
                }
            }
            return super.saveState();
        }
    }
}
