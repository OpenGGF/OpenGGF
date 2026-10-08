package com.openggf.game.sonic3k.render;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;

/**
 * SSZ2's floating island is high-priority Plane B over low-priority Plane A sky.
 * The native VDP does that ordering automatically. Our background-first pipeline
 * needs a high-tile replay and matching low-sprite mask, just as HCZ/SOZ do; drawing
 * only the ordinary background pass hides the island behind the opaque sky tiles.
 * This preserves ROM priority bits rather than changing the foreground's pixels.
 */
public final class SszAct2BackgroundPriorityEffect implements SpecialRenderEffect {
    private final boolean mask;

    public SszAct2BackgroundPriorityEffect(boolean mask) {
        this.mask = mask;
    }

    @Override
    public SpecialRenderEffectStage stage() {
        return mask ? SpecialRenderEffectStage.SPRITE_PRIORITY_MASK
                : SpecialRenderEffectStage.AFTER_FOREGROUND;
    }

    @Override
    public void render(SpecialRenderEffectContext context) {
        HczBgHighPriorityTileRenderer.render(context, false, mask);
    }
}
