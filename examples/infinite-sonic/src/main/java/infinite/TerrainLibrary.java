package infinite;

import com.openggf.level.*;
import java.util.ArrayList;
import java.util.List;

/** Selects ROM columns with continuous walkable floors and matching seam heights. */
public final class TerrainLibrary {
    public static final int WIDTH = 64;
    public static final long SEED = 0x534F4E4943L;
    private final List<int[][]> sections;
    private final List<int[]> floorProfiles;
    private final List<Block> blocks = new ArrayList<>();
    private final int height;
    private final int[][][] gapSections = new int[3][][];
    private final int seamHeight;

    public TerrainLibrary(Level source) {
        height = source.getLayerHeightBlocks(0);
        int seam = floor(source, 0, 128);
        seamHeight = seam;
        var candidates = new ArrayList<int[][]>();
        var profiles = new ArrayList<int[]>();
        for (int col = 0; col < source.getLayerWidthBlocks(0); col++) {
            int entry = floor(source, col, 0);
            boolean safe = entry >= 64 && (seam - entry) % 16 == 0;
            for (int row = 0; row < height; row++) {
                if ((source.getMap().getValue(0, col, row) & 0x80) != 0) safe = false;
            }
            int previous = entry;
            for (int x = 1; x < 256 && safe; x++) {
                int next = floor(source, col, x);
                if (next < 64 || Math.abs(next - previous) > 4
                        || next + seam - entry < 256 || next + seam - entry > 1408) safe = false;
                previous = next;
            }
            if (!safe) continue;
            // Each section is a ROM column followed by its horizontal reflection.
            // Both outside edges meet the same floor, while slopes form hills/dips.
            int[][] section = new int[2][height];
            for (int side = 0; side < 2; side++) {
                for (int row = 0; row < height; row++) {
                    Block block = new Block(16);
                    for (int cy = 0; cy < 16; cy++) {
                        int sourceY = row * 16 + cy - (seam - entry) / 16;
                        for (int cx = 0; cx < 16; cx++) {
                            int word = 0;
                            if (sourceY >= 0 && sourceY < height * 16) {
                                int id = Byte.toUnsignedInt(source.getMap().getValue(0, col, sourceY / 16));
                                word = source.getBlock(id).getChunkDesc(side == 0 ? cx : 15 - cx,
                                        sourceY % 16).get();
                                if (side == 1) word ^= 0x400;
                            }
                            block.setChunkDesc(cx, cy, new ChunkDesc(word));
                        }
                    }
                    section[side][row] = intern(block);
                }
            }
            if (candidates.stream().noneMatch(c -> java.util.Arrays.deepEquals(c, section))) {
                candidates.add(section);
                int[] profile = new int[512];
                for (int x = 0; x < 256; x++) {
                    profile[x] = floor(source, col, x) + seam - entry;
                    profile[511 - x] = profile[x];
                }
                profiles.add(profile);
            }
        }
        if (candidates.size() < 2) throw new IllegalArgumentException(
                "Green Hill requires at least two compatible terrain sections; found " + candidates.size());
        // Use a genuinely flat ROM section for both banks. Cutting arbitrary hills
        // can leave an uphill landing wall or a downhill launch that defeats a jump.
        int flat = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (java.util.Arrays.stream(profiles.get(i)).allMatch(y -> y == seam)) {
                flat = i;
                break;
            }
        }
        if (flat < 0) throw new IllegalArgumentException("No flat ROM section for jump banks");
        for (int variant = 0; variant < gapSections.length; variant++) {
            int halfGap = 32 + variant * 16;
            int[][] section = new int[2][height];
            for (int side = 0; side < 2; side++) {
                for (int row = 0; row < height; row++) {
                    Block original = blocks.get(candidates.get(flat)[side][row]);
                    Block cut = new Block(16);
                    for (int cy = 0; cy < 16; cy++) {
                        for (int cx = 0; cx < 16; cx++) {
                            int x = side * 256 + cx * 16;
                            // Clear the whole column: no invisible floor beneath the pit.
                            int word = x >= 256 - halfGap && x < 256 + halfGap
                                    ? 0 : original.getChunkDesc(cx, cy).get();
                            cut.setChunkDesc(cx, cy, new ChunkDesc(word));
                        }
                    }
                    section[side][row] = intern(cut);
                }
            }
            gapSections[variant] = section;
        }
        if (blocks.size() > 256) throw new IllegalArgumentException("Terrain block budget exceeded");
        sections = List.copyOf(candidates);
        floorProfiles = List.copyOf(profiles);
    }

    private int intern(Block block) {
        int[] state = block.saveState();
        for (int i = 0; i < blocks.size(); i++) {
            if (java.util.Arrays.equals(state, blocks.get(i).saveState())) return i;
        }
        blocks.add(block);
        return blocks.size() - 1;
    }
    public int candidateCount() { return sections.size(); }
    public int height() { return height; }
    public int blockCount() { return blocks.size(); }
    public Block block(int index) { return blocks.get(index); }
    private int sectionIndex(long section) {
        return section == 0 ? 0 : (int) Long.remainderUnsigned(random(section + SEED), sections.size());
    }

    static long random(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public int cell(long column, int row) {
        long section = Math.floorDiv(column, 2);
        int width = gapWidth(section);
        return (width == 0 ? sections.get(sectionIndex(section))
                : gapSections[(width - 64) / 32])[Math.floorMod(column, 2)][row];
    }

    /** Mod-designed jump corridors: one per four sections, separated by >= 1536px.
     * Flat banks provide 192px minimum runway on either side of a 64/96/128px pit.
     * Stock held jump is about 96px high and lasts about 60 frames on level ground;
     * even a 3px/frame approach comfortably clears the largest pit. Actual physics
     * traversal and bidirectional jumps are covered by TestInfiniteSonic.
     */
    public int gapWidth(long section) {
        if (section < 3 || Math.floorMod(section, 4) != 3) return 0;
        // Teach the jump requirement before speed can carry Sonic over narrower pits.
        if (section == 3) return 128;
        return 64 + 32 * (int) Long.remainderUnsigned(random(section + SEED + 0x474150L), 3);
    }

    /** Surface of the generated terrain, including translated and mirrored columns. */
    public int floorAt(long worldX) {
        long section = Math.floorDiv(worldX, 512);
        int x = Math.floorMod(worldX, 512);
        int width = gapWidth(section);
        if (width != 0) return x >= 256 - width / 2 && x < 256 + width / 2 ? -1 : seamHeight;
        return floorProfiles.get(sectionIndex(section))[x];
    }

    /** Top floor in a decoded S1 column; rejects ceilings, walls and missing floor. */
    public static int floor(Level level, int column, int x) {
        for (int y = 0; y < level.getLayerHeightBlocks(0) * 256; y += 16) {
            int id = Byte.toUnsignedInt(level.getMap().getValue(0, column, y / 256));
            Block block = level.getBlock(id & 0x7f);
            ChunkDesc desc = block.getChunkDesc(x / 16, (y % 256) / 16);
            if (desc.getPrimaryCollisionMode() != CollisionMode.ALL_SOLID
                    && desc.getPrimaryCollisionMode() != CollisionMode.TOP_SOLID) continue;
            Chunk chunk = level.getChunk(desc.getChunkIndex());
            int solid = chunk.getSolidTileIndex();
            if (solid == 0) continue;
            SolidTile tile = level.getSolidTile(solid);
            int h = tile.getHeightAt((byte) (desc.getHFlip() ? 15 - x % 16 : x % 16));
            if (h == 0) continue;
            if (desc.getVFlip() || h < 0 || h > 16) return -1;
            return y + 16 - h;
        }
        return -1;
    }
}
