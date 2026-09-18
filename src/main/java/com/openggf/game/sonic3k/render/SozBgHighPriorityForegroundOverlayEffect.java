package com.openggf.game.sonic3k.render;

import com.openggf.game.GameServices;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;

/** Native SOZ Plane-B priority covers Plane-A low tiles and low-priority sprites. */
public final class SozBgHighPriorityForegroundOverlayEffect implements SpecialRenderEffect {
    private final boolean priorityMask;

    public SozBgHighPriorityForegroundOverlayEffect() { this(false); }

    private SozBgHighPriorityForegroundOverlayEffect(boolean priorityMask) {
        this.priorityMask = priorityMask;
    }

    public static SpecialRenderEffect spritePriorityMask() {
        return new SozBgHighPriorityForegroundOverlayEffect(true);
    }

    @Override public SpecialRenderEffectStage stage() {
        return priorityMask ? SpecialRenderEffectStage.SPRITE_PRIORITY_MASK
                : SpecialRenderEffectStage.AFTER_FOREGROUND;
    }
    @Override public void render(SpecialRenderEffectContext context) {
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElse(null);
        if (state != null && state.backgroundPlaneWindowActive()) {
            // SOZ2's native row mask also applies to the high-priority replay.
            // The main BG pass already wraps its decoded 2048px tilemap.
            HczBgHighPriorityTileRenderer.render(context, state.backgroundLayoutYMask() != 0xFFFF, priorityMask);
        }
    }
}
