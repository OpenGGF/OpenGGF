package com.openggf.level.objects;

/**
 * Owns the object-visible VBlank counter and its game-profile service edge.
 */
final class VBlankClock {
    private int value;

    int value() {
        return value;
    }

    void initialize(int initialValue) {
        value = initialValue;
    }

    void advance(ObjectServices services) {
        value++;
        if (services != null && services.gameModule() != null) {
            services.gameModule().getLevelInitProfile().serviceLevelLoadVBlank();
        }
    }
}
