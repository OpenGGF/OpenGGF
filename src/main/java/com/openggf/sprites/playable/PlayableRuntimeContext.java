package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.CharacterKey;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.EngineServices;
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
    final GameplayModeContext world;

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
        this.world = null;
    }

    private PlayableRuntimeContext(GameModule module, GameplayModeContext world) {
        this.module = Objects.requireNonNull(module);
        this.world = Objects.requireNonNull(world);
        this.camera = world.getCamera();
        this.level = world.getLevelManager();
        this.collision = world.getCollisionSystem();
        this.water = world.getWaterSystem();
        this.sprites = world.getSpriteManager();
        this.gameState = world.getGameStateManager();
        this.timers = world.getTimerManager();
        this.audio = EngineServices.current().audio();
        // Randomness belongs to the world and is already captured by its rewind registry.
        this.rng = world.getRng();
    }

    /**
     * Constructs one native donor playable over an existing owned world's services. Its
     * movement/animation rules remain donor-owned, while collision, events and clocks are
     * world-owned. This does not open or replace a gameplay session.
     */
    public static AbstractPlayableSprite constructShared(GameModule actorModule, GameplayModeContext world,
            String character, String code, int centreX, int centreY) {
        var key = CharacterKey.parsePersisted(character);
        var definition = actorModule.getPlayableCharacterRegistry().find(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown native character: " + character));
        var context = new PlayableRuntimeContext(actorModule, world);
        return context.construct(() -> CharacterConstructionScope.call(key,
                () -> CharacterConstructionScope.validateFactoryResult(key,
                        definition.spriteFactory().create(code, centreX, centreY))));
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

    /** Internal distinction from detached puppets; this does not authorize controller input. */
    public static boolean isSharedWorldBound(AbstractPlayableSprite sprite) {
        return isBound(sprite) && sprite.runtimeServices.world != null;
    }

    public static BackgroundPlaneCollisionProvider background(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? sprite.runtimeServices.world == null ? null
                : sprite.runtimeServices.world.getBackgroundPlaneCollisionProvider() : GameServices.backgroundPlaneCollisionOrNull();
    }

    public static ZoneRuntimeRegistry zoneRuntime(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? sprite.runtimeServices.world == null ? null
                : sprite.runtimeServices.world.getZoneRuntimeRegistry() : GameServices.zoneRuntimeRegistryOrNull();
    }

    public static TimerManager timersOrNull(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? sprite.runtimeServices.timers : GameServices.timersOrNull();
    }

    public static Camera cameraOrNull(AbstractPlayableSprite sprite) {
        return isBound(sprite) ? sprite.runtimeServices.camera : GameServices.cameraOrNull();
    }
}
