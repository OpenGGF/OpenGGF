package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.PlaybackDebugManager;

import java.util.function.BooleanSupplier;

/** Drives represented interior and return-presentation rows on the shared movie clock. */
final class ComparedInteriorRowDrive implements Runnable {
    private final PlaybackDebugManager playback;
    private final Runnable production;
    private final BooleanSupplier sourceExhausted;
    private final Runnable closeSource;
    private boolean sourceClosed;

    ComparedInteriorRowDrive(
            PlaybackDebugManager playback, Runnable production,
            BooleanSupplier sourceExhausted, Runnable closeSource) {
        this.playback = playback;
        this.production = production;
        this.sourceExhausted = sourceExhausted;
        this.closeSource = closeSource;
    }

    @Override
    public void run() {
        int movieRow = playback.getCursorFrame();
        playback.prepareCurrentFrame();
        production.run();
        // Bonus gameplay advances itself; fade/title owners need the physical
        // row driver to advance after their production iteration instead.
        if (playback.getCursorFrame() == movieRow) {
            playback.onLevelFrameAdvanced();
        }
        // The existing descriptor identifies the final source LevelLoop row
        // (S3K LevelLoop sets the reload bit before its fade/title tail). Close
        // its production ownership before the destination title loads, while
        // preserving the attached comparator/timing schedule for that tail.
        if (!sourceClosed && sourceExhausted.getAsBoolean()) {
            closeSource.run();
            sourceClosed = true;
        }
    }

    boolean sourceClosed() {
        return sourceClosed;
    }
}
