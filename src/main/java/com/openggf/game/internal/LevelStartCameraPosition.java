package com.openggf.game.internal;

import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Internal game policy for the camera registers initialized during a level load. */
public interface LevelStartCameraPosition {
    void initializeLevelStartCamera(Camera camera, AbstractPlayableSprite player,
                                    int zone, int act, boolean checkpoint);
}
