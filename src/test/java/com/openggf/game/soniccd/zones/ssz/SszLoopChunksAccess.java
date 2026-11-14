package com.openggf.game.soniccd.zones.ssz;

/** Test access to {@link SszPlayerProfile}'s loop-chunk arithmetic. */
public final class SszLoopChunksAccess {
    private SszLoopChunksAccess() { }

    public static int loopChunk(int chunk, boolean path, boolean layer) {
        return SszPlayerProfile.loopChunk(chunk, path, layer);
    }
}
