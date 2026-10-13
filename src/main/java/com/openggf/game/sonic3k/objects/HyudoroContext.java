package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectInstance;

/** Captured managed identity supplying the native ghost's darkness and count ownership. */
public interface HyudoroContext extends ObjectInstance {
    int darkness();
    void ghostDeleted();
}
