package com.openggf.game.sonic2.timing;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.OptionalInt;

/** Rewind-owned publication gate for Sonic 2's pending {@code Level_PlayBgm}. */
public final class Sonic2LevelMusicScheduler
        implements RewindSnapshottable<Sonic2LevelMusicScheduler.Snapshot> {
    public static final String REWIND_KEY = "sonic2.level-music-scheduler";

    private int pendingMusicId = -1;
    private int remainingVBlanks;

    public void arm(int musicId, int terminalRowBucket) {
        if (terminalRowBucket <= 0) {
            throw new IllegalArgumentException("terminal row bucket must be positive");
        }
        pendingMusicId = musicId;
        remainingVBlanks = terminalRowBucket;
    }

    public OptionalInt serviceVBlank() {
        if (pendingMusicId < 0) {
            return OptionalInt.empty();
        }
        remainingVBlanks--;
        if (remainingVBlanks > 0) {
            return OptionalInt.empty();
        }
        int released = pendingMusicId;
        cancel();
        return OptionalInt.of(released);
    }

    public boolean pending() {
        return pendingMusicId >= 0;
    }

    public void cancel() {
        pendingMusicId = -1;
        remainingVBlanks = 0;
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(pendingMusicId, remainingVBlanks);
    }

    @Override
    public void restore(Snapshot snapshot) {
        if (snapshot == null) {
            cancel();
            return;
        }
        pendingMusicId = snapshot.pendingMusicId();
        remainingVBlanks = snapshot.remainingVBlanks();
    }

    @Override
    public void resetForMissingSnapshot() {
        cancel();
    }

    public record Snapshot(int pendingMusicId, int remainingVBlanks) {
    }
}
