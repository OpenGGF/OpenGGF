package com.openggf.game.sonic3k.render;

import com.openggf.game.GameServices;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Temporary FBZ2 Plane A/B and VSRAM reversal used by the end-boss approach. */
public final class FbzBossPlaneRenderMode implements AdvancedRenderMode {
    private final Supplier<FbzZoneRuntimeState> stateSupplier;
    private final IntSupplier foregroundVScroll;
    private final IntSupplier backgroundVScroll;

    public FbzBossPlaneRenderMode() {
        this(() -> GameServices.hasRuntime()
                        ? S3kRuntimeStates.currentFbz(GameServices.zoneRuntimeRegistry()).orElse(null) : null,
                () -> GameServices.parallaxOrNull() != null
                        ? GameServices.parallaxOrNull().getVscrollFactorBG() : 0,
                () -> GameServices.parallaxOrNull() != null
                        ? GameServices.parallaxOrNull().getVscrollFactorFG() : 0);
    }

    public FbzBossPlaneRenderMode(Supplier<FbzZoneRuntimeState> stateSupplier,
                                  IntSupplier foregroundVScroll,
                                  IntSupplier backgroundVScroll) {
        this.stateSupplier = stateSupplier;
        this.foregroundVScroll = foregroundVScroll;
        this.backgroundVScroll = backgroundVScroll;
    }

    @Override public String id() { return "s3k-fbz-boss-plane-reversal"; }

    @Override
    public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
        FbzZoneRuntimeState state = stateSupplier.get();
        if (state == null || state.planeAssignmentMode() != Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED) return;
        builder.reversePlaneAssignment()
                .setForegroundVScrollOverride((short) foregroundVScroll.getAsInt())
                .setBackgroundVScrollOverride((short) backgroundVScroll.getAsInt());
    }

}
