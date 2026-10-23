package com.openggf.level;

import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.game.internal.CheckpointScrollProvider;
import com.openggf.util.EngineCallerAccess;

/** Internal load/checkpoint dispatch to the provider owned by this parallax manager. */
public final class ParallaxCheckpointAccess {
    private ParallaxCheckpointAccess() { }

    public static void initialize(ParallaxManager parallax, int zoneId, int actId,
                                  int cameraX, int cameraY, CheckpointBackgroundScroll checkpoint) {
        requireEngineCaller();
        parallax.resetZoneState();
        parallax.initZone(zoneId, actId, cameraX, cameraY);
        if (checkpoint != null && parallax.checkpointScrollProvider() instanceof CheckpointScrollProvider provider) {
            provider.restoreCheckpointBackground(zoneId, checkpoint);
        }
    }

    public static CheckpointBackgroundScroll capture(ParallaxManager parallax, int zoneId) {
        requireEngineCaller();
        return parallax.checkpointScrollProvider() instanceof CheckpointScrollProvider provider
                ? provider.captureCheckpointBackground(zoneId) : null;
    }

    private static void requireEngineCaller() {
        if (EngineCallerAccess.callerOutside(ParallaxCheckpointAccess.class).getClassLoader()
                != ParallaxCheckpointAccess.class.getClassLoader()) {
            throw new SecurityException("Engine parallax checkpoint transport only");
        }
    }
}
