package com.openggf.mods.run;

import com.openggf.game.ModApi;

/** Commands a host may issue to its running run. They take effect at the next safe boundary. */
@ModApi
public interface RunHandle {
    /** Reloads the run's starting act from its beginning, clearing star-post progress. */
    void retry();

    /** Ends the run and returns to the host. */
    void leave();

    /**
     * Spectator camera after the act is complete: while {@code active}, the camera stops following
     * the player and pans by {@code dx}/{@code dy} pixels within the level bounds; clearing it
     * restores normal following. Ignored until the act's completion signal has been raised, so it
     * can never move the camera during play.
     */
    void spectate(boolean active, int dx, int dy);

    /** True until the run has ended. */
    boolean isActive();
}
