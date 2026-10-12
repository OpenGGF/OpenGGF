package com.openggf.game.sonic3k.render;

/** Shared native/scene widescreen sampling of DEZ1's fixed H40 interior. */
public final class DezInteriorBackground {
    private DezInteriorBackground() {}

    public record Tile(int x, boolean flip) {}

    public static Tile tile(int sourceX, int width) {
        int x = sourceX - ((width - 320) / 16) * 8;
        boolean flip = false;
        if (x < 0) {
            int tile = Math.floorMod(Math.floorDiv(x, 8), 8);
            flip = tile >= 4;
            x = (flip ? 7 - tile : tile) * 8;
        } else if (x >= 320) {
            int tile = Math.floorMod(Math.floorDiv(x - 320, 8), 8);
            flip = tile < 4;
            x = (flip ? 39 - tile : 32 + tile) * 8;
        }
        return new Tile(x, flip);
    }

    public static int pixel(int sourceX, int width) {
        Tile tile = tile(sourceX, width);
        int fine = Math.floorMod(sourceX - ((width - 320) / 16) * 8, 8);
        return (tile.x() & ~7) + (tile.flip() ? 7 - fine : fine);
    }
}
