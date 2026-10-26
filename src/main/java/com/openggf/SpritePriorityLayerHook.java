package com.openggf;

/**
 * Render-only hook for each prepared bucket's low and high sprite layers, including layers
 * without local players. Painter traversal invokes it before that layer's first local player;
 * front-to-back sprite-table traversal invokes it after that layer's last local player.
 */
@FunctionalInterface
@com.openggf.game.ModApi
public interface SpritePriorityLayerHook {
    void beforePriorityLayer(int bucket, boolean highPriority);
}
