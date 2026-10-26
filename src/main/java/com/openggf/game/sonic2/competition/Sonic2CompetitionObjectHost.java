package com.openggf.game.sonic2.competition;

/** World-policy port for native object requests; the world owner performs atomic swaps. */
public interface Sonic2CompetitionObjectHost {
    boolean teleportOnly();

    /**
     * An Obj2E whose contents were already selected requests the full native swap.
     * A later goal crossing does not cancel this request; actor routine >= 6 does.
     */
    void requestTeleport(Sonic2CompetitionSession session);
}
