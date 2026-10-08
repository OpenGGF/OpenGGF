package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.ZoneKey;
import com.openggf.game.animation.ChannelContext;
import com.openggf.game.modzone.ModZoneRuntimeContext;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.game.modzone.ModZoneRuntimeServices;
import com.openggf.game.session.ModZoneRuntimeInstaller;
import com.openggf.game.session.WorldSession;

import java.util.List;

/** Owns installation, refresh and removal of one act's contributed runtime services. */
final class LevelContributedZoneRuntime {
    private final LevelManager levelManager;
    private final WorldSession worldSession;
    private ModZoneRuntimeServices services;

    LevelContributedZoneRuntime(LevelManager levelManager, WorldSession worldSession) {
        this.levelManager = levelManager;
        this.worldSession = worldSession;
    }

    void initialize(ModZoneRuntimeContribution contribution) {
        var zoneRuntime = GameServices.zoneRuntimeRegistryOrNull();
        var animatedTiles = GameServices.animatedTileChannelGraphOrNull();
        if (zoneRuntime != null) zoneRuntime.clear();
        if (animatedTiles != null) animatedTiles.clear();
        var factory = contribution.runtimeFactory();
        services = factory == null ? null : factory.create(new ModZoneRuntimeContext(
                new ZoneKey.Mod(contribution.ownerModId(), contribution.localKey()),
                levelManager.gameModule.getGameCode(), levelManager.currentZone,
                levelManager.currentAct, levelManager.level));
        levelManager.zoneFeatureProvider = services == null ? null : services.features();
        if (levelManager.parallaxManager != null) {
            levelManager.parallaxManager.installContributedHandler(levelManager.currentZone,
                    services == null ? null : services.scroll());
        }
        if (services != null) {
            var runtime = services;
            if (zoneRuntime != null && runtime.state() != null) zoneRuntime.install(runtime.state());
            if (animatedTiles != null) animatedTiles.install(runtime.animatedTiles());
            levelManager.animatedPaletteManager = runtime.paletteAnimation();
            var effects = GameServices.specialRenderEffectRegistryOrNull();
            if (effects != null) runtime.renderEffects().forEach(effects::register);
            var modes = GameServices.advancedRenderModeControllerOrNull();
            if (modes != null) runtime.renderModes().forEach(modes::register);
            ModZoneRuntimeInstaller.install(worldSession, List.copyOf(runtime.rewindAdapters().values()));
        }
    }

    void clear() {
        services = null;
        if (levelManager.parallaxManager != null) {
            levelManager.parallaxManager.installContributedHandler(-1, null);
        }
        ModZoneRuntimeInstaller.install(worldSession, List.of());
    }

    void updateAnimatedTiles() {
        if (services == null) return;
        var graph = GameServices.animatedTileChannelGraphOrNull();
        if (graph != null) graph.update(new ChannelContext(graph, null, levelManager.level,
                services.state(), levelManager.currentZone, levelManager.currentAct,
                levelManager.frameCounter));
    }

    WaterDataProvider waterProviderOrNull() {
        return services == null ? null : services.water();
    }
}
