package starpost.realvalley;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-encodes a row of Sonic 1 256-pixel blocks as a Sonic 3&amp;K format-v2 level.
 *
 * <ul>
 *   <li>Each 256-pixel block becomes 2x2 Sonic 3&amp;K 128-pixel blocks (8x8 chunks), keeping every
 *       chunk word's flips and solidity bits.</li>
 *   <li>Patterns, chunks and blocks are compacted to the ones the valley reaches; pattern, chunk and
 *       block 0 stay empty, as the layout's "nothing here".</li>
 *   <li>Sonic 1's collision profiles (heights, widths, angles) are compacted by exact contents.
 *       Relative loop-path flips are baked into profiles when both paths share one chunk word.</li>
 *   <li>The loop: Sonic 1 draws the loop block and, behind the loop ({@code Sonic_Loops}' low plane),
 *       collides with the next block. Here the loop block's primary path is its own collision and its
 *       secondary path is the twin's; PATH_SWAP objects switch planes (see {@link ValleyLevel}).</li>
 *   <li>Palette: lines 1-3 keep their Sonic 1 cells. The S3K host owns line 0 and the HUD's line-1
 *       cells, so art drawn with those cells is moved to free cells holding the same colour (patterns
 *       duplicated with remapped pixels where needed). One extra cell is kept free for the
 *       placeholder's claim, which the host bridge re-submits every frame.</li>
 * </ul>
 */
public final class ValleyEncoder {
    /** Line-1 cells the S3K lives HUD owns ({@code S3kHudPaletteUseContract}, content-mods.md). */
    private static final int HUD_LINE1_MASK = (1 << 1) | (1 << 5) | (1 << 12) | (1 << 14) | (1 << 15);

    /**
     * What to encode.
     *
     * @param columns       Sonic 1 block id per 256-pixel column, west to east
     * @param skyRows       empty 128-pixel rows above the blocks
     * @param loopBlock     the block whose second collision path comes from {@code loopTwin} (or -1)
     * @param loopTwin      the loop block's low-plane twin
     * @param freeLine      palette line of the cell the art must not use (the placeholder's claim)
     * @param freeColor     colour of that cell
     */
    public record Spec(int[] columns, int skyRows, int loopBlock, int loopTwin, int freeLine, int freeColor) {
    }

    /** One remapped use of a source pattern: its new index and palette line. */
    private record PatternUse(int pattern, int line) {
    }

    private final S1Terrain source;
    private final Spec spec;
    private final List<byte[]> patterns = new ArrayList<>();
    private final Map<String, Integer> patternIds = new HashMap<>();
    private final Map<Integer, PatternUse> uses = new HashMap<>();
    private final int[][] claims = new int[4][16];
    private final List<int[]> chunks = new ArrayList<>();
    private final List<int[]> chunkCollision = new ArrayList<>();
    private final Map<String, Integer> chunkIds = new HashMap<>();
    private final List<int[]> blocks = new ArrayList<>();
    private final Map<String, Integer> blockIds = new HashMap<>();
    private final List<byte[]> profiles = new ArrayList<>();
    private final Map<String, Integer> profileIds = new HashMap<>();
    private int remappedUses;

    private ValleyEncoder(S1Terrain source, Spec spec) {
        this.source = source;
        this.spec = spec;
        for (int[] line : claims) {
            Arrays.fill(line, -1);
        }
        profiles.add(new byte[33]);
        profileIds.put(Arrays.toString(new byte[33]), 0);
        patterns.add(new byte[64]);
        patternIds.put("blank", 0);
        chunks.add(new int[4]);
        chunkCollision.add(new int[2]);
        chunkIds.put(Arrays.toString(new int[6]), 0);
        blocks.add(new int[64]);
        blockIds.put(Arrays.toString(new int[64]), 0);
    }

    public static EncodedValley encode(S1Terrain source, Spec spec) {
        return new ValleyEncoder(source, spec).run();
    }

    private EncodedValley run() {
        int width = spec.columns().length * 2;
        int height = spec.skyRows() + 2;
        // Palette first: lines 1-3 keep their own cells, then everything else is packed around them.
        List<int[]> reached = reachedPatternUses();
        claims[2][0] = source.color(2, 0);       // the backdrop (S1's VDP register 7 = $8720)
        for (int[] use : reached) {
            if (keepsOwnCells(use[0], use[1])) {
                for (int c : coloursOf(use[0])) {
                    claims[use[1]][c] = source.color(use[1], c);
                }
                uses.put(key(use[0], use[1]), new PatternUse(patternId(use[0], identity()), use[1]));
            }
        }
        for (int[] use : reached) {
            if (!uses.containsKey(key(use[0], use[1]))) {
                uses.put(key(use[0], use[1]), remap(use[0], use[1]));
                remappedUses++;
            }
        }

        byte[] foreground = new byte[width * height];
        byte[] background = new byte[width * height];
        for (int column = 0; column < spec.columns().length; column++) {
            int block = spec.columns()[column];
            int twin = block == spec.loopBlock() ? spec.loopTwin() : -1;
            for (int q = 0; q < 4; q++) {
                int x = column * 2 + q % 2;
                int y = spec.skyRows() + q / 2;
                foreground[y * width + x] = (byte) quadrant(block, twin, q % 2, q / 2, true);
            }
        }
        for (int y = 0; y < height; y++) {
            int sourceRow = y / 2;
            if (sourceRow >= source.bgHeight()) {
                continue;
            }
            for (int x = 0; x < width; x++) {
                int block = source.bgLayout()[sourceRow * source.bgWidth() + (x / 2) % source.bgWidth()];
                background[y * width + x] = (byte) quadrant(block, -1, x % 2, y % 2, false);
            }
        }
        if (blocks.size() > 256 || chunks.size() > 1024 || patterns.size() > 2048 || profiles.size() > 256) {
            throw new IllegalStateException("The valley exceeds Sonic 3&K's limits: " + blocks.size()
                    + " blocks, " + chunks.size() + " chunks, " + patterns.size() + " patterns");
        }
        return new EncodedValley(width, height, patternBytes(), chunkBytes(), blockBytes(), foreground,
                background, profileBytes(0, 16), profileBytes(16, 16), profileBytes(32, 1),
                collisionColumn(0), collisionColumn(1), claimTriples(), patterns.size(), chunks.size(),
                blocks.size(), remappedUses);
    }

    // ---- palette ----

    /** Every (pattern, line) pair drawn by the reached foreground and background chunks. */
    private List<int[]> reachedPatternUses() {
        List<int[]> result = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.Set<Integer> reachedBlocks = new java.util.TreeSet<>();
        for (int block : spec.columns()) {
            reachedBlocks.add(block);
        }
        for (int block : source.bgLayout()) {
            reachedBlocks.add(block);
        }
        for (int block : reachedBlocks) {
            if (block <= 0 || block >= source.blocks().length) {
                continue;
            }
            for (int word : source.blocks()[block]) {
                for (int patternWord : source.chunks()[word & 0x3FF]) {
                    int pattern = patternWord & 0x7FF;
                    int line = (patternWord >> 13) & 3;
                    if (seen.add(key(pattern, line))) {
                        result.add(new int[] {pattern, line});
                    }
                }
            }
        }
        return result;
    }

    private boolean keepsOwnCells(int pattern, int line) {
        if (line == 0) {
            return false;
        }
        for (int c : coloursOf(pattern)) {
            if (reserved(line, c)) {
                return false;
            }
        }
        return true;
    }

    private boolean reserved(int line, int color) {
        return line == 0 || line == 1 && (HUD_LINE1_MASK & (1 << color)) != 0
                || line == spec.freeLine() && color == spec.freeColor();
    }

    /** Moves a pattern's colours to cells holding the same colours on some line 1-3. */
    private PatternUse remap(int pattern, int line) {
        int[] colours = coloursOf(pattern);
        int[] order = line == 0 ? new int[] {2, 3, 1} : new int[] {line, line == 1 ? 2 : 1, line == 3 ? 2 : 3};
        for (int target : order) {
            int[] map = identity();
            int[] tentative = claims[target].clone();
            boolean fits = true;
            for (int c : colours) {
                int word = source.color(line, c);
                int cell = find(tentative, target, word);
                if (cell < 0) {
                    fits = false;
                    break;
                }
                tentative[cell] = word;
                map[c] = cell;
            }
            if (fits) {
                claims[target] = tentative;
                return new PatternUse(patternId(pattern, map), target);
            }
        }
        throw new IllegalStateException("No palette line has room for pattern " + pattern + " (line " + line
                + ") beside the host's reserved cells");
    }

    /** A claimed cell already holding {@code word}, else a free unreserved one; -1 when none. */
    private int find(int[] line, int lineIndex, int word) {
        for (int c = 1; c < 16; c++) {
            if (line[c] == word && !reserved(lineIndex, c)) {
                return c;
            }
        }
        for (int c = 1; c < 16; c++) {
            if (line[c] < 0 && !reserved(lineIndex, c)) {
                return c;
            }
        }
        return -1;
    }

    private int[] coloursOf(int pattern) {
        boolean[] used = new boolean[16];
        for (byte pixel : source.pixels()[pattern]) {
            used[pixel & 0xF] = true;
        }
        int count = 0;
        for (int c = 1; c < 16; c++) {
            count += used[c] ? 1 : 0;
        }
        int[] result = new int[count];
        int n = 0;
        for (int c = 1; c < 16; c++) {
            if (used[c]) {
                result[n++] = c;
            }
        }
        return result;
    }

    private static int[] identity() {
        int[] map = new int[16];
        for (int i = 0; i < 16; i++) {
            map[i] = i;
        }
        return map;
    }

    private int patternId(int pattern, int[] map) {
        String key = pattern + Arrays.toString(map);
        Integer id = patternIds.get(key);
        if (id == null) {
            byte[] remapped = new byte[64];
            byte[] pixels = source.pixels()[pattern];
            for (int i = 0; i < 64; i++) {
                remapped[i] = (byte) map[pixels[i] & 0xF];
            }
            id = patterns.size();
            patterns.add(remapped);
            patternIds.put(key, id);
        }
        return id;
    }

    private static int key(int pattern, int line) {
        return pattern << 2 | line;
    }

    // ---- chunks and blocks ----

    /**
     * Encodes one 128-pixel quadrant of a Sonic 1 block and returns its block id. With a twin, the
     * twin's collision becomes the secondary path; without, both paths are the block's own.
     */
    private int quadrant(int block, int twin, int qx, int qy, boolean solid) {
        if (block <= 0 || block >= source.blocks().length) {
            return 0;
        }
        int[] words = new int[64];
        for (int cy = 0; cy < 8; cy++) {
            for (int cx = 0; cx < 8; cx++) {
                int index = (qy * 8 + cy) * 16 + qx * 8 + cx;
                int word = source.blocks()[block][index];
                words[cy * 8 + cx] = twin < 0 ? cell(word, solid) : loopCell(word, source.blocks()[twin][index]);
            }
        }
        String key = Arrays.toString(words);
        Integer id = blockIds.get(key);
        if (id == null) {
            id = blocks.size();
            blocks.add(words);
            blockIds.put(key, id);
        }
        return id;
    }

    /** A plain cell: the same chunk, flips and solidity, collision on both paths. */
    private int cell(int word, boolean solid) {
        int chunk = word & 0x3FF;
        int collision = profileId(source.collision()[chunk], 0);
        int id = chunkId(chunk, 0, collision, collision);
        return id | (word & 0x0C00) | (solid ? word & 0xF000 : 0);
    }

    /**
     * A loop cell: drawn from the loop block, colliding as the loop block on the primary path and as
     * its twin on the secondary path. The cell's flips must suit whichever paths are solid; the art is
     * pre-flipped inside a new chunk when they differ from the loop block's.
     */
    private int loopCell(int front, int behind) {
        int primarySolidity = (front >> 12) & 3;
        int secondarySolidity = (behind >> 12) & 3;
        int frontFlip = (front >> 10) & 3;
        int behindFlip = (behind >> 10) & 3;
        int primary = primarySolidity != 0 ? source.collision()[front & 0x3FF] : 0;
        int secondary = secondarySolidity != 0 ? source.collision()[behind & 0x3FF] : 0;
        int flip;
        if (primary != 0) {
            flip = frontFlip;

        } else if (secondary != 0) {
            flip = behindFlip;
        } else {
            flip = frontFlip;
        }
        // S3K has one flip field for both paths. Bake the relative reflection into the
        // twin's heights, widths and angle; no source geometry is approximated.
        primary = profileId(primary, frontFlip ^ flip);
        secondary = profileId(secondary, behindFlip ^ flip);
        int id = chunkId(front & 0x3FF, frontFlip ^ flip, primary, secondary);
        return id | flip << 10 | primarySolidity << 12 | secondarySolidity << 14;
    }

    /** The compacted chunk for a source chunk drawn with {@code bakedFlip} (bit 0 X, bit 1 Y). */
    private int chunkId(int chunk, int bakedFlip, int primary, int secondary) {
        int[] words = new int[4];
        for (int i = 0; i < 4; i++) {
            int word = source.chunks()[chunk][i];
            int pattern = word & 0x7FF;
            int line = (word >> 13) & 3;
            PatternUse use = uses.get(key(pattern, line));
            if (use == null) {
                throw new IllegalStateException("Pattern " + pattern + " was not reached by the palette pass");
            }
            words[i] = (word & 0x9800) | use.line() << 13 | use.pattern();
        }
        if ((bakedFlip & 1) != 0) {
            words = new int[] {words[1] ^ 0x0800, words[0] ^ 0x0800, words[3] ^ 0x0800, words[2] ^ 0x0800};
        }
        if ((bakedFlip & 2) != 0) {
            words = new int[] {words[2] ^ 0x1000, words[3] ^ 0x1000, words[0] ^ 0x1000, words[1] ^ 0x1000};
        }
        int[] key = {words[0], words[1], words[2], words[3], primary, secondary};
        String text = Arrays.toString(key);
        Integer id = chunkIds.get(text);
        if (id == null) {
            id = chunks.size();
            chunks.add(words);
            chunkCollision.add(new int[] {primary, secondary});
            chunkIds.put(text, id);
        }
        return id;
    }

    /** A reflected source collision profile, compacted by all 33 bytes (including its angle). */
    private int profileId(int sourceId, int flip) {
        if (sourceId == 0) return 0;
        byte[] profile = new byte[33];
        for (int i = 0; i < 16; i++) {
            int hx = (flip & 1) != 0 ? 15 - i : i;
            int wy = (flip & 2) != 0 ? 15 - i : i;
            int height = source.heights()[sourceId * 16 + hx];
            int width = source.widths()[sourceId * 16 + wy];
            // FindFloor leaves the full-tile sentinel $10 positive under a vertical flip.
            profile[i] = (byte) ((flip & 2) != 0 && height != 16 ? -height : height);
            profile[16 + i] = (byte) ((flip & 1) != 0 ? -width : width);
        }
        int angle = source.angles()[sourceId] & 255;
        if ((flip & 1) != 0) angle = -angle;
        if ((flip & 2) != 0) angle = 0x80 - angle;
        profile[32] = (byte) angle;
        String key = Arrays.toString(profile);
        Integer id = profileIds.get(key);
        if (id == null) {
            id = profiles.size();
            profiles.add(profile);
            profileIds.put(key, id);
        }
        return id;
    }

    private byte[] profileBytes(int offset, int size) {
        byte[] out = new byte[profiles.size() * size];
        for (int i = 0; i < profiles.size(); i++) {
            System.arraycopy(profiles.get(i), offset, out, i * size, size);
        }
        return out;
    }

    // ---- output ----

    private byte[] patternBytes() {
        byte[] out = new byte[patterns.size() * 32];
        for (int p = 0; p < patterns.size(); p++) {
            byte[] pixels = patterns.get(p);
            for (int i = 0; i < 32; i++) {
                out[p * 32 + i] = (byte) ((pixels[i * 2] & 0xF) << 4 | pixels[i * 2 + 1] & 0xF);
            }
        }
        return out;
    }

    private byte[] chunkBytes() {
        byte[] out = new byte[chunks.size() * 8];
        for (int c = 0; c < chunks.size(); c++) {
            for (int i = 0; i < 4; i++) {
                out[c * 8 + i * 2] = (byte) (chunks.get(c)[i] >> 8);
                out[c * 8 + i * 2 + 1] = (byte) chunks.get(c)[i];
            }
        }
        return out;
    }

    private byte[] blockBytes() {
        byte[] out = new byte[blocks.size() * 128];
        for (int b = 0; b < blocks.size(); b++) {
            for (int i = 0; i < 64; i++) {
                out[b * 128 + i * 2] = (byte) (blocks.get(b)[i] >> 8);
                out[b * 128 + i * 2 + 1] = (byte) blocks.get(b)[i];
            }
        }
        return out;
    }

    private int[] collisionColumn(int path) {
        int[] out = new int[chunkCollision.size()];
        for (int c = 0; c < out.length; c++) {
            out[c] = chunkCollision.get(c)[path];
        }
        return out;
    }

    /** Claimed cells as {line, colour, Genesis word} triples. */
    private int[][] claimTriples() {
        List<int[]> out = new ArrayList<>();
        for (int line = 1; line < 4; line++) {
            for (int c = 0; c < 16; c++) {
                if (claims[line][c] >= 0) {
                    out.add(new int[] {line, c, claims[line][c]});
                }
            }
        }
        return out.toArray(new int[0][]);
    }
}
