package com.openggf.mods.state;

/** Deterministic xorshift64* stream whose complete state is one captured long.
 * Zero remains the absorbing, unseeded state; callers choose their own seed fallback.
 */
@com.openggf.game.ModApi
public final class SnapshotRandom {
    private long state;
    public SnapshotRandom(long seed) { reset(seed); }
    public void reset(long seed) {
        state=seed;
    }
    public long snapshot() { return state; }
    public void restore(long capturedState) { reset(capturedState); }
    public long nextLong() {
        long x=state;
        x ^= x >>> 12; x ^= x << 25; x ^= x >>> 27;
        state=x;
        return x * 0x2545F4914F6CDD1DL;
    }
    /** Bounds zero and one consume no randomness, matching common mod selection code. */
    public int nextInt(int bound) {
        return bound <= 1 ? 0 : (int)Long.remainderUnsigned(nextLong(),bound);
    }
}
