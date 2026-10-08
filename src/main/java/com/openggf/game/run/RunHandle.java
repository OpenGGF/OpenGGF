package com.openggf.game.run;

import com.openggf.game.ModApi;

/** Commands a host may issue to its running run. They take effect at the next safe boundary. */
@ModApi
public interface RunHandle {
    /** Reloads the run's starting act from its beginning, clearing star-post progress. */
    void retry();

    /** Ends the run and returns to the host. */
    void leave();

    /** True until the run has ended. */
    boolean isActive();
}
