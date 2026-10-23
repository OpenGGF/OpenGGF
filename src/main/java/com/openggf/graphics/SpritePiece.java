package com.openggf.graphics;

/**
 * Immutable admitted sprite geometry. Source tiles are numbered column-major;
 * flips affect their destinations, while clipping selects an unflipped source window.
 */
public record SpritePiece(
        int x, int y, int widthTiles, int heightTiles,
        int firstPatternIndex, int rawTileWordLow11, int paletteIndex,
        boolean hFlip, boolean vFlip, boolean piecePriority, boolean globalHighPriority,
        SpriteMaskReplayRole maskReplayRole,
        int startColTile, int colCountTiles, int startRowTile, int rowCountTiles,
        String debugSource) {

    public SpritePiece {
        validateSourceWindow(widthTiles, heightTiles, startColTile, colCountTiles, startRowTile, rowCountTiles);
    }

    /** Shared with the pinned legacy input record; retain its validation and null semantics. */
    public static void validateSourceWindow(int width, int height, int startCol, int cols, int startRow, int rows) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Tile dimensions must be non-negative");
        }
        if (startCol < 0 || cols < 0 || startCol + cols > width) {
            throw new IllegalArgumentException("Clipped columns must stay within the piece width");
        }
        if (startRow < 0 || rows < 0 || startRow + rows > height) {
            throw new IllegalArgumentException("Clipped rows must stay within the piece height");
        }
    }

    public SpritePiece clipRect(int col, int cols, int row, int rows) {
        return new SpritePiece(x, y, widthTiles, heightTiles, firstPatternIndex, rawTileWordLow11, paletteIndex,
                hFlip, vFlip, piecePriority, globalHighPriority, maskReplayRole, col, cols, row, rows, debugSource);
    }

    public SpritePiece withDebugSource(String source) {
        return new SpritePiece(x, y, widthTiles, heightTiles, firstPatternIndex, rawTileWordLow11, paletteIndex,
                hFlip, vFlip, piecePriority, globalHighPriority, maskReplayRole,
                startColTile, colCountTiles, startRowTile, rowCountTiles, source);
    }

    public SpritePiece withMaskReplayRole(SpriteMaskReplayRole role) {
        return new SpritePiece(x, y, widthTiles, heightTiles, firstPatternIndex, rawTileWordLow11, paletteIndex,
                hFlip, vFlip, piecePriority, globalHighPriority, role,
                startColTile, colCountTiles, startRowTile, rowCountTiles, debugSource);
    }

    public SpritePiece withPriorityFlags(boolean piece, boolean global) {
        return new SpritePiece(x, y, widthTiles, heightTiles, firstPatternIndex, rawTileWordLow11, paletteIndex,
                hFlip, vFlip, piece, global, maskReplayRole,
                startColTile, colCountTiles, startRowTile, rowCountTiles, debugSource);
    }

    public int endXExclusive() {
        return x + widthTiles * 8;
    }

    public int endYExclusive() {
        return y + heightTiles * 8;
    }

    public boolean effectiveHighPriority() {
        return piecePriority || globalHighPriority;
    }

    @FunctionalInterface
    public interface TileConsumer {
        void render(int patternIndex, boolean hFlip, boolean vFlip, int paletteIndex, int drawX, int drawY);
    }

    /** Expands synchronously in source-row/source-column order; no consumer is retained. */
    public void forEachTile(TileConsumer consumer) {
        for (int ty = 0; ty < rowCountTiles; ty++) {
            int sourceRow = startRowTile + ty;
            for (int tx = 0; tx < colCountTiles; tx++) {
                int sourceCol = startColTile + tx;
                int drawCol = hFlip ? widthTiles - 1 - sourceCol : sourceCol;
                int drawRow = vFlip ? heightTiles - 1 - sourceRow : sourceRow;
                int patternIndex = firstPatternIndex + sourceCol * heightTiles + sourceRow;
                consumer.render(patternIndex, hFlip, vFlip, paletteIndex, x + drawCol * 8, y + drawRow * 8);
            }
        }
    }
}
