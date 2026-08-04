package com.openggf.game;

import com.openggf.graphics.FadeManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TestSpecialStageEntryPresentationController {

    /**
     * ROM: {@code GM_Special} runs {@code PaletteWhiteOut} before anything else
     * (sonic.asm:3227), so a ready stage still white-outs first and reveals only
     * once that fade has reached its opaque hold.
     */
    @Test
    void readyWhiteEntryWhitesOutBeforeRevealing() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        FadeManager fade = mock(FadeManager.class);
        Runnable music = mock(Runnable.class);
        when(provider.isEntryPresentationReady()).thenReturn(true);
        SpecialStageEntryPresentationController controller =
                new SpecialStageEntryPresentationController();

        controller.begin(provider, false, fade, music);

        verify(fade).startFadeToWhite(any(), anyInt());
        verify(music, never()).run();
        assertTrue(controller.isPending());

        // Still fading out: nothing is revealed yet.
        when(fade.getState()).thenReturn(FadeManager.FadeState.FADING_TO_WHITE);
        controller.update(provider, fade, music);
        verify(music, never()).run();

        when(fade.getState()).thenReturn(FadeManager.FadeState.HOLD_WHITE);
        controller.update(provider, fade, music);

        InOrder order = inOrder(music, fade);
        order.verify(music).run();
        order.verify(fade).startFadeFromWhite(any());
        assertFalse(controller.isPending());
    }

    /** A screen a native owner already drove fully white is kept, not re-faded. */
    @Test
    void alreadyWhiteScreenIsNotFadedAgain() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        FadeManager fade = mock(FadeManager.class);
        Runnable music = mock(Runnable.class);
        when(provider.isEntryPresentationReady()).thenReturn(true);
        when(fade.getState()).thenReturn(FadeManager.FadeState.HOLD_WHITE);
        SpecialStageEntryPresentationController controller =
                new SpecialStageEntryPresentationController();

        controller.begin(provider, false, fade, music);

        verify(fade, never()).startFadeToWhite(any(), anyInt());
        InOrder order = inOrder(music, fade);
        order.verify(music).run();
        order.verify(fade).startFadeFromWhite(any());
        assertFalse(controller.isPending());
    }

    /** An already-white screen that is not ready yet stays pinned white. */
    @Test
    void alreadyWhiteScreenIsPinnedWhileTheStageIsNotReady() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        FadeManager fade = mock(FadeManager.class);
        Runnable music = mock(Runnable.class);
        when(provider.isEntryPresentationReady()).thenReturn(false);
        when(fade.getState()).thenReturn(FadeManager.FadeState.HOLD_WHITE);
        SpecialStageEntryPresentationController controller =
                new SpecialStageEntryPresentationController();

        controller.begin(provider, false, fade, music);

        verify(fade, never()).startFadeToWhite(any(), anyInt());
        verify(fade).holdWhite();
        verify(music, never()).run();
        assertTrue(controller.isPending());
    }

    @Test
    void deferredBlackEntryHoldsThenStartsExactlyOnceAtReadiness() {
        AtomicBoolean ready = new AtomicBoolean(false);
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        FadeManager fade = mock(FadeManager.class);
        Runnable music = mock(Runnable.class);
        when(provider.isEntryPresentationReady()).thenAnswer(ignored -> ready.get());
        SpecialStageEntryPresentationController controller =
                new SpecialStageEntryPresentationController();

        controller.begin(provider, true, fade, music);

        verify(fade).holdBlack();
        verify(music, never()).run();
        assertTrue(controller.isPending());

        ready.set(true);
        controller.update(provider, fade, music);
        controller.update(provider, fade, music);

        InOrder order = inOrder(music, fade);
        order.verify(music).run();
        order.verify(fade).startFadeFromBlack(any());
        verifyNoMoreInteractions(music);
        assertFalse(controller.isPending());
    }

    @Test
    void deferredWhiteEntryWhitesOutAndWaitsForReadiness() {
        AtomicBoolean ready = new AtomicBoolean(false);
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        FadeManager fade = mock(FadeManager.class);
        Runnable music = mock(Runnable.class);
        when(provider.isEntryPresentationReady()).thenAnswer(ignored -> ready.get());
        SpecialStageEntryPresentationController controller =
                new SpecialStageEntryPresentationController();

        controller.begin(provider, false, fade, music);

        verify(fade).startFadeToWhite(any(), anyInt());
        verify(fade, never()).holdBlack();
        verify(music, never()).run();
        assertTrue(controller.isPending());

        // The white-out finishes before the stage is ready: stay opaque.
        when(fade.getState()).thenReturn(FadeManager.FadeState.HOLD_WHITE);
        controller.update(provider, fade, music);
        verify(music, never()).run();
        assertTrue(controller.isPending());

        ready.set(true);
        controller.update(provider, fade, music);
        controller.update(provider, fade, music);

        verify(music).run();
        verify(fade).startFadeFromWhite(any());
        assertFalse(controller.isPending());
    }

    @Test
    void clearPreventsAbandonedEntryFromStartingLater() {
        AtomicBoolean ready = new AtomicBoolean(false);
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        FadeManager fade = mock(FadeManager.class);
        Runnable music = mock(Runnable.class);
        when(provider.isEntryPresentationReady()).thenAnswer(ignored -> ready.get());
        SpecialStageEntryPresentationController controller =
                new SpecialStageEntryPresentationController();
        controller.begin(provider, true, fade, music);

        controller.clear();
        ready.set(true);
        controller.update(provider, fade, music);

        verify(music, never()).run();
        verify(fade, never()).startFadeFromBlack(any());
        assertFalse(controller.isPending());
    }
}
