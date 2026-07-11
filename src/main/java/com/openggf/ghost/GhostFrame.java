package com.openggf.ghost;

/** One resolved render-state ghost frame (spec §7: final render state, never physics). */
@com.openggf.game.ModApi
public record GhostFrame(int x, int y, int mappingFrame, boolean hFlip, boolean vFlip,
                         boolean finished, int priorityBucket, boolean highPriority) {
}
