package com.openggf.route;

/**
 * The eight physical Genesis three-button pad levels a route row records. There is no logical
 * jump bit and no debug or system channel: a row names exactly the buttons BizHawk would hold.
 * The bit order is the tape's packing order and never changes.
 */
public enum PhysicalButton {
    UP('U'), DOWN('D'), LEFT('L'), RIGHT('R'), START('S'), A('A'), B('B'), C('C');

    private final char letter;

    PhysicalButton(char letter) {
        this.letter = letter;
    }

    /** Bit within one lane's 8-bit mask. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Letter used by route scripts and OpenGGF input logs. */
    public char letter() {
        return letter;
    }
}
