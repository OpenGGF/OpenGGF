package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.EndingProvider;
import com.openggf.game.ShieldType;
import com.openggf.game.SpecialStageProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Adversarial pending-visit controls: reset retires ownership without granting exit progress. */
class TestGameLoopSessionResetVisits {
    @Test void bonusDisposesDistinctLiveAndDeferredOwnersOnceWithoutCollectingRewards() throws Exception {
        var visit = new GameLoopBonusStageSession(mock(GameLoopBonusStageSession.Host.class), mock(AudioManager.class));
        var live = mock(BonusStageProvider.class);
        var deferred = mock(BonusStageProvider.class);
        set(visit, "activeBonusStageProvider", live);
        set(visit, "deferredBonusProvider", deferred);
        set(visit, "bonusStageTransitionPending", true);
        visit.setPendingShield(ShieldType.FIRE);

        visit.retireForSessionReset();
        visit.retireForSessionReset();

        assertNull(visit.provider());
        assertFalse(visit.isTransitionPending());
        assertFalse(visit.hasPendingShield());
        verify(live).onExit();
        verify(deferred).onExit();
        verifyNoMoreInteractions(live, deferred);
    }

    @Test void oneBonusProviderInBothSlotsIsNotDisposedTwice() throws Exception {
        var visit = new GameLoopBonusStageSession(mock(GameLoopBonusStageSession.Host.class), mock(AudioManager.class));
        var provider = mock(BonusStageProvider.class);
        set(visit, "activeBonusStageProvider", provider);
        set(visit, "deferredBonusProvider", provider);
        visit.retireForSessionReset();
        verify(provider).onExit();
        verifyNoMoreInteractions(provider);
    }

    @Test void specialVisitDropsCompletionAndReturnWithoutAnEmeraldOrResultCallback() throws Exception {
        var host = mock(GameLoopSpecialStageLifecycle.Host.class);
        var visit = new GameLoopSpecialStageLifecycle(host, mock(AudioManager.class));
        var provider = mock(SpecialStageProvider.class);
        set(visit, "activeSpecialStageProvider", provider);
        set(visit, "specialStageTransitionPending", true);
        set(visit, "returningFromSpecialStage", true);
        set(visit, "ssEmeraldCollected", true);
        visit.retireForSessionReset();
        visit.retireForSessionReset();
        assertFalse(visit.transitionPending());
        assertFalse(visit.returningFromSpecialStage());
        assertNull(visit.resultsScreen());
        verify(provider).reset();
        verifyNoMoreInteractions(provider);
        verifyNoInteractions(host);
    }

    @Test void endingRetirementDoesNotRunItsSaveOrCompletionPathAndNextVisitCanBeAdmitted() throws Exception {
        var host = mock(GameLoopEndingSession.Host.class);
        var visit = new GameLoopEndingSession(host, mock(AudioManager.class));
        var provider = mock(EndingProvider.class);
        set(visit, "endingProvider", provider);
        visit.setTransitionPending(true);
        assertTrue(visit.isTransitionPending());
        visit.retireForSessionReset();
        visit.retireForSessionReset();
        assertNull(visit.provider());
        assertFalse(visit.isTransitionPending());
        verifyNoInteractions(provider, host);
        visit.setTransitionPending(true);
        assertTrue(visit.isTransitionPending());
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
