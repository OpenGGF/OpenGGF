package com.openggf.game.timing;

/** Ordered production service points at which hardware readiness becomes visible. */
public enum HardwareServiceBoundary {
    VINT_SERVICE,
    PRE_MAIN_LOOP,
    POST_OBJECTS;

    public static HardwareServiceBoundary fromWireName(String wireName) {
        return switch (wireName) {
            case "vint_service" -> VINT_SERVICE;
            case "pre_main_loop" -> PRE_MAIN_LOOP;
            case "post_objects" -> POST_OBJECTS;
            default -> throw new IllegalArgumentException("unknown boundary: " + wireName);
        };
    }
}
