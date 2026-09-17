package com.openggf.game.sonic3k.render;

import com.openggf.game.GameServices;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;

import java.util.function.Supplier;

/**
 * The Doomsday Zone foreground plane is scrolled independently of the camera: {@code ApplyDeformation2}
 * writes {@code -_unkEE98} as every line's Plane A word and {@code V_scroll_value} is {@code _unkEE9C}
 * (sonic3k.asm:118964-118976). This mode makes the renderer read the per-line foreground words and
 * the absolute foreground V-scroll, so the end boss body drawn in the foreground layout appears at
 * the window {@code SwScrlDdz} computes. Sprites keep the gameplay camera.
 */
public final class DdzForegroundPlaneRenderMode implements AdvancedRenderMode {
    private final Supplier<DdzZoneRuntimeState> stateSupplier;

    public DdzForegroundPlaneRenderMode() {
        this(() -> GameServices.hasRuntime()
                ? S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).orElse(null) : null);
    }

    public DdzForegroundPlaneRenderMode(Supplier<DdzZoneRuntimeState> stateSupplier) {
        this.stateSupplier = stateSupplier;
    }

    @Override
    public String id() {
        return "s3k-ddz-foreground-plane";
    }

    @Override
    public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
        DdzZoneRuntimeState state = stateSupplier.get();
        if (state == null) {
            return;
        }
        builder.enablePerLineForegroundScroll()
                .setForegroundVScrollOverride((short) state.foregroundY());
    }
}
