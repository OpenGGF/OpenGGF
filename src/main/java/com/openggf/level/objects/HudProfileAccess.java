package com.openggf.level.objects;

import java.util.Objects;

/** Engine-internal bridge for destination-scoped HUD profile installation. */
public final class HudProfileAccess {
    private HudProfileAccess() {
    }

    public static void install(HudRenderManager hud, HudProfile profile) {
        Objects.requireNonNull(hud, "hud").installProfile(profile);
    }

    public static HudProfile current(HudRenderManager hud) {
        return Objects.requireNonNull(hud, "hud").currentProfile();
    }
}
