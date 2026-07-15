package com.openggf.level.objects;

/** Engine-only access for selecting the HUD palette upload path. */
public final class HudPaletteBridgeAccess {
    private HudPaletteBridgeAccess() {
    }

    public static void routeLivesPaletteOverrideThroughOwnership(HudRenderManager hud,
                                                                 boolean routed) {
        hud.setRouteLivesPaletteOverrideThroughOwnership(routed);
    }
}
