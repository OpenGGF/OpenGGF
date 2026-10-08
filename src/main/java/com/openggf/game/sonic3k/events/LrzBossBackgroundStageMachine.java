package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;

/** LRZ3_BackgroundEvent ($59C48): distant view, refill, locked lava arena and exit. */
public final class LrzBossBackgroundStageMachine {
    private LrzBossBackgroundStageMachine() { }

    /** The arena callback owns camera writes and its single AllocateObject attempt. */
    public static void advance(LrzZoneRuntimeState state, int cameraX, int cameraY,
                               int maximumY, Runnable enterArena) {
        int stage = state.backgroundRoutine();
        if (stage == 0) {
            if ((cameraY & 0xFFFF) < 0x500) return;
            state.setBackgroundRoutine(4);
            stage = 4; // loc_59C60 branches directly into loc_59C8C.
        }
        if (stage == 4) {
            if ((cameraY & 0xFFFF) < 0x500) {
                state.saveBackgroundCamera(state.backgroundCameraX(), state.backgroundCameraY());
                state.setDelayedRowcount(0xF);
                state.setBackgroundRoutine(8);
                drainRows(state); // loc_59CD8 -> loc_59D06, including the first two rows.
            } else if ((cameraX & 0xFFFF) == 0xA00 && (cameraY & 0xFFFF) == (maximumY & 0xFFFF)) {
                enterArena.run();
                // Even failed allocation advances to $C; this is not a retry gate.
                state.setBackgroundRoutine(12);
                finishTiltIfReady(state);
            }
        } else if (stage == 8) {
            drainRows(state);
        } else if (stage == 12) {
            finishTiltIfReady(state);
        }
    }

    private static void drainRows(LrzZoneRuntimeState state) {
        // Draw_PlaneVertTopDown calls its row helper twice, stopping on underflow.
        int count = (short) (state.delayedRowcount() - 1);
        if (count >= 0) count = (short) (count - 1);
        state.setDelayedRowcount(count);
        if (count < 0) {
            // loc_59D06 clears only Events_bg+$04; the saved Y word remains.
            state.saveBackgroundCamera(0, state.savedBackgroundCameraY());
            state.setBackgroundRoutine(0);
        }
    }

    private static void finishTiltIfReady(LrzZoneRuntimeState state) {
        if (state.bossAct().backgroundExitRequested() && state.bossAct().lavaAmplitude() == 0) {
            state.bossAct().clearBackgroundExitRequest();
            state.setBackgroundRoutine(16);
        }
    }
}
