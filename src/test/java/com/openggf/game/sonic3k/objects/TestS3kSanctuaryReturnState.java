package com.openggf.game.sonic3k.objects;

import com.openggf.game.RespawnState;
import com.openggf.level.BigRingReturnState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestS3kSanctuaryReturnState {

    @Test
    void saved2RetainsOriginAndCheckpointWhileHpzUsesClearedSentinel() {
        BigRingReturnState state = new BigRingReturnState(
                0x1234, 0x567, 0x1194, 0x500, 42,
                (byte) 0x0C, (byte) 0x0D, 0x700, 6, 0,
                7, 1, 3, 5, 0x1000, 0x500, 0xF60, 0x480);

        assertEquals(7, state.originZone());
        assertEquals(1, state.originAct());
        assertEquals(3, state.checkpointIndex());
        assertEquals(5, state.starPostActivationMark());

        RespawnState checkpoint = mock(RespawnState.class);
        state.restoreCheckpointState(checkpoint);

        verify(checkpoint).restoreFromSaved(0x1000, 0x500, 0xF60, 0x480, 3);
        verify(checkpoint).restoreStarPostActivationMark(5);
    }
}
