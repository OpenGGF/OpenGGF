package com.openggf.level.objects;

import java.util.Objects;

/** Engine-internal bridge for destination-scoped HUD profile installation. */
public final class HudProfileAccess {
    private HudProfileAccess() {
    }

    public static void install(HudRenderManager hud, HudProfile profile) {
        Objects.requireNonNull(hud, "hud").installProfile(profile);
    }

    public static void setScreenLeftAnchorEnabled(HudRenderManager hud, boolean enabled) {
        Objects.requireNonNull(hud, "hud").setScreenLeftAnchorEnabled(enabled);
    }

    public static void installWarningPolicy(HudRenderManager hud,
            com.openggf.game.internal.HudWarningPolicyProvider policy, java.util.function.IntSupplier clock) {
        Objects.requireNonNull(hud, "hud").setWarningPolicy(
                Objects.requireNonNull(policy, "policy"), Objects.requireNonNull(clock, "clock"));
    }

    public static void drawVBlankCounters(HudRenderManager hud, com.openggf.game.LevelState state,
            com.openggf.game.PlayableEntity player, boolean advanceTimer) {
        Objects.requireNonNull(hud, "hud").drawForCounterPublication(state, player, advanceTimer);
    }

    public static HudProfile current(HudRenderManager hud) {
        return Objects.requireNonNull(hud, "hud").currentProfile();
    }
}
