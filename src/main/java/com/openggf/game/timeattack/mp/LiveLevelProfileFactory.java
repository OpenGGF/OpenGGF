package com.openggf.game.timeattack.mp;

import com.openggf.game.GameServices;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.net.hub.TrackValidationProfile;

/** Builds host validation metadata from gameplay state on the game thread. */
public final class LiveLevelProfileFactory {
    private LiveLevelProfileFactory() {
    }

    public static TrackValidationProfile fromLoadedLevelOrNull() {
        LevelManager manager = GameServices.levelOrNull();
        if (manager == null) {
            return null;
        }
        Level level = manager.getCurrentLevel();
        if (level == null) {
            return null;
        }
        int blockSize = level.getBlockPixelSize();
        int width = Math.max(1, level.getLayerWidthBlocks(0)) * blockSize;
        int height = Math.max(1, level.getLayerHeightBlocks(0)) * blockSize;
        return new TrackValidationProfile(width, height,
                TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME,
                TrackValidationProfile.FRAME_RATE_CAP);
    }
}
