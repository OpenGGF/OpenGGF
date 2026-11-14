package com.openggf.physics;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/** Engine-only terrain probes for game modules, without extending the creator collision API. */
public final class CollisionSystemInternalAccess {
    private CollisionSystemInternalAccess() { }

    /**
     * The projected wall distance at a caller-chosen probe angle; see
     * {@code CollisionSystem.projectedWallDistance}. Negative is the overlap,
     * {@link Integer#MAX_VALUE} when there is no level.
     */
    public static int projectedWallDistance(CollisionSystem collision, AbstractPlayableSprite player,
                                            int probeAngle) {
        return Objects.requireNonNull(collision, "collision").projectedWallDistance(player, probeAngle);
    }
}
