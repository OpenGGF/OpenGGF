package com.openggf.game.mutators;

/** Explicit native/creator motion admission; managers never guess writable coordinates from render art. */
@com.openggf.game.ModApi
public interface NativeObjectFieldTarget {
    @com.openggf.game.ModApi
    enum Kind { BADNIK, PROJECTILE }
    Kind fieldKind();
    /** Opt-in for grounded field motion and stasis; other targets retain their normal routine admission. */
    default boolean supportsGroundedFieldMotion() { return false; }
    void displaceForField(int dx, int dy);
}
