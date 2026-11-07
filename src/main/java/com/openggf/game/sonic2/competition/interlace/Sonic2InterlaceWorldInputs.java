package com.openggf.game.sonic2.competition.interlace;

import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs;

/** Compatibility name until the legacy presentation package retires after C1. */
public final class Sonic2InterlaceWorldInputs extends Sonic2CompetitionDisplayInputs {
    private Sonic2InterlaceWorldInputs() { }

    protected static void word(int value) { Sonic2CompetitionDisplayInputs.word(value); }
    protected static void octet(int value) { Sonic2CompetitionDisplayInputs.octet(value); }
}
