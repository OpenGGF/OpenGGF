package com.openggf.game.ghost;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostRenderRegistry {
    @Test
    void fansOutToAllRegisteredRenderersInOrder() {
        GhostRenderRegistry registry = new GhostRenderRegistry();
        List<String> calls = new ArrayList<>();
        registry.register((bucket, high) -> calls.add("a:" + bucket + ":" + high));
        registry.register((bucket, high) -> calls.add("b:" + bucket + ":" + high));
        registry.renderForLayer(3, true);
        assertEquals(List.of("a:3:true", "b:3:true"), calls);
    }

    @Test
    void unregisterStopsCallsAndEmptyIsCheap() {
        GhostRenderRegistry registry = new GhostRenderRegistry();
        assertTrue(registry.isEmpty());
        List<String> calls = new ArrayList<>();
        GhostRenderRegistry.GhostLayerRenderer r = (bucket, high) -> calls.add("x");
        registry.register(r);
        assertFalse(registry.isEmpty());
        registry.unregister(r);
        registry.renderForLayer(0, false);
        assertTrue(calls.isEmpty());
        assertTrue(registry.isEmpty());
    }
}
