package openggf.timeattack.mp;

import com.openggf.game.run.RunLevelStart;
import openggf.racing.hub.TrackValidationProfile;

/** Builds host validation metadata for the loaded act from the run's level-start report. */
public final class LiveLevelProfileFactory {
    private LiveLevelProfileFactory() {
    }

    public static TrackValidationProfile fromLevelStart(RunLevelStart start) {
        if (start == null || start.levelWidth() <= 0 || start.levelHeight() <= 0) {
            return null;
        }
        return new TrackValidationProfile(start.levelWidth(), start.levelHeight(),
                TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME,
                TrackValidationProfile.FRAME_RATE_CAP);
    }
}
