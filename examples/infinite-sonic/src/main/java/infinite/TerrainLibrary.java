package infinite;

import com.openggf.level.*;
import java.util.ArrayList;
import java.util.List;

/** Selects ROM columns with continuous walkable floors and matching seam heights. */
public final class TerrainLibrary {
    public static final int WIDTH = 64;
    public static final long SEED = 0x534F4E4943L;
    /** Elevation tiers -64, -32, 0 and +32px from the seam floor (negative is higher). Mod design. */
    static final int TIER_COUNT = 4;
    static final int TIER_STEP = 32;
    private static final int GROUND_TIER = 2;
    /** Largest climb or drop at one corridor; a held jump peaks near 96px. */
    static final int MAX_STEP = 64;
    /** Pit widths 64, 96, 128, 160 and 192px. */
    static final int GAP_COUNT = 5;
    private final List<List<int[][]>> sections = new ArrayList<>();
    private final List<List<int[]>> floorProfiles = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private final int height;
    private final int[][][] flatHalves = new int[TIER_COUNT][2][];
    private final int[][][][] gapHalves = new int[GAP_COUNT][TIER_COUNT][2][];
    private final int seamHeight;

    public TerrainLibrary(Level source) {
        height = source.getLayerHeightBlocks(0);
        int budget = 256 - source.getBlockCount();
        int seam = floor(source, 0, 128);
        seamHeight = seam;
        var columns = new ArrayList<Integer>();
        var baseProfiles = new ArrayList<int[]>();
        var groundSections = new ArrayList<int[][]>();
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
            int[][] section = section(source, col, seam - entry);
            if (groundSections.stream().noneMatch(c -> java.util.Arrays.deepEquals(c, section))) {
                groundSections.add(section);
                columns.add(col);
                int[] profile = new int[512];
                for (int x = 0; x < 256; x++) {
                    profile[x] = floor(source, col, x) + seam - entry;
                    profile[511 - x] = profile[x];
                }
                baseProfiles.add(profile);
            }
        }
        if (groundSections.size() < 2) throw new IllegalArgumentException(
                "Green Hill requires at least two compatible terrain sections; found " + groundSections.size());
        // Use a genuinely flat ROM section for both banks. Cutting arbitrary hills
        // can leave an uphill landing wall or a downhill launch that defeats a jump.
        int flat = -1;
        for (int i = 0; i < baseProfiles.size(); i++) {
            if (java.util.Arrays.stream(baseProfiles.get(i)).allMatch(y -> y == seam)) {
                flat = i;
                break;
            }
        }
        if (flat < 0) throw new IllegalArgumentException("No flat ROM section for jump banks");
        int flatColumn = columns.get(flat);
        int flatShift = seam - floor(source, flatColumn, 0);
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            sections.add(new ArrayList<>());
            floorProfiles.add(new ArrayList<>());
            int[][] banks = section(source, flatColumn, flatShift + tierOffset(tier));
            flatHalves[tier] = banks;
            for (int w = 0; w < GAP_COUNT; w++) {
                int halfGap = gapWidthAt(w) / 2;
                for (int side = 0; side < 2; side++) {
                    int[] half = new int[height];
                    for (int row = 0; row < height; row++) {
                        Block original = blocks.get(banks[side][row]);
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
                        half[row] = intern(cut);
                    }
                    gapHalves[w][tier][side] = half;
                }
            }
        }
        if (blocks.size() > budget) throw new IllegalArgumentException("Terrain block budget exceeded");
        // Ground level keeps every ROM section. Raised/lowered tiers take whole sections,
        // in ROM order, while the shared 256-entry block index still has room.
        for (int i = 0; i < columns.size(); i++) {
            int col = columns.get(i);
            int shift = seam - floor(source, col, 0);
            for (int tier = 0; tier < TIER_COUNT; tier++) {
                int mark = blocks.size();
                int[][] section = tier == GROUND_TIER ? groundSections.get(i) : section(source, col, shift + tierOffset(tier));
                if (blocks.size() > budget) {
                    blocks.subList(mark, blocks.size()).clear();
                    continue;
                }
                sections.get(tier).add(section);
                int offset = tierOffset(tier);
                floorProfiles.get(tier).add(java.util.Arrays.stream(baseProfiles.get(i)).map(y -> y + offset).toArray());
            }
        }
    }

    /** A ROM column followed by its horizontal reflection, shifted down by {@code shift} pixels. */
    private int[][] section(Level source, int col, int shift) {
        // Both outside edges meet the same floor, while slopes form hills/dips.
        int[][] section = new int[2][height];
        for (int side = 0; side < 2; side++) {
            for (int row = 0; row < height; row++) {
                Block block = new Block(16);
                for (int cy = 0; cy < 16; cy++) {
                    // Raised terrain repeats the bottom chunk row rather than leaving a void below.
                    int sourceY = Math.min(row * 16 + cy - shift / 16, height * 16 - 1);
                    for (int cx = 0; cx < 16; cx++) {
                        int word = 0;
                        if (sourceY >= 0) {
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
        return section;
    }

    private int intern(Block block) {
        int[] state = block.saveState();
        for (int i = 0; i < blocks.size(); i++) {
            if (java.util.Arrays.equals(state, blocks.get(i).saveState())) return i;
        }
        blocks.add(block);
        return blocks.size() - 1;
    }
    public int candidateCount() { return sections.get(GROUND_TIER).size(); }
    public int candidateCount(int tier) { return sections.get(tier).size(); }
    public int height() { return height; }
    public int blockCount() { return blocks.size(); }
    public Block block(int index) { return blocks.get(index); }
    private int sectionIndex(long section, int tier) {
        return section == 0 ? 0
                : (int) Long.remainderUnsigned(random(section + SEED), sections.get(tier).size());
    }

    static long random(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /** Every fourth section after the opening joins two elevation stretches. */
    public boolean isCorridor(long section) {
        return section >= 3 && Math.floorMod(section, 4) == 3;
    }

    /** Tier of stretch k (sections 4k..4k+2 plus the corridor banks either side). Stateless:
     * even stretches pick freely; odd stretches pick a tier within MAX_STEP of both neighbours. */
    int stretchTier(long stretch) {
        if (stretch <= 1) return GROUND_TIER; // Opening and first taught jump stay on the ground.
        long random = random(stretch + SEED + 0x5449455253L);
        if (Math.floorMod(stretch, 2) == 0) return (int) Long.remainderUnsigned(random, TIER_COUNT);
        int before = tierOffset(stretchTier(stretch - 1));
        int after = tierOffset(stretchTier(stretch + 1));
        int reachable = 0;
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            if (reachable(tier, before, after)) reachable++;
        }
        int pick = (int) Long.remainderUnsigned(random, reachable);
        for (int tier = 0; ; tier++) {
            if (reachable(tier, before, after) && pick-- == 0) return tier;
        }
    }

    static int tierOffset(int tier) { return (tier - GROUND_TIER) * TIER_STEP; }
    static int gapWidthAt(int index) { return 64 + 32 * index; }

    private static boolean reachable(int tier, int before, int after) {
        return Math.abs(tierOffset(tier) - before) <= MAX_STEP && Math.abs(tierOffset(tier) - after) <= MAX_STEP;
    }

    /** Vertical change across a corridor; positive drops, negative climbs. */
    public int stepHeight(long section) {
        if (!isCorridor(section)) return 0;
        long stretch = Math.floorDiv(section, 4);
        return tierOffset(stretchTier(stretch + 1)) - tierOffset(stretchTier(stretch));
    }

    private int tierAt(long section, int side) {
        long stretch = Math.floorDiv(section, 4);
        return stretchTier(isCorridor(section) && side == 1 ? stretch + 1 : stretch);
    }

    public int cell(long column, int row) {
        long section = Math.floorDiv(column, 2);
        int side = Math.floorMod(column, 2);
        int tier = tierAt(section, side);
        if (!isCorridor(section)) {
            return sections.get(tier).get(sectionIndex(section, tier))[side][row];
        }
        int width = gapWidth(section);
        return width == 0 ? flatHalves[tier][side][row]
                : gapHalves[(width - 64) / 32][tier][side][row];
    }

    /** Mod-designed corridors: one per four sections, separated by >= 1536px.
     * Flat banks provide at least 160px of runway on either side of a 64-192px pit and
     * may sit at different elevations. Stock held jump is about 96px high and lasts about
     * 60 frames on level ground; climbs up to 64px are paired with pits up to 128px, and the
     * widest pits stay within one 32px tier. Drops may be a plain ledge with no pit (width 0).
     * Actual physics traversal and bidirectional jumps are covered by TestInfiniteSonic.
     */
    public int gapWidth(long section) {
        if (!isCorridor(section)) return 0;
        // Teach the jump requirement before speed can carry Sonic over narrower pits.
        if (section == 3) return 128;
        int step = stepHeight(section);
        long random = random(section + SEED + 0x474150L);
        if (step > 0 && Long.remainderUnsigned(random >>> 16, 3) == 0) return 0;
        int choices = Math.abs(step) > 32 ? 3 : Math.abs(step) > 0 ? 4 : 5;
        return gapWidthAt((int) Long.remainderUnsigned(random, choices));
    }

    /** Surface of the generated terrain, including translated and mirrored columns. */
    public int floorAt(long worldX) {
        long section = Math.floorDiv(worldX, 512);
        int x = Math.floorMod(worldX, 512);
        int side = x < 256 ? 0 : 1;
        int tier = tierAt(section, side);
        if (isCorridor(section)) {
            int width = gapWidth(section);
            return x >= 256 - width / 2 && x < 256 + width / 2 ? -1 : seamHeight + tierOffset(tier);
        }
        return floorProfiles.get(tier).get(sectionIndex(section, tier))[x];
    }

    /** Top floor in a decoded S1 column; rejects ceilings, walls and missing floor. */
    public static int floor(Level level, int column, int x) {
        for (int y = 0; y < level.getLayerHeightBlocks(0) * 256; y += 16) {
            // Layout values are full block indices here: the ROM loader already strips
            // S1's loop flag, and generated layouts may use indices above 0x7F.
            int id = Byte.toUnsignedInt(level.getMap().getValue(0, column, y / 256));
            Block block = level.getBlock(id);
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
