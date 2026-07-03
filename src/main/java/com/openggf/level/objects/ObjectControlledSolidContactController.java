package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

/**
 * Narrow seam for carry states that need selected solid callbacks while the player is
 * otherwise object-controlled.
 */
public interface ObjectControlledSolidContactController {
    boolean allowsObjectControlledSolidContact(PlayableEntity player, ObjectInstance candidate);

    /**
     * Returns whether this controller currently owns {@code player} through an
     * active carry state. The rewind post-restore pass uses this to relink the
     * player's carry-owner reference to the restored controller instance (the
     * live reference is not part of any snapshot and goes stale when the
     * restore recreates the owning object).
     */
    default boolean ownsCarriedPlayerForRewind(PlayableEntity player) {
        return false;
    }
}
