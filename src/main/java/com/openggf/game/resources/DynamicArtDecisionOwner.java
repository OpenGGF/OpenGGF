package com.openggf.game.resources;

import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.Objects;

/**
 * Explicit production capability for one authoritative playable-art owner.
 *
 * <p>Render-only sprites, including trace ghosts, are never given this
 * capability.
 */
public final class DynamicArtDecisionOwner {
    private final DynamicArtLifecycleService lifecycle;
    private final String owner;
    private final PlayerSpriteRenderer renderer;

    public DynamicArtDecisionOwner(
            DynamicArtLifecycleService lifecycle,
            String owner,
            PlayerSpriteRenderer renderer) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public void observe(int mappingFrame) {
        if (!lifecycle.isRunActive()) {
            return;
        }
        DynamicArtLifecycleService.ArtUpdate update =
                lifecycle.observePlayerDplc(
                        owner, mappingFrame,
                        renderer.dplcFrame(mappingFrame),
                        DynamicArtLifecycleService.DecisionKind.NORMAL_OBJECT);
        renderer.applyRuntimeArtUpdate(mappingFrame, update);
    }

    public void prime(int mappingFrame) {
        if (!lifecycle.isRunActive()) {
            return;
        }
        DynamicArtLifecycleService.ArtUpdate update =
                lifecycle.primePlayerDplc(
                        owner, mappingFrame,
                        renderer.dplcFrame(mappingFrame));
        renderer.applyRuntimeArtUpdate(mappingFrame, update);
    }
}
