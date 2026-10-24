package com.openggf.graphics;

final class UnderwaterPaletteTransform {
    private UnderwaterPaletteTransform() {
    }

    static boolean eligible(int unsignedR, int unsignedG, int unsignedB) {
        return unsignedR + unsignedG + unsignedB > 0;
    }

    static int ratioX256(long normalSum, long waterSum) {
        return normalSum > 0 ? (int) (waterSum * 256 / normalSum) : 256;
    }

    static int scale(int unsignedChannel, int ratioX256) {
        return Math.min(255, unsignedChannel * ratioX256 / 256);
    }
}
