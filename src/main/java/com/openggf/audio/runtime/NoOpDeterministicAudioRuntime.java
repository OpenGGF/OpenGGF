package com.openggf.audio.runtime;

public final class NoOpDeterministicAudioRuntime implements DeterministicAudioRuntime {
    public static final NoOpDeterministicAudioRuntime INSTANCE = new NoOpDeterministicAudioRuntime();

    private NoOpDeterministicAudioRuntime() {
    }

    @Override
    public void advanceFrame(long frame, FrameAudioMode mode) {
    }
}
