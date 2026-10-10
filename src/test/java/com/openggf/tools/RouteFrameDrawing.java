package com.openggf.tools;

import com.openggf.game.GameServices;

/** Test route drawing policy; simulation and queued render-thread work always run. */
final class RouteFrameDrawing {
    private final boolean everyFrame;
    private long drawnFrames;
    private long skippedFrames;

    RouteFrameDrawing() {
        this(Boolean.getBoolean("openggf.tests.drawEveryFrame"));
    }

    /** Keep an existing route-specific full-drawing switch compatible. */
    RouteFrameDrawing(String legacyProperty) {
        this(Boolean.getBoolean("openggf.tests.drawEveryFrame") || Boolean.getBoolean(legacyProperty));
    }

    RouteFrameDrawing(boolean everyFrame) {
        this.everyFrame = everyFrame;
    }

    void afterStep(GameplayCaptureSession session) {
        if (everyFrame) {
            draw(session);
        } else {
            // Production prepared loads can publish through this queue. Skipping
            // scene drawing must not withhold the work needed by later game ticks.
            GameServices.graphics().runPendingRenderThreadTasks();
            skippedFrames++;
        }
    }

    void checkpoint(GameplayCaptureSession session) {
        if (!everyFrame) {
            draw(session);
        }
    }

    /** Rewind branches and explicit presentation witnesses always draw every frame. */
    void draw(GameplayCaptureSession session) {
        session.renderFrame();
        drawnFrames++;
    }

    long drawnFrames() {
        return drawnFrames;
    }

    long skippedFrames() {
        return skippedFrames;
    }
}
