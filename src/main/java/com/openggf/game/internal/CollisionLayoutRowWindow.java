package com.openggf.game.internal;

/**
 * A level whose block lookup masks the collision layout row with a window of its own
 * rather than its game's {@code CollisionRules.defaultCollisionLayoutYMask()}: the
 * layout RAM and the mask are assembled into each stage program, so the loaded level
 * owns them. The level manager's layout lookups and the player's terrain sensors both
 * apply it. Rows past the loaded layout read as blank, as cleared layout RAM does.
 */
public interface CollisionLayoutRowWindow {
    /** The Y mask applied to every collision lookup: a window of {@code mask + 1} pixels. */
    int collisionLayoutYMask();
}
