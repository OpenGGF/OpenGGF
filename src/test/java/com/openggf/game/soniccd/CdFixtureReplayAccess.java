package com.openggf.game.soniccd;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The frontier replay's comparison rules ({@link CdFixtureReplay}) and test media for tests outside this package
 * (the player-launch test drives the engine's own frame loop and compares its rows the same way).
 */
public final class CdFixtureReplayAccess {
    private CdFixtureReplayAccess() { }

    /** The native row holding row {@code row}'s completed level frame ({@link CdFixtureReplay#completedRow}). */
    public static int completedRow(TraceData trace, int row) { return CdFixtureReplay.completedRow(trace, row); }

    /** The frontier's player and camera comparison of one row ({@link CdFixtureReplay#compare}). */
    public static FrameComparison compare(TraceFrame expected, AbstractPlayableSprite sonic) {
        return CdFixtureReplay.compare(CdFixtureReplay.binder(), expected, sonic);
    }

    /** The USA disc's sheet; skips the test when {@code soniccd.media.root} is not set. */
    public static Path usaCue() throws IOException { return CdTestMedia.usaCue(); }

    /** A native fixture file; skips the test when it is absent. */
    public static Path nativeFile(String relative) { return CdTestMedia.nativeFile(relative); }
}
