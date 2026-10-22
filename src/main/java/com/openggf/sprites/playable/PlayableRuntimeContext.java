package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import java.util.Objects;
import java.util.function.Supplier;

/** Engine-internal services captured once by an isolated playable's constructor. */
public final class PlayableRuntimeContext {
    private static final ThreadLocal<PlayableRuntimeContext> CONSTRUCTION = new ThreadLocal<>();
    final GameModule module;
    final Camera camera;
    final LevelManager level;
    final CollisionSystem collision;
    final WaterSystem water;
    final SpriteManager sprites;
    final GameStateManager gameState;
    final TimerManager timers;
    final AudioManager audio;
    final GameRng rng;

    PlayableRuntimeContext(GameModule module, Camera camera, LevelManager level,
            CollisionSystem collision, WaterSystem water, SpriteManager sprites,
            GameStateManager gameState, TimerManager timers, AudioManager audio) {
        this.module = Objects.requireNonNull(module);
        this.camera = camera;
        this.level = level;
        this.collision = collision;
        this.water = water;
        this.sprites = sprites;
        this.gameState = gameState;
        this.timers = timers;
        this.audio = audio;
        this.rng = new GameRng(module.rngFlavour());
    }

    static PlayableRuntimeContext captureConstruction() { return CONSTRUCTION.get(); }

    <T> T construct(Supplier<T> factory) {
        if (CONSTRUCTION.get() != null) throw new IllegalStateException("Nested puppet construction");
        CONSTRUCTION.set(this);
        try { return factory.get(); }
        finally { CONSTRUCTION.remove(); }
    }

    public static boolean isBound(AbstractPlayableSprite sprite) {
        return sprite != null && sprite.runtimeServices != null;
    }

    public static BackgroundPlaneCollisionProvider background(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? null : GameServices.backgroundPlaneCollisionOrNull();
    }

    public static ZoneRuntimeRegistry zoneRuntime(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? null : GameServices.zoneRuntimeRegistryOrNull();
    }

    public static TimerManager timersOrNull(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? sprite.runtimeServices.timers : GameServices.timersOrNull();
    }

    public static Camera cameraOrNull(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? sprite.runtimeServices.camera : GameServices.cameraOrNull();
    }
}
