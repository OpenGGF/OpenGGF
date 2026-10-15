package com.openggf.level.render;

import com.openggf.level.Pattern;

/** Decimal presentation using column-major, two-tile ROM digits. */
public final class NumericPatternWriter {
    private NumericPatternWriter() {
    }

    /**
     * Writes four bonus digits, blanking leading zeroes but retaining the ones digit.
     * An oversized leading digit or incomplete destination pair is left untouched.
     * Leading blank pairs require allocated destination tiles, as in the results screens.
     */
    public static void writeBonus(Pattern[] dest, int startIndex, int value,
                                  Pattern[] digits, Pattern blank) {
        writeBonus(dest, startIndex, value, digits, blank, true);
    }

    /**
     * Writes four variable tens-of-points digits for a mapping that already
     * appends a fixed zero. At zero all four variable columns become blank.
     * S1/S2 Hud_TimeRingBonus use this layout; the default writer's standalone
     * ones digit remains available to other consumers.
     */
    public static void writeBonusWithFixedZero(Pattern[] dest, int startIndex, int value,
                                             Pattern[] digits, Pattern blank) {
        writeBonus(dest, startIndex, value, digits, blank, false);
    }

    private static void writeBonus(Pattern[] dest, int startIndex, int value,
                                   Pattern[] digits, Pattern blank, boolean retainOnesDigit) {
        int divisor = 1000;
        boolean hasDigit = false;
        for (int i = 0; i < 4; i++) {
            int digit = value / divisor;
            value %= divisor;
            int tileIndex = startIndex + i * 2;
            if (digit != 0 || hasDigit || (retainOnesDigit && i == 3)) {
                hasDigit = true;
                copyDigit(dest, tileIndex, digit, digits);
            } else {
                dest[tileIndex].copyFrom(blank);
                dest[tileIndex + 1].copyFrom(blank);
            }
            divisor /= 10;
        }
    }

    public static void copyDigit(Pattern[] dest, int destIndex, int digit, Pattern[] digits) {
        int srcIndex = digit * 2;
        if (srcIndex + 1 >= digits.length || destIndex + 1 >= dest.length) {
            return;
        }
        dest[destIndex].copyFrom(digits[srcIndex]);
        dest[destIndex + 1].copyFrom(digits[srcIndex + 1]);
    }
}
