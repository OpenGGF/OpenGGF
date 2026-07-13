package com.openggf;

/** Render-only hook invoked immediately before a nonempty sprite priority layer. */
@FunctionalInterface
@com.openggf.game.ModApi
public interface SpritePriorityLayerHook {
    void beforePriorityLayer(int bucket, boolean highPriority);
}
