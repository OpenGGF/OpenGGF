package com.openggf.level.objects;

/**
 * Semantic mutation hook for collision-response-list entries in the ROM's
 * powered full-screen attack special category.
 */
public interface PoweredScreenAttackSpecial {

    /** Applies the ROM collision-property bit mask owned by the target object. */
    void orCollisionProperty(int mask);
}
