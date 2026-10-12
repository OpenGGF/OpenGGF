package com.openggf.game.internal;

import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Internal game policy for camera registers initialized during a level load.
 * Retained player starts include native saved positions and an admitted module entry;
 * they bypass intro focus overrides without changing checkpoint or event state.
 */
public interface LevelStartCameraPosition {
    void initializeLevelStartCamera(Camera camera, AbstractPlayableSprite player,
                                    int zone, int act, boolean retainedPlayerStart);
}
