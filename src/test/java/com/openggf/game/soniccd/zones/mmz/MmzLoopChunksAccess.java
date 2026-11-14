package com.openggf.game.soniccd.zones.mmz;

/** Test access to {@link MmzPlayerProfile}'s loop-chunk arithmetic. */
public final class MmzLoopChunksAccess {
    private MmzLoopChunksAccess() { }

    public static int loopChunk(int chunk, int layer) {
        return MmzPlayerProfile.loopChunk(chunk, layer);
    }
}
