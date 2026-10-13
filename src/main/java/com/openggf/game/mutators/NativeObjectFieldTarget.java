package com.openggf.game.mutators;

/** Explicit native motion admission; managers never guess writable coordinates from render art. */
public interface NativeObjectFieldTarget {
    enum Kind { BADNIK, PROJECTILE }
    Kind fieldKind();
    void displaceForField(int dx, int dy);
}
