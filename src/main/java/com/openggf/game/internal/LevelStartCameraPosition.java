package com.openggf.game.internal;

import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Internal game policy for the camera registers initialized during a level load. */
public interface LevelStartCameraPosition {
    /** Reconstructs load registers from an explicitly positioned, already spawned player. */
    default void initializePositionedLevelStartCamera(Camera camera, AbstractPlayableSprite player,
                                                     int zone, int act, boolean checkpoint) {
        initializeLevelStartCamera(camera, player, zone, act, checkpoint);
    }

    void initializeLevelStartCamera(Camera camera, AbstractPlayableSprite player,
                                    int zone, int act, boolean checkpoint);
}
