package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectInstance;

/** Captured managed identity supplying the native ghost's darkness and count ownership. */
public interface HyudoroContext extends ObjectInstance {
    int darkness();
    /** Native SOZ opt-in only; encounter-local ghost controllers stay stock. */
    default boolean aggressiveAtCurrentLight() { return false; }
    void ghostDeleted();
}
