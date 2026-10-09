package starpost.realvalley;

/**
 * The Sonic 1 level data the valley re-encodes, as plain arrays (no engine types), so the encoder can
 * be tested on small hand-made levels. {@link S1TerrainReader} fills it from the player's ROM.
 *
 * @param pixels     8x8 patterns, 64 colour indices each (row-major)
 * @param chunks     16x16 chunks, four pattern words each (TL, TR, BL, BR: {@code PCCV HIII IIII IIII})
 * @param collision  each chunk's collision profile index (Sonic 1 has one per chunk, for both paths)
 * @param blocks     256-pixel blocks, 256 chunk words each, row-major, in the Sonic 2/3&amp;K form
 *                   {@code SSTT YXII IIII IIII} (Sonic 1's one solidity pair already on both paths);
 *                   block 0 is the empty layout block
 * @param heights    16 column heights per collision profile
 * @param widths     16 row widths per collision profile
 * @param angles     one angle per collision profile
 * @param palette    the level's four palette lines as Genesis colour words (64 entries)
 * @param bgWidth    background layout width in 256-pixel blocks
 * @param bgHeight   background layout height in 256-pixel blocks
 * @param bgLayout   background block ids, row-major
 */
public record S1Terrain(byte[][] pixels, int[][] chunks, int[] collision, int[][] blocks, byte[] heights,
        byte[] widths, byte[] angles, int[] palette, int bgWidth, int bgHeight, int[] bgLayout) {

    /** Genesis colour word of palette line {@code line}, colour {@code color}. */
    public int color(int line, int color) {
        return palette[line * 16 + color];
    }
}
