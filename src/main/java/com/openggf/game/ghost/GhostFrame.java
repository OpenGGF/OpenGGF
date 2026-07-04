package com.openggf.game.ghost;

/** One resolved render-state ghost frame (spec §7: final render state, never physics). */
public record GhostFrame(int x, int y, int mappingFrame, boolean hFlip, boolean vFlip,
                         boolean finished, int priorityBucket, boolean highPriority) {
}
