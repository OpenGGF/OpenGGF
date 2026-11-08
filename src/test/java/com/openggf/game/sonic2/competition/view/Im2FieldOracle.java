package com.openggf.game.sonic2.competition.view;

/** Test-only native 8x16 sampler, retained independently from the production projection. */
final class Im2FieldOracle {
    private Im2FieldOracle() { }
    static int pixel(byte[] nativePatterns, int cell, int x, int y) {
        int packed = nativePatterns[(cell & 1023) * 64 + y * 4 + x / 2] & 255;
        return (packed >>> ((x & 1) == 0 ? 4 : 0)) & 15;
    }
}
