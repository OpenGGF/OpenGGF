package com.openggf;

import com.openggf.configuration.DisplayPacing;

/** Real host scheduler and held-input entry, with fake time and no window swaps. */
public final class CdSpecialLiveFrameTestDriver implements Runnable {
    private final LiveFrameRuntime runtime;
    private long now;
    private int ticks;

    public CdSpecialLiveFrameTestDriver(GameLoop loop) {
        runtime = new LiveFrameRuntime(new LiveFrameRuntime.Owner() {
            public LiveFrameRuntime.HostState hostState() {
                return new LiveFrameRuntime.HostState(60, 60, true, false, loop.getCurrentGameMode(),
                        false, false, DisplayPacing.EXACT, DisplayOutput.UNKNOWN, false, false, false, false);
            }
            public boolean tick(boolean interpolationActive) {
                loop.stepPresentationFrame();
                Engine.presentOuterAudioFrame(loop, false, false);
                ticks++;
                return true;
            }
            public boolean draw(LivePictureRequest picture) { return true; }
            public void swap() { }
            public void swapInterval(int interval) { }
            public void serviceDevice() { }
        }, () -> now);
    }

    @Override
    public void run() {
        int before = ticks;
        for (int attempts = 0; ticks == before; attempts++) {
            if (attempts > 4) throw new AssertionError("live scheduler did not admit a service");
            var iteration = runtime.iterate();
            now = Math.max(now + 1, iteration.wakeAtNanos());
        }
    }
}
