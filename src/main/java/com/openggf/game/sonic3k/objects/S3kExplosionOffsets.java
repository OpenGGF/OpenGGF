package com.openggf.game.sonic3k.objects;

/** Native {@code loc_83E90} random positioning shared by explosion controllers. */
final class S3kExplosionOffsets {
    private S3kExplosionOffsets() { }

    static int xOffset(int random, int range) {
        return (random & ((range * 2) - 1)) - range;
    }

    static int yOffset(int random, int range) {
        // loc_83E90 uses SWAP after X: the Y mask consumes the high word.
        return ((random >>> 16) & ((range * 2) - 1)) - range;
    }
}
