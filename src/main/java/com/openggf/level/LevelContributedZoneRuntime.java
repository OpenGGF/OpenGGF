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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    void initialize(ModZoneRuntimeContribution contribution) throws IOException {
        var zoneRuntime = GameServices.zoneRuntimeRegistryOrNull();
        var animatedTiles = GameServices.animatedTileChannelGraphOrNull();
        if (zoneRuntime != null) zoneRuntime.clear();
        if (animatedTiles != null) animatedTiles.clear();
        var settings = contribution.levelData().settings();
        var builtIn = settings.background() == null && settings.waterHeight() == null ? null
                : levelManager.gameModule.getModLevelSettingsProvider().create(settings,
                        levelManager.worldDataSource().rom().orElse(null));
        var factory = contribution.runtimeFactory();
        var explicit = factory == null ? null : factory.create(new ModZoneRuntimeContext(
                new ZoneKey.Mod(contribution.ownerModId(), contribution.localKey()),
                levelManager.gameModule.getGameCode(), levelManager.currentZone,
                levelManager.currentAct, levelManager.level));
        services = combine(builtIn, explicit);
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

    /** Authored choices and explicit factories may contribute separate services, never competing owners. */
    static ModZoneRuntimeServices combine(ModZoneRuntimeServices settings, ModZoneRuntimeServices explicit) {
        if (explicit == null) return settings;
        if (settings == null) return explicit;
        var animations = new ArrayList<>(settings.animatedTiles());
        animations.addAll(explicit.animatedTiles());
        var effects = new ArrayList<>(settings.renderEffects());
        effects.addAll(explicit.renderEffects());
        var modes = new ArrayList<>(settings.renderModes());
        modes.addAll(explicit.renderModes());
        var adapters = new LinkedHashMap<>(settings.rewindAdapters());
        explicit.rewindAdapters().forEach((key, adapter) -> {
            if (adapters.putIfAbsent(key, adapter) != null)
                throw new IllegalArgumentException("Authored settings and explicit runtime both provide rewind adapter " + key);
        });
        return new ModZoneRuntimeServices(singleOwner(settings.features(), explicit.features(), "features"),
                singleOwner(settings.water(), explicit.water(), "water"),
                singleOwner(settings.scroll(), explicit.scroll(), "scroll"),
                singleOwner(settings.state(), explicit.state(), "state"), animations,
                singleOwner(settings.paletteAnimation(), explicit.paletteAnimation(), "palette animation"),
                effects, modes, adapters);
    }

    private static <T> T singleOwner(T settings, T explicit, String service) {
        if (settings != null && explicit != null)
            throw new IllegalArgumentException("Authored settings and explicit runtime both provide " + service);
        return settings != null ? settings : explicit;
    }
}
