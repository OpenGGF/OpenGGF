package com.openggf.tools;

import com.openggf.game.GameServices;

/** Drawing policy for the SOZ route checks; simulation and queued render work always run. */
final class SozColdRouteFrameDrawing {
    private final boolean everyFrame;
    private long drawnFrames;
    private long skippedFrames;

    SozColdRouteFrameDrawing() {
        this(Boolean.getBoolean("openggf.soz.drawEveryFrame"));
    }

    SozColdRouteFrameDrawing(boolean everyFrame) {
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
