package com.openggf.game.sonic2.objects;

import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** AddPoints2 (s2.asm:87835-87845): the native actor selects the score bank. */
final class Sonic2ObjectScore {
    private Sonic2ObjectScore() { }

    static void award(ObjectServices services, AbstractPlayableSprite player, int points) {
        var roster = NativeCompetitionRoster.from(services.worldSession());
        int slot = roster != null ? roster.slotOf(player) : -1;
        (slot >= 0 ? roster.gameState(slot) : services.gameState()).addScore(points);
    }
}
