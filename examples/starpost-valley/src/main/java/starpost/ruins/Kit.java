package starpost.ruins;

/**
 * A zone's level blocks as chamber generation sees them: the stock layout and each block's
 * collision. {@link RomKit} reads Sonic 1's level kit; tests build small kits of their own, so
 * generation and the traversal check run without a ROM.
 */
public interface Kit {
    /** Block side in pixels (256 for Sonic 1). */
    int size();

    int columns();

    int rows();

    /** The block at a layout cell, 0 (open air) outside the layout. */
    int block(int column, int row);

    int blockCount();

    /**
     * A block's collision, {@code size * size} values row by row: {@code SceneLevelKit.EMPTY},
     * {@code TOP_SOLID} or {@code SOLID}. Shared and cached: never modify it.
     */
    byte[] solidity(int block);

    /** The stock act's playable area, {@code {x, y, width, height}} in pixels. */
    int[] area();

    /** Whether the block's picture is drawn at this pixel (false where the background shows). */
    boolean opaque(int block, int x, int y);
}
