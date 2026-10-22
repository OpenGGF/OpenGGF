package com.openggf;

import com.openggf.ghost.GhostFrame;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.debug.DebugColor;
import com.openggf.sprites.ghost.ActiveGhost;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostRendererLayerSelection {
    @Test
    void matchesOnlyOwnRecordedLayer() {
        GhostFrame frame = new GhostFrame(0, 0, 0, false, false, false, 2, true);
        assertTrue(GhostRenderer.layerMatches(frame, 2, true));
        assertFalse(GhostRenderer.layerMatches(frame, 2, false));
        assertFalse(GhostRenderer.layerMatches(frame, 3, true));
    }
    @Test
    void nameplatesWaitForOverlayAndAreConsumedOnce() {
        PixelFontTextRenderer text = mock(PixelFontTextRenderer.class);
        when(text.measureWidth("[SPEED]+", 0.5f)).thenReturn(28);
        GhostRenderer renderer = new GhostRenderer(text);
        ActiveGhost ghost = new ActiveGhost("net:5", "sonic",
                new GhostFrame(200, 150, 0, false, false, false, 2, false), "[SPEED]+", 1f);
        renderer.queueNameplate(ghost, 100, 50);
        renderer.queueNameplate(ghost, 110, 60); // repeated preparation replaces, rather than duplicates
        verify(text, never()).beginBatch();
        verify(text, never()).drawShadowedText(anyString(), anyInt(), anyInt(), any(), anyFloat());

        renderer.drawNameplates(new float[16]);
        verify(text).drawShadowedText("[SPEED]+", 76, 66, DebugColor.WHITE, 0.5f);
        verify(text).endBatch();
        renderer.drawNameplates(new float[16]);
        verify(text, times(1)).beginBatch();
    }

    @Test
    void unloadingDiscardsLabelsAndUnnamedGhostsDoNotStartATextBatch() {
        PixelFontTextRenderer text = mock(PixelFontTextRenderer.class);
        GhostRenderer renderer = new GhostRenderer(text);
        GhostFrame frame = new GhostFrame(200, 150, 0, false, false, false, 2, false);
        renderer.queueNameplate(new ActiveGhost("net:1", "sonic", frame, "Old name", 1f), 0, 0);
        renderer.clearSlots();
        renderer.queueNameplate(new ActiveGhost("net:2", "sonic", frame, " ", 1f), 0, 0);
        renderer.queueNameplate(new ActiveGhost("net:3", "sonic", frame), 0, 0);
        renderer.drawNameplates(new float[16]);
        verifyNoInteractions(text);
    }
}
