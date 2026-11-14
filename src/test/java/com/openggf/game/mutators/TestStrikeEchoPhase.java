package com.openggf.game.mutators;

import com.openggf.game.StrikeTrailStyle;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ProfileSuperStateController;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Spawn-phase echo tints: an authored form's running cycle row wins over the displayed palette. */
class TestStrikeEchoPhase {
    private static final List<StrikeTrailStyle.FrontLayer> FRONT = List.of(new StrikeTrailStyle.FrontLayer(0xFFFFFF, 14, 3, 255, 4));

    @Test
    void echoPhaseSamplesTheRunningCycleBeneathAStrikePalette() {
        var player = mock(AbstractPlayableSprite.class);
        var form = mock(ProfileSuperStateController.class);
        when(player.getSuperStateController()).thenReturn(form);
        when(form.cyclePhaseColour(4)).thenReturn(0xFFDB00);
        assertEquals(0xFFDB00, HomingAttackEffects.phaseTint(player,
                new StrikeTrailStyle(List.of(1, 2, 3), FRONT, -1, 100, 4)));
    }

    @Test
    void noPhaseSlotMeansNoPhaseTint() {
        var player = mock(AbstractPlayableSprite.class);
        assertEquals(-1, HomingAttackEffects.phaseTint(player, new StrikeTrailStyle(List.of(1, 2, 3), FRONT, 0x123456, 100)));
        assertEquals(-1, HomingAttackEffects.phaseTint(null, new StrikeTrailStyle(List.of(1, 2, 3), FRONT, -1, 100, 4)));
    }
}
