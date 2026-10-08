package com.openggf.game.sonic3k.render;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;

/**
 * LRZ3's sloping pool uses high-priority Plane B tiles over Plane A's lava wall.
 * The VDP orders those tiles automatically. The engine draws its background
 * first, so replay the ROM high tiles above foreground-low and into the shared
 * low-sprite mask. The shared renderer applies sub_59DBC's column scroll too;
 * changing tile priority or drawing a new lava sprite would lose ROM ordering.
 */
public final class LrzBossBackgroundPriorityEffect implements SpecialRenderEffect {
    private final boolean mask;

    public LrzBossBackgroundPriorityEffect(boolean mask) {
        this.mask = mask;
    }

    @Override public SpecialRenderEffectStage stage() {
        return mask ? SpecialRenderEffectStage.SPRITE_PRIORITY_MASK
                : SpecialRenderEffectStage.AFTER_FOREGROUND;
    }

    @Override public void render(SpecialRenderEffectContext context) {
        HczBgHighPriorityTileRenderer.render(context, false, mask);
    }
}
