package com.openggf.level;

import com.openggf.game.LevelState;
import com.openggf.game.ShieldType;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelContinuationCarry {
    private final LevelTransitionCoordinator transitions = new LevelTransitionCoordinator();
    private final LevelManager manager = mock(LevelManager.class);
    private final LevelState level = mock(LevelState.class);

    private void request(int rings, long timer) {
        when(manager.getTransitions()).thenReturn(transitions);
        when(manager.getCurrentZone()).thenReturn(22);
        when(manager.getCurrentAct()).thenReturn(0);
        when(manager.getLevelGamestate()).thenReturn(level);
        LevelContinuationCarry.request(manager, 22, 0, rings, timer, ShieldType.FIRE);
    }

    @Test void freshLoadSeparatesShieldAndScreenEventCounterRestore() {
        request(37, 12345);
        assertFalse(LevelContinuationCarry.bypassInitialPresentation(transitions));
        LevelContinuationCarry.restoreCounters(manager);
        verifyNoInteractions(level);
        assertTrue(transitions.consumeZoneActRequest());
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        assertTrue(LevelContinuationCarry.bypassInitialPresentation(transitions));
        var player = mock(AbstractPlayableSprite.class);
        LevelContinuationCarry.restoreShield(transitions, player);
        verify(player).giveShield(ShieldType.FIRE);
        LevelContinuationCarry.finishLoad(transitions, true);
        assertFalse(LevelContinuationCarry.bypassInitialPresentation(transitions));
        LevelContinuationCarry.restoreCounters(manager);
        LevelContinuationCarry.restoreCounters(manager);
        verify(level).setRings(37);
        verify(level).setTimerFrames(12345);
        verify(level).resumeTimer();
        verifyNoMoreInteractions(level);
        assertNull(transitions.continuationCarry);
    }

    @Test void screenEventDuringLoadDoesNotReenableTitleCard() {
        request(0, 0);
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        LevelContinuationCarry.restoreCounters(manager);
        assertTrue(LevelContinuationCarry.bypassInitialPresentation(transitions));
        verifyNoInteractions(level);
        LevelContinuationCarry.finishLoad(transitions, true);
        assertNull(transitions.continuationCarry);
    }

    @Test void failedAndMismatchedLoadsDiscardTheBank() {
        request(1, 2);
        LevelContinuationCarry.beginLoad(transitions, 23, 0);
        assertNull(transitions.continuationCarry);
        request(1, 2);
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        LevelContinuationCarry.finishLoad(transitions, false);
        assertNull(transitions.continuationCarry);
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        assertFalse(LevelContinuationCarry.bypassInitialPresentation(transitions));
    }

    @Test void ordinaryReplacementAndResetDiscardTheBank() {
        request(1, 2);
        transitions.requestZoneAndAct(22, 0);
        assertNull(transitions.continuationCarry);
        request(1, 2);
        transitions.resetState();
        assertNull(transitions.continuationCarry);
    }

    @Test void rewindRestoresRequestAndConsumedDestinationBank() {
        request(13, 42);
        var adapter = new LevelTransitionRewindAdapter(transitions);
        var beforeFade = adapter.capture();
        transitions.consumeZoneActRequest();
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        LevelContinuationCarry.finishLoad(transitions, true);
        var beforeEvent = adapter.capture();
        LevelContinuationCarry.restoreCounters(manager);
        adapter.restore(beforeEvent);
        LevelContinuationCarry.restoreCounters(manager);
        verify(level, times(2)).setRings(13);
        adapter.restore(beforeFade);
        assertTrue(transitions.consumeZoneActRequest());
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        assertTrue(LevelContinuationCarry.bypassInitialPresentation(transitions));
    }

    @Test void unconsumedBankCannotLeakIntoASecondDirectLoad() {
        request(1, 2);
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        LevelContinuationCarry.finishLoad(transitions, true);
        LevelContinuationCarry.beginLoad(transitions, 22, 0);
        assertNull(transitions.continuationCarry);
    }
    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void finalDezProductionScreenEventConsumesItsLoadedCounterBankOnce() {
        com.openggf.game.session.SessionManager.clear();
        com.openggf.tests.TestEnvironment.activeGameplayMode();
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        var live = com.openggf.game.GameServices.level();
        // Begin at the destination-load boundary; the other cases above exercise how
        // request/load produces this bank. Here the real screen event must consume it.
        live.getTransitions().continuationCarry = new LevelContinuationCarry.State(
                23, 0, 37, 12345, null, LevelContinuationCarry.Phase.LOADED, true);
        fixture.stepIdleFrames(1);
        assertNull(live.getTransitions().continuationCarry);
        assertEquals(37, live.getLevelGamestate().getRings());
        // The ordinary frame advances the restored running timer once after ScreenEvents.
        assertEquals(12346, live.getLevelGamestate().getTimerFrames());
        live.getLevelGamestate().setRings(12);
        fixture.stepIdleFrames(1);
        assertEquals(12, live.getLevelGamestate().getRings());
    }

}
