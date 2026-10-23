package com.openggf.game.internal;

/** Native checkpoint camera words, separate from per-frame rewind accumulators. */
public interface CheckpointScrollProvider {
    CheckpointBackgroundScroll captureCheckpointBackground(int zoneId);
    void restoreCheckpointBackground(int zoneId, CheckpointBackgroundScroll background);
}
