package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Sonic2SpecialStageRendererDeterminismTest {

    /**
     * The player invulnerability flash must be driven purely by the manager-owned
     * special-stage frame counter, not by wall-clock time. This is verified
     * behaviourally: feeding the renderer the same frame counter must always yield the
     * same flash state, and advancing the counter across the flash period must flip it
     * deterministically.
     */
    @Test
    public void playerFlashingUsesSpecialStageFrameCounterInsteadOfWallClock() throws Exception {
        // GraphicsManager is not touched by the flash predicate, so null is fine here.
        Sonic2SpecialStageRenderer renderer = new Sonic2SpecialStageRenderer(null);

        Sonic2SpecialStagePlayer player = mock(Sonic2SpecialStagePlayer.class);
        when(player.isInvulnerable()).thenReturn(true);

        Method flashHidden = Sonic2SpecialStageRenderer.class
                .getDeclaredMethod("isInvulnerabilityFlashHidden", Sonic2SpecialStagePlayer.class);
        flashHidden.setAccessible(true);

        // Same frame counter -> identical flash state, repeatedly (no wall-clock sampling).
        renderer.setFrameCounter(0);
        boolean firstReadAtZero = (boolean) flashHidden.invoke(renderer, player);
        renderer.setFrameCounter(0);
        boolean secondReadAtZero = (boolean) flashHidden.invoke(renderer, player);
        assertEquals(firstReadAtZero, secondReadAtZero,
                "Flash state must be a deterministic function of the frame counter, not wall-clock time");
        assertFalse(firstReadAtZero, "Flash should be visible (not hidden) at frame counter 0");

        // Advancing the counter into the next flash half-period flips the state.
        renderer.setFrameCounter(8); // (8 >> 3) & 1 == 1 -> hidden
        boolean readAtEight = (boolean) flashHidden.invoke(renderer, player);
        assertTrue(readAtEight, "Flash should be hidden at frame counter 8");

        // Returning to an earlier counter restores the earlier state (pure function of counter).
        renderer.setFrameCounter(0);
        boolean readBackAtZero = (boolean) flashHidden.invoke(renderer, player);
        assertEquals(firstReadAtZero, readBackAtZero,
                "Re-applying an earlier frame counter must reproduce the earlier flash state");
    }
}
