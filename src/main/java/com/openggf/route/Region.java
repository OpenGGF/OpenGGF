package com.openggf.route;

/** A world-coordinate rectangle, inclusive, in ROM centre coordinates. */
public record Region(int left, int top, int right, int bottom) {
    public Region {
        if (right < left || bottom < top) throw new IllegalArgumentException("empty region " + left + "," + top
                + ".." + right + "," + bottom);
    }

    public static Region around(int x, int y, int halfWidth, int halfHeight) {
        return new Region(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
    }

    public boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}
