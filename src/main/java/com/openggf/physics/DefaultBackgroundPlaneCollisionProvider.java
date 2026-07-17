package com.openggf.physics;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.Objects;
import java.util.function.Supplier;

/** Default adapter preserving the legacy collision flag and live scroll state. */
public final class DefaultBackgroundPlaneCollisionProvider implements BackgroundPlaneCollisionProvider {
    private final GameStateManager gameState;
    private final Camera camera;
    private final ParallaxManager parallax;
    private final ZoneRuntimeRegistry zoneRuntime;
    private final Supplier<LevelManager> levelManager;
    private State cachedState = State.INACTIVE;
    private int cachedCameraX = Integer.MIN_VALUE;
    private int cachedCameraY = Integer.MIN_VALUE;
    private int cachedBgX = Integer.MIN_VALUE;
    private int cachedBgY = Integer.MIN_VALUE;
    private boolean cachedActive;
    private State cachedExplicit;

    public DefaultBackgroundPlaneCollisionProvider(GameStateManager gameState,
                                                    Camera camera,
                                                    ParallaxManager parallax,
                                                    ZoneRuntimeRegistry zoneRuntime,
                                                    Supplier<LevelManager> levelManager) {
        this.gameState = Objects.requireNonNull(gameState, "gameState");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.parallax = Objects.requireNonNull(parallax, "parallax");
        this.zoneRuntime = Objects.requireNonNull(zoneRuntime, "zoneRuntime");
        this.levelManager = Objects.requireNonNull(levelManager, "levelManager");
    }

    @Override
    public State state() {
        return state(null);
    }

    @Override
    public State state(LevelManager probeLevel) {
        State explicit = zoneRuntime.current().backgroundPlaneCollisionStateOrNull();
        if (explicit != null) {
            if (explicit.equals(cachedExplicit)) return cachedState;
            cachedExplicit = explicit;
            cachedState = explicit;
            return cachedState;
        }
        cachedExplicit = null;
        if (!gameState.isBackgroundCollisionFlag()) {
            cachedActive = false;
            cachedState = State.INACTIVE;
            return State.INACTIVE;
        }

        int cameraX = camera.getX();
        int cameraY = camera.getY();
        int bgCameraX = cameraX;
        int bgCameraY = cameraY;
        boolean live = false;
        LevelManager level = probeLevel != null ? probeLevel : levelManager.get();
        if (level != null) {
            ZoneScrollHandler handler = parallax.getHandler(level.getFeatureZoneId());
            if (handler != null) {
                int handlerBgX = handler.getBgCameraX();
                short handlerBgY = handler.getVscrollFactorBG();
                if (handlerBgX != Integer.MIN_VALUE) {
                    bgCameraX = handlerBgX;
                }
                bgCameraY = handlerBgY;
                live = true;
            }
        }
        if (!live) {
            int cachedBgX = parallax.getBgCameraX();
            if (cachedBgX != Integer.MIN_VALUE) {
                bgCameraX = cachedBgX;
            }
            bgCameraY = parallax.getVscrollFactorBG();
        }
        if (cachedActive && cachedCameraX == cameraX && cachedCameraY == cameraY
                && cachedBgX == bgCameraX && cachedBgY == bgCameraY) {
            return cachedState;
        }
        cachedActive = true;
        cachedCameraX = cameraX;
        cachedCameraY = cameraY;
        cachedBgX = bgCameraX;
        cachedBgY = bgCameraY;
        cachedState = new State(true, cameraX - bgCameraX, cameraY - bgCameraY);
        return cachedState;
    }
}
