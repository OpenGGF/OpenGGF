package com.openggf.editor.selection;

/** A nonempty, half-open rectangle in world map cells. */
public record CellRectangle(int x, int y, int width, int height) {
    public CellRectangle {
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || (long) x + width > Integer.MAX_VALUE || (long) y + height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid world-cell rectangle");
        }
    }

    public static CellRectangle between(int x1, int y1, int x2, int y2) {
        return new CellRectangle(Math.min(x1, x2), Math.min(y1, y2),
                Math.addExact(Math.abs(Math.subtractExact(x2, x1)), 1),
                Math.addExact(Math.abs(Math.subtractExact(y2, y1)), 1));
    }

    public boolean containsPixel(int px, int py, int cellSize) {
        return px >= (long) x * cellSize && py >= (long) y * cellSize
                && px < (long) (x + width) * cellSize && py < (long) (y + height) * cellSize;
    }
}
