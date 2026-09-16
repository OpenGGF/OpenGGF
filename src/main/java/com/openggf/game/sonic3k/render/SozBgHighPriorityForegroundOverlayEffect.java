package com.openggf.game.sonic3k.render;

import com.openggf.game.GameServices;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;

/** Native SOZ temple Plane-B priority covers the desert's Plane-A low tiles. */
public final class SozBgHighPriorityForegroundOverlayEffect implements SpecialRenderEffect {
    @Override public SpecialRenderEffectStage stage() { return SpecialRenderEffectStage.AFTER_FOREGROUND; }
    @Override public void render(SpecialRenderEffectContext context) {
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElse(null);
        if (state != null && state.backgroundPlaneWindowActive()) {
            // SOZ2's native row mask also applies to the high-priority replay.
            // The main BG pass already wraps its decoded 2048px tilemap.
            HczBgHighPriorityTileRenderer.render(context, state.backgroundLayoutYMask() != 0xFFFF);
        }
    }
}
