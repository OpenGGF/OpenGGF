package com.openggf.game.soniccd.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;

import java.util.function.Supplier;

/** Rewinds the stop word owned by the currently loaded stage. */
public final class CdTimeStopRewind implements RewindSnapshottable<Integer> {
    private final Supplier<LevelManager> levels;

    public CdTimeStopRewind(Supplier<LevelManager> levels) {
        this.levels = levels;
    }

    private CdLevel stage() {
        LevelManager manager = levels.get();
        return manager != null && manager.getCurrentLevel() instanceof CdLevel cd ? cd : null;
    }

    @Override public String key() { return "soniccd.timestop"; }

    @Override
    public Integer capture() {
        CdLevel stage = stage();
        return stage == null ? 0 : stage.flags().timeStopWord;
    }

    @Override
    public void restore(Integer snapshot) {
        CdLevel stage = stage();
        if (stage != null) stage.flags().timeStopWord = snapshot == null ? 0 : snapshot;
    }

    @Override public void resetForMissingSnapshot() { restore(0); }
}
