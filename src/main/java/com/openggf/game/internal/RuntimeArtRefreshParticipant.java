package com.openggf.game.internal;

/** Retains native controller state when normal playable art is rebuilt mid-level. */
public interface RuntimeArtRefreshParticipant {
    /** The initializer has installed the new normal renderer and animation resources. */
    void rebindAfterArtRefresh();
}
