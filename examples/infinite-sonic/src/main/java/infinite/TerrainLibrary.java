package infinite;

import com.openggf.level.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

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
    /** Open space every walkable section keeps above its floor: Sonic is 39px tall and a
     * held jump rises about 96px. Mod design; it rejects tunnels, mazes and overhangs. */
    public static final int CLEARANCE = 112;
    /** Jump banks also keep headroom for a held jump launched from the lower side of a climb. */
    static final int BANK_CLEARANCE = 176;
    /** S1 object $54 (LTag_Main): invisible hurt zones laid over MZ lava, which is otherwise
     * ordinary solid collision. Its touch sizes $14-$16 reach at most $80px horizontally and
     * $20px vertically from the tag; the margins below cover the largest. */
    private static final int LAVA_TAG = 0x54;
    private static final int LAVA_REACH_X = 0x80;
    private static final int LAVA_REACH_Y = 0x20 + 16;
    /** The seam floor sits near GHZ1's stock start floor (Y=944 centre + 19px radius). */
    private static final int SEAM_BASE = 960;
    private final List<List<int[][]>> sections = new ArrayList<>();
    private final List<List<int[]>> floorProfiles = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    /** Course rows: enough for the 1536px camera limit; deeper source rows are not copied. */
    static final int MAX_HEIGHT = 6;
    private final int height;
    private final int sourceHeight;
    private final int[][][] flatHalves = new int[TIER_COUNT][2][];
    private final int[][][][] gapHalves = new int[GAP_COUNT][TIER_COUNT][2][];
    private final int seamHeight;
    private final int romZone;
    private final int[] groundSpecies;
    private final int[] airSpecies;
    private final List<CoursePlatforms.Kind> platformKinds;
    /** An empty column: the open middle of a platform stretch's pit. */
    private final int[] emptyHalf;

    /** One walkable 256px floor in a ROM foreground column. {@code profile} holds source Y. */
    private record Candidate(int column, int[] profile, int clearance) {
        int entry() { return profile[0]; }
        boolean flat() { return Arrays.stream(profile).allMatch(y -> y == profile[0]); }
    }

    public TerrainLibrary(Level source) {
        romZone = source.getZoneIndex();
        // The act's own stock placement decides which badniks the course uses.
        var stockObjects = source.getObjects();
        groundSpecies = CourseSpecies.lineUp(stockObjects, false, romZone);
        airSpecies = CourseSpecies.lineUp(stockObjects, true, romZone);
        platformKinds = CoursePlatforms.lineUp(stockObjects, romZone);
        sourceHeight = source.getLayerHeightBlocks(0);
        height = Math.min(sourceHeight, MAX_HEIGHT);
        int budget = 256 - backgroundBlocks(source).length;
        var found = new ArrayList<Candidate>();
        for (int col = 0; col < source.getLayerWidthBlocks(0); col++) {
            for (int y = 0; y < sourceHeight * 256; y++) {
                if (!surface(source, col, 0, y)) continue;
                int[] profile = trace(source, col, y);
                if (profile == null) continue;
                int clearance = clearance(source, col, profile);
                if (clearance >= CLEARANCE && !lava(source, col, profile)) found.add(new Candidate(col, profile, clearance));
            }
        }
        // Sections shift by whole 16px chunk rows, so every seam floor shares one residue.
        // Prefer a residue that offers a flat jump bank, then the most candidates.
        int bestResidue = -1;
        int bestScore = -1;
        for (int residue = 0; residue < 16; residue++) {
            int seam = SEAM_BASE + residue;
            int count = 0;
            boolean flat = false;
            for (Candidate c : found) {
                if (Math.floorMod(c.entry(), 16) == residue && fits(c, seam)) {
                    count++;
                    flat |= c.flat();
                }
            }
            int score = (flat ? 1 << 20 : 0) + count;
            if (score > bestScore) {
                bestScore = score;
                bestResidue = residue;
            }
        }
        seamHeight = SEAM_BASE + bestResidue;
        int residue = bestResidue;
        var usable = found.stream()
                .filter(c -> Math.floorMod(c.entry(), 16) == residue && fits(c, seamHeight)).toList();
        // Use a genuinely flat ROM section for both banks. Cutting arbitrary hills
        // can leave an uphill landing wall or a downhill launch that defeats a jump.
        Candidate flat = usable.stream().filter(Candidate::flat)
                .max((a, b) -> Integer.compare(Math.min(a.clearance(), BANK_CLEARANCE),
                        Math.min(b.clearance(), BANK_CLEARANCE)))
                .orElse(null);
        if (flat == null) throw new IllegalArgumentException("No flat ROM section for jump banks");
        int flatShift = seamHeight - flat.entry();
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            sections.add(new ArrayList<>());
            floorProfiles.add(new ArrayList<>());
            // Banks always drop scenery above their jump headroom: overhangs above a pit
            // would also multiply blocks for every cut width.
            int[][] banks = section(source, flat.column(), flatShift + tierOffset(tier),
                    seamHeight + tierOffset(tier) - BANK_CLEARANCE);
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
        emptyHalf = new int[height];
        Arrays.fill(emptyHalf, intern(new Block(16)));
        if (blocks.size() > budget) throw new IllegalArgumentException("Terrain block budget exceeded");
        // Ground level takes distinct ROM sections first; raised/lowered tiers then take whole
        // sections, in ROM order, while the shared 256-entry block index still has room.
        var ground = new ArrayList<Candidate>();
        for (Candidate c : usable) {
            int mark = blocks.size();
            int[][] section = section(source, c.column(), seamHeight - c.entry(), -1);
            if (blocks.size() > budget || sections.get(GROUND_TIER).stream().anyMatch(s -> Arrays.deepEquals(s, section))) {
                blocks.subList(mark, blocks.size()).clear();
                continue;
            }
            ground.add(c);
            sections.get(GROUND_TIER).add(section);
            floorProfiles.get(GROUND_TIER).add(courseProfile(c, 0));
        }
        if (ground.size() < 2) throw new IllegalArgumentException(
                "Endless terrain requires at least two compatible ROM sections; found " + ground.size());
        for (Candidate c : ground) {
            for (int tier = 0; tier < TIER_COUNT; tier++) {
                if (tier == GROUND_TIER) continue;
                int mark = blocks.size();
                int[][] section = section(source, c.column(), seamHeight - c.entry() + tierOffset(tier), -1);
                if (blocks.size() > budget) {
                    blocks.subList(mark, blocks.size()).clear();
                    continue;
                }
                sections.get(tier).add(section);
                floorProfiles.get(tier).add(courseProfile(c, tierOffset(tier)));
            }
        }
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            // A tier with no hill shape of its own still has flat ground from the jump banks.
            if (sections.get(tier).isEmpty()) {
                sections.get(tier).add(flatHalves[tier]);
                int floor = seamHeight + tierOffset(tier);
                floorProfiles.get(tier).add(java.util.stream.IntStream.range(0, 512).map(x -> floor).toArray());
            }
        }
    }

    /** True when a stock Lava Tag covers this floor: the course spawns no tags, so lava would be safe ground. */
    private static boolean lava(Level level, int column, int[] profile) {
        int top = Arrays.stream(profile).min().orElse(0);
        int bottom = Arrays.stream(profile).max().orElse(0);
        for (var spawn : level.getObjects()) {
            if (spawn.objectId() != LAVA_TAG) continue;
            if (spawn.x() + LAVA_REACH_X >= column * 256 && spawn.x() - LAVA_REACH_X < column * 256 + 256
                    && spawn.y() + LAVA_REACH_Y >= top && spawn.y() - LAVA_REACH_Y <= bottom) return true;
        }
        return false;
    }

    /** Distinct background block indices; only these source blocks need layout slots. */
    public static int[] backgroundBlocks(Level source) {
        var used = new TreeSet<Integer>();
        for (int x = 0; x < source.getLayerWidthBlocks(1); x++) {
            for (int y = 0; y < source.getLayerHeightBlocks(1); y++) {
                used.add(Byte.toUnsignedInt(source.getMap().getValue(1, x, y)));
            }
        }
        return used.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean fits(Candidate c, int seam) {
        int shift = seam - c.entry();
        for (int y : c.profile()) {
            if (y + shift < 256 || y + shift > 1408) return false;
        }
        return true;
    }

    private int[] courseProfile(Candidate c, int offset) {
        int shift = seamHeight - c.entry() + offset;
        int[] profile = new int[512];
        for (int x = 0; x < 256; x++) {
            profile[x] = c.profile()[x] + shift;
            profile[511 - x] = profile[x];
        }
        return profile;
    }

    /** Follows one floor across a column, allowing at most 4px change per horizontal pixel. */
    private static int[] trace(Level level, int column, int entry) {
        int[] profile = new int[256];
        profile[0] = entry;
        for (int x = 1; x < 256; x++) {
            int previous = profile[x - 1];
            int next = -1;
            for (int d = 0; d <= 4 && next < 0; d++) {
                if (surface(level, column, x, previous - d)) next = previous - d;
                else if (d > 0 && surface(level, column, x, previous + d)) next = previous + d;
            }
            if (next < 0) return null;
            profile[x] = next;
        }
        return profile;
    }

    /** Smallest open height above the floor across the column, capped at 256px. */
    private static int clearance(Level level, int column, int[] profile) {
        int clearance = 256;
        for (int x = 0; x < 256; x++) {
            for (int dy = 1; dy < clearance; dy++) {
                if (solid(level, column, x, profile[x] - dy)) {
                    clearance = dy - 1;
                    break;
                }
            }
        }
        return clearance;
    }

    /** A ROM column followed by its horizontal reflection, shifted down by {@code shift} pixels.
     * Rows wholly above {@code clearAbove} (course Y, or -1) are emptied for jump headroom. */
    private int[][] section(Level source, int col, int shift, int clearAbove) {
        // Both outside edges meet the same floor, while slopes form hills/dips.
        int[][] section = new int[2][height];
        for (int side = 0; side < 2; side++) {
            for (int row = 0; row < height; row++) {
                Block block = new Block(16);
                for (int cy = 0; cy < 16; cy++) {
                    // Raised terrain repeats the bottom chunk row rather than leaving a void below.
                    int sourceY = Math.min(row * 16 + cy - shift / 16, sourceHeight * 16 - 1);
                    boolean cleared = (row * 16 + cy + 1) * 16 <= clearAbove;
                    for (int cx = 0; cx < 16; cx++) {
                        int word = 0;
                        if (sourceY >= 0 && !cleared) {
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
            if (Arrays.equals(state, blocks.get(i).saveState())) return i;
        }
        blocks.add(block);
        return blocks.size() - 1;
    }
    public int candidateCount() { return sections.get(GROUND_TIER).size(); }
    public int candidateCount(int tier) { return sections.get(tier).size(); }
    public int height() { return height; }
    /** Floor height where corridors and section edges meet at ground tier. */
    public int seam() { return seamHeight; }
    public int blockCount() { return blocks.size(); }
    /** Stock S1 zone id of the source level, which also selects the loaded object art. */
    public int romZone() { return romZone; }
    /** Weighted {@link CourseSpecies} ids for ground and air encounters in this act. */
    public int[] groundSpecies() { return groundSpecies.clone(); }
    public int[] airSpecies() { return airSpecies.clone(); }
    /** Stock platform kinds this act lends its platform stretches; empty when it places none. */
    public List<CoursePlatforms.Kind> platformKinds() { return platformKinds; }
    public Block block(int index) { return blocks.get(index); }
    private int sectionIndex(long section, int tier) {
        return (int) Long.remainderUnsigned(random(section + SEED), sections.get(tier).size());
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
        // The opening is a flat bank, so every zone starts Sonic on level seam floor.
        if (section == 0) return flatHalves[tier][side][row];
        if (platformRun(section)) {
            long stretch = Math.floorDiv(section, 4);
            // Section 4k+2 keeps a flat bank and opens at its right end; 4k+3 is open until
            // the far bank. Both cuts reuse the corridor halves.
            if (Math.floorMod(section, 4) == 2) {
                return side == 0 ? flatHalves[tier][0][row] : gapHalves[platformCut(stretch, 0)][tier][0][row];
            }
            return side == 0 ? emptyHalf[row] : gapHalves[platformCut(stretch, 1)][tier][1][row];
        }
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
        if (!isCorridor(section) || platformRun(section)) return 0;
        // Teach the jump requirement before speed can carry Sonic over narrower pits.
        if (section == 3) return 128;
        int step = stepHeight(section);
        long random = random(section + SEED + 0x474150L);
        if (step > 0 && Long.remainderUnsigned(random >>> 16, 3) == 0) return 0;
        int choices = Math.abs(step) > 32 ? 3 : Math.abs(step) > 0 ? 4 : 5;
        return gapWidthAt((int) Long.remainderUnsigned(random, choices));
    }

    /**
     * Platform stretches: sections 4k+2 and 4k+3 become one 320-448px pit, bridged by stock
     * platforms ({@link PlatformPlan}). Mod design: a third of eligible stretches, from the
     * third corridor on, where the banks differ by at most one tier. The approach keeps a
     * flat bank of at least 352px and the far bank at least 160px before the next section.
     */
    public boolean platformRun(long section) {
        long part = Math.floorMod(section, 4);
        return (part == 2 || part == 3) && platformStretch(Math.floorDiv(section, 4));
    }

    public boolean platformStretch(long stretch) {
        if (platformKinds.isEmpty() || stretch < 2) return false;
        if (Math.abs(stepHeight(stretch * 4 + 3)) > TIER_STEP) return false;
        return Long.remainderUnsigned(random(stretch + SEED + 0x504c4154L), 3) == 0;
    }

    /** Corridor cut index for the near (0) or far (1) edge of a platform stretch's pit. */
    private int platformCut(long stretch, int side) {
        return (int) Long.remainderUnsigned(random(stretch + SEED + 0x435554L) >>> (side * 16), GAP_COUNT);
    }

    /** First open pixel of a platform stretch's pit. */
    public long platformPitStart(long stretch) {
        return (stretch * 4 + 3) * 512 - gapWidthAt(platformCut(stretch, 0)) / 2;
    }

    /** First far-bank pixel after a platform stretch's pit. */
    public long platformPitEnd(long stretch) {
        return (stretch * 4 + 3) * 512 + 256 + gapWidthAt(platformCut(stretch, 1)) / 2;
    }

    /** Surface of the generated terrain, including translated and mirrored columns. */
    public int floorAt(long worldX) {
        long section = Math.floorDiv(worldX, 512);
        int x = Math.floorMod(worldX, 512);
        int side = x < 256 ? 0 : 1;
        int tier = tierAt(section, side);
        if (section == 0) return seamHeight + tierOffset(tier);
        if (platformRun(section)) {
            long stretch = Math.floorDiv(section, 4);
            return worldX >= platformPitStart(stretch) && worldX < platformPitEnd(stretch)
                    ? -1 : seamHeight + tierOffset(tier);
        }
        if (isCorridor(section)) {
            int width = gapWidth(section);
            return x >= 256 - width / 2 && x < 256 + width / 2 ? -1 : seamHeight + tierOffset(tier);
        }
        return floorProfiles.get(tier).get(sectionIndex(section, tier))[x];
    }

    /** Top floor in a decoded S1 column; rejects ceilings, walls and missing floor. */
    public static int floor(Level level, int column, int x) {
        return floorBelow(level, column, x, 0);
    }

    /** First floor surface at or below {@code fromY}, or -1 when a ceiling or wall comes first. */
    public static int floorBelow(Level level, int column, int x, int fromY) {
        for (int y = Math.max(0, fromY); y < level.getLayerHeightBlocks(0) * 256; y++) {
            if (surface(level, column, x, y)) return y;
            if (solid(level, column, x, y)) return -1;
        }
        return -1;
    }

    /** Top pixel of a standable floor: solid from a floor-capable tile with open space above. */
    static boolean surface(Level level, int column, int x, int y) {
        if (solid(level, column, x, y - 1)) return false;
        ChunkDesc desc = desc(level, column, x, y);
        if (desc == null) return false;
        CollisionMode mode = desc.getPrimaryCollisionMode();
        if (mode != CollisionMode.ALL_SOLID && mode != CollisionMode.TOP_SOLID) return false;
        int h = height(level, desc, x);
        // Layout values are full block indices here: the ROM loader already strips
        // S1's loop flag, and generated layouts may use indices above 0x7F.
        return !desc.getVFlip() && h > 0 && h <= 16 && Math.floorMod(y, 16) == 16 - h;
    }

    /** Any primary collision at this pixel, including walls, ceilings and top-solid ledges. */
    static boolean solid(Level level, int column, int x, int y) {
        if (y < 0) return false;
        ChunkDesc desc = desc(level, column, x, y);
        if (desc == null || desc.getPrimaryCollisionMode() == CollisionMode.NO_COLLISION) return false;
        int h = height(level, desc, x);
        if (h == 0 || h > 16 || h < -16) return false;
        int row = desc.getVFlip() ? 15 - Math.floorMod(y, 16) : Math.floorMod(y, 16);
        return h > 0 ? row >= 16 - h : row < -h;
    }

    private static ChunkDesc desc(Level level, int column, int x, int y) {
        if (y < 0 || y >= level.getLayerHeightBlocks(0) * 256) return null;
        int id = Byte.toUnsignedInt(level.getMap().getValue(0, column, y / 256));
        if (id >= level.getBlockCount()) return null;
        return level.getBlock(id).getChunkDesc(x / 16, (y % 256) / 16);
    }

    private static int height(Level level, ChunkDesc desc, int x) {
        int solid = level.getChunk(desc.getChunkIndex()).getSolidTileIndex();
        if (solid == 0) return 0;
        return level.getSolidTile(solid).getHeightAt((byte) (desc.getHFlip() ? 15 - x % 16 : x % 16));
    }
}
