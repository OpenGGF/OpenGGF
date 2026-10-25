package com.openggf.game.competition;

import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Internal, world-owned replacement for a native human's death/respawn dispatch.
 * Returning false leaves the ordinary movement path in charge. Implementations
 * must check current roster identity and must not acquire CPU followers.
 */
public interface NativePlayerDeathPass {
    /** Runs movement only; the normal caller retains sensor and animation ownership. */
    boolean runDeathPass(AbstractPlayableSprite actor);

    /** Queried at dispatch start; a final respawn call remains a non-control pass. */
    default boolean ownsDispatch(AbstractPlayableSprite actor) { return false; }

    /** Routine 8 can omit animation without moving animation ownership into the service. */
    default boolean animatesThisPass(AbstractPlayableSprite actor) { return true; }

    static NativePlayerDeathPass from(AbstractPlayableSprite actor) {
        var level = actor.currentLevelManagerIfAvailable();
        return level == null ? null : WorldSessionPolicyAccess.getService(
                level.getWorldSession(), NativePlayerDeathPass.class);
    }
}
