package com.openggf.level.objects;

/**
 * Owns only the object-visible VBlank counter.
 */
final class VBlankClock {
    private int value;

    int value() {
        return value;
    }

    void initialize(int initialValue) {
        value = initialValue;
    }

    void advance() {
        value++;
    }
}
