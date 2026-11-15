package com.openggf.game.soniccd.special;

/**
 * The special stage's arithmetic as the sub-CPU program computes it. These helpers reproduce
 * the integer results of the program's multiply, shift and divide steps (their widths,
 * truncation and the divide overflow case), not any CPU behaviour beyond those results.
 */
final class CdSpecialMath {
    private CdSpecialMath() { }

    static int s16(int value) { return (short) value; }

    /** {@code muls.w}: signed 16 x 16 to 32. */
    static int muls(int a, int b) { return (short) a * (short) b; }

    /**
     * {@code divs.w}: signed 32 / 16 truncating toward zero, quotient in the low word and
     * remainder in the high word. When the quotient does not fit 16 bits the destination is
     * left unchanged, so callers that keep only the low word read the dividend's low word.
     */
    static int divs(int dividend, int divisor) {
        int d = (short) divisor;
        long q = (long) dividend / d;
        if (q < Short.MIN_VALUE || q > Short.MAX_VALUE) return dividend;
        int r = (int) (dividend - q * d);
        return (r << 16) | ((int) q & 0xFFFF);
    }

    /**
     * {@code GetSine}/{@code GetCosine} (scddisasm/src/Special Stage/Sub.asm:2632-2653): 512 steps per turn, a 128-entry
     * quarter table mirrored by bit 7 and negated by bit 8. Values are 8.8 fixed point.
     */
    static int sine(short[] quarterTable, int angle) {
        int value = angle & 0x1FF;
        int index = value;
        if ((value & 0x80) != 0) index = ~index;
        int result = quarterTable[index & 0x7F];
        if ((value & 0x100) != 0) result = -result;
        return (short) result;
    }

    static int cosine(short[] quarterTable, int angle) {
        return sine(quarterTable, angle + 0x80);
    }
}
