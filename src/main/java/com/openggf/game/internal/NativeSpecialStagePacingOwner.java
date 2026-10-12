package com.openggf.game.internal;

/** Host scheduling identity; accepted-sample ordinal is restored, lifetime entry epoch is not. */
public final class NativeSpecialStagePacingOwner {
    private long entryEpoch;
    private long acceptedSamples;
    public void beginEntry() { entryEpoch++; acceptedSamples = 0; }
    public void acceptedSample() { acceptedSamples++; }
    public long captureSamples() { return acceptedSamples; }
    public void restoreSamples(long samples) { acceptedSamples = samples; }
    public NativeSpecialStagePacing.State state(boolean interactive, int p1Held, int p2Held, boolean p2Supported) {
        return new NativeSpecialStagePacing.State(interactive, entryEpoch, acceptedSamples, p1Held, p2Held, p2Supported);
    }
}
