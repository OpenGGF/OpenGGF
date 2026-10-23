package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;

/** The shell owns actual actors, two views, and two-camera streaming; the session owns native rules. */
public interface Sonic2CompetitionHost {
    /** True only for a connected, verified native session/view/finish/results path. */
    boolean supportsZone(int romZoneId);
    void loadAct(Sonic2CompetitionSession session, int romZoneId, int act);
    void step(Sonic2CompetitionSession session, InputHandler input);
    void draw(Sonic2CompetitionSession session);
    void freezeObjects(int ticks);
    /** False for illegal/dead actor routines. True requires the entire native atomic swap. */
    boolean swapNativeActorWorldState();
}
