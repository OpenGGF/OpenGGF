package com.openggf.game.sonic3k.objects;

import com.openggf.game.RespawnState;
import com.openggf.game.LevelGamestate;
import com.openggf.game.ShieldType;
import com.openggf.camera.Camera;
import com.openggf.level.BigRingReturnState;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3kSanctuaryReturnState {

    @Test
    void saved2RetainsOriginAndCheckpointWhileHpzUsesClearedSentinel() {
        BigRingReturnState state = new BigRingReturnState(
                0x1234, 0x567, 0x1194, 0x500, 42,
                (byte) 0x0C, (byte) 0x0D, 0x700, 6, 0,
                8_765L, 0x06, 1 << 6, 0x0700, true,
                7, 1, 3, 5, 0x1000, 0x500, 0xF60, 0x480);

        assertEquals(7, state.originZone());
        assertEquals(1, state.originAct());
        assertEquals(3, state.checkpointIndex());
        assertEquals(5, state.starPostActivationMark());
        assertEquals(8_765L, state.timerFrames());
        assertEquals(0x06, state.extraLifeFlags());
        assertEquals(0x0700, state.apparentZoneAndAct());

        RespawnState checkpoint = mock(RespawnState.class);
        state.restoreCheckpointState(checkpoint);

        verify(checkpoint).restoreFromSaved(0x1000, 0x500, 0xF60, 0x480, 3);
        verify(checkpoint).restoreStarPostActivationMark(5);
    }

    @Test
    void saved2RestoresTimerThresholdShieldAndWaterPresentation() {
        BigRingReturnState state = new BigRingReturnState(
                0x1234, 0x567, 0x1194, 0x500, 42,
                (byte) 0x0C, (byte) 0x0D, 0x700, 6, 0x620,
                8_765L, 0x06, 1 << 6, 0x0700, true,
                7, 1, -1, -1, 0, 0, 0, 0);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        Camera camera = mock(Camera.class);
        WaterSystem water = mock(WaterSystem.class);
        LevelGamestate levelState = new LevelGamestate();
        when(water.hasWater(7, 1)).thenReturn(true);

        state.restoreToPlayer(player, camera, levelState, water, 7, 1);

        assertEquals(42, levelState.getRings());
        assertEquals(8_765L, levelState.getTimerFrames());
        assertEquals(0x06, levelState.getRingExtraLifeFlags());
        verify(player).removeShield();
        verify(player).giveShield(ShieldType.BUBBLE);
        verify(water).setFullScreenFlag(7, 1, true);
    }
}
