package com.openggf.sprites.ghost;

import com.openggf.game.timeattack.GhostRenderer;
import com.openggf.ghost.GhostFrame;
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
}
