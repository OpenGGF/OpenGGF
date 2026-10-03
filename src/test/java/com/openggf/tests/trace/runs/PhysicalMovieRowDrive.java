package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.PlaybackDebugManager;

/** Publishes one represented movie row after a successful production iteration. */
final class PhysicalMovieRowDrive {
    private PhysicalMovieRowDrive() { }

    static void run(PlaybackDebugManager playback, Runnable production) {
        int movieRow = playback.getCursorFrame();
        playback.prepareCurrentFrame();
        production.run();
        // Gameplay advances itself; represented fade/title loops do not.
        // Exceptions must leave the physical row unpublished.
        if (playback.getCursorFrame() == movieRow) {
            playback.onLevelFrameAdvanced();
        }
    }
}
