package starpost.realvalley;

/**
 * A re-encoded valley in Sonic 3&amp;K format-v2 byte form (see {@code docs/modding/formats/level-definition.md}):
 * 32-byte patterns, 8-byte chunks, 128-byte (8x8) blocks, one byte per map cell, 16-byte height and
 * width profiles, one angle per profile and one collision index per chunk and path.
 *
 * @param claims        claimed palette cells as {line, colour, Genesis word}
 * @param remappedUses  pattern uses moved off the host's reserved palette cells
 */
public record EncodedValley(int width, int height, byte[] patterns, byte[] chunks, byte[] blocks,
        byte[] foreground, byte[] background, byte[] heights, byte[] widths, byte[] angles, int[] primary,
        int[] secondary, int[][] claims, int patternCount, int chunkCount, int blockCount, int remappedUses) {
}
