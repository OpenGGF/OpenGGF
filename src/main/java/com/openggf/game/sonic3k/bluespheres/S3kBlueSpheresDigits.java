package com.openggf.game.sonic3k.bluespheres;

/**
 * The number rows of Obj_SpheresTitle_4D03E: eleven digit children after the
 * label object, the first and last holding bracket frames.
 */
final class S3kBlueSpheresDigits {
    /** {@code Map_BlueSpheresNum} frame for an empty cell. */
    static final int BLANK = 0x0A;
    private static final int[] POWERS = {
            100_000_000, 10_000_000, 1_000_000, 100_000, 10_000, 1_000, 100, 10, 1 };

    private S3kBlueSpheresDigits() {
    }

    /**
     * sub_4D0F6 (sonic3k.asm:100821-100859): nine digits with leading zeros
     * into children 2-10, the row starting at x {@code $F7}.
     */
    static void stage(S3kBlueSpheresObject[] slots, int parent, int value) {
        long remaining = value & 0x0FFFFFFFL;
        for (int i = 0; i < 9; i++) {
            slots[parent + 2 + i].frame = (int) (remaining / POWERS[i]);
            remaining %= POWERS[i];
        }
        for (int i = 0; i < 11; i++) slots[parent + 1 + i].setX(0xF7 + i * 8);
    }

    /**
     * sub_4D13A (sonic3k.asm:100860-100922): leading zeros blank, and the
     * row slides right four pixels per blank so the digits stay centred.
     */
    static void level(S3kBlueSpheresObject[] slots, int parent, int value) {
        long remaining = value & 0x7FFFFFFL;
        int significant = 0;
        for (int i = 0; i < 9; i++) {
            int digit = (int) (remaining / POWERS[i]);
            remaining %= POWERS[i];
            if (digit != 0 && significant == 0) significant = 9 - i;
            slots[parent + 2 + i].frame = significant == 0 ? BLANK : digit;
        }
        int blanks = 9 - significant;
        int x = 0xF7 + blanks * 4;
        int child = parent + 1;
        for (int i = 0; i <= blanks; i++) slots[child++].setX(x);
        for (int i = 0; i <= significant; i++) {
            x += 8;
            slots[child++].setX(x);
        }
    }
}
