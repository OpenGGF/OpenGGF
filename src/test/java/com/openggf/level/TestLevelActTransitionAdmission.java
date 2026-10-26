package com.openggf.level;

import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.internal.WorldPlcPatternPublication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelActTransitionAdmission {
    @Test
    void nullRequestPreservesAnEnclosingAdmissionWithoutOpeningAnotherAttempt() throws Exception {
        LevelManager level = mock(LevelManager.class);
        var enclosing = new LevelManager.NativePlcLoadAdmission(
                mock(WorldPlcPatternPublication.class), new Object());
        level.nativePlcLoadAdmission = enclosing;

        new LevelActTransitionExecutor(level).execute(null);

        assertSame(enclosing, level.nativePlcLoadAdmission);
        verify(level, never()).beginNativePlcLoadAdmission();
        verify(level).finishNativePlcLoadAdmission(null, true);
        verifyNoMoreInteractions(level);
    }

    @Test
    void rejectedHandoffClosesOnlyItsAttemptBeforeRestoringEnclosingAdmission() {
        LevelManager level = mock(LevelManager.class);
        var enclosing = new LevelManager.NativePlcLoadAdmission(
                mock(WorldPlcPatternPublication.class), new Object());
        var selected = new LevelManager.NativePlcLoadAdmission(
                mock(WorldPlcPatternPublication.class), new Object());
        level.nativePlcLoadAdmission = enclosing;
        when(level.beginNativePlcLoadAdmission()).thenReturn(selected);
        doAnswer(call -> {
            assertSame(selected, level.nativePlcLoadAdmission,
                    "finish must see the current attempt until its original finally closes");
            return null;
        }).when(level).finishNativePlcLoadAdmission(selected, false);
        var request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.RESOURCE_HANDOFF_OWNER)
                .build();

        assertThrows(IllegalStateException.class,
                () -> new LevelActTransitionExecutor(level).execute(request));

        assertSame(enclosing, level.nativePlcLoadAdmission);
        var order = inOrder(level);
        order.verify(level).beginNativePlcLoadAdmission();
        order.verify(level).finishNativePlcLoadAdmission(selected, false);
        verifyNoMoreInteractions(level);
    }
}
