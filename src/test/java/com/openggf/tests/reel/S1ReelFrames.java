package com.openggf.tests.reel;

import com.openggf.tests.HeadlessTestFixture;
import com.openggf.debug.playback.Bk2FrameInput;

/** Exact existing S1 fixture ticks; drawing stays opt-in and never advances simulation. */
public final class S1ReelFrames {
    private S1ReelFrames() {}

    public static void step(HeadlessTestFixture fixture, boolean up, boolean down,
            boolean left, boolean right, boolean jump) {
        fixture.stepFrame(up, down, left, right, jump);
        TestReelCapture.afterHeadlessFrame(fixture, up, down, left, right, jump);
    }

    public static void afterPauseStep(HeadlessTestFixture fixture, boolean startEdge, boolean paused) {
        afterPauseStep(fixture, fixture.frameCount(), startEdge, paused);
    }

    public static void afterPauseStep(HeadlessTestFixture fixture, int inputIndex, boolean startEdge, boolean paused) {
        TestReelCapture.afterHeadlessFrame(fixture,
                new Bk2FrameInput(inputIndex, 0, 0, startEdge, "fixture Start edge=" + startEdge),
                paused ? "PAUSED" : "LEVEL");
    }

    public static void idle(HeadlessTestFixture fixture, int count) {
        for (int i = 0; i < count; i++) step(fixture, false, false, false, false, false);
    }
}
