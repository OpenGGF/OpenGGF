package com.openggf.game.ghost;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gameplay-owned ghost render registry (main spec §6.1). LevelRenderer consults
 * this during the layered sprite pass; the trace-only TraceGhostHook global is
 * a separate, unchanged path. Renderers draw visuals only — never gameplay state.
 */
public final class GhostRenderRegistry {
    @FunctionalInterface
    public interface GhostLayerRenderer {
        void renderGhostsForLayer(int bucket, boolean highPriority);
    }

    private final List<GhostLayerRenderer> renderers = new CopyOnWriteArrayList<>();

    public void register(GhostLayerRenderer renderer) { renderers.add(renderer); }
    public void unregister(GhostLayerRenderer renderer) { renderers.remove(renderer); }
    public boolean isEmpty() { return renderers.isEmpty(); }

    public void renderForLayer(int bucket, boolean highPriority) {
        for (GhostLayerRenderer renderer : renderers) {
            renderer.renderGhostsForLayer(bucket, highPriority);
        }
    }
}
