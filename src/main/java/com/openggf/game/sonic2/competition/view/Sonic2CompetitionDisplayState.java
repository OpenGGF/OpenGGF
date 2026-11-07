package com.openggf.game.sonic2.competition.view;

/** Read-only native teleport/pause words at display boundaries. The lifecycle owner supplies these. */
public interface Sonic2CompetitionDisplayState {
    Sonic2CompetitionDisplayState INACTIVE = new Sonic2CompetitionDisplayState() { };
    default boolean teleportFlag() { return false; }
    default int teleportTimer() { return 0; }
    default boolean gamePaused() { return false; }
}
