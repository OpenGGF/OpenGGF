package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;

final class LevelZoneScopedRegistryResetter {
    private LevelZoneScopedRegistryResetter() {
    }

    static void reset(LevelManager caller, WorldSession world) {
        var current = SessionManager.getCurrentGameplayMode();
        // A retained manager must never resolve the replacement mode's registries,
        // including when the same world has been reopened with fresh managers.
        if (current == null || !current.isGameplayRuntimeReady()
                || current.getWorldSession() != world || current.getLevelManager() != caller) {
            return;
        }
        PaletteOwnershipRegistry paletteOwnershipRegistry =
                GameServices.paletteOwnershipRegistryOrNull();
        SpecialRenderEffectRegistry specialRenderEffectRegistry =
                GameServices.specialRenderEffectRegistryOrNull();
        AdvancedRenderModeController advancedRenderModeController =
                GameServices.advancedRenderModeControllerOrNull();
        if (paletteOwnershipRegistry != null) {
            paletteOwnershipRegistry.clear();
        }
        if (specialRenderEffectRegistry != null) {
            specialRenderEffectRegistry.clear();
        }
        if (advancedRenderModeController != null) {
            advancedRenderModeController.clear();
        }
    }
}
