package com.openggf.game.sonic1.specialstage;

import com.openggf.game.SpecialStageProvider;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

final class Sonic1SpecialStageRewindAdapter
        implements RewindSnapshottable<Sonic1SpecialStageSnapshot> {
    private final Sonic1SpecialStageManager manager;

    Sonic1SpecialStageRewindAdapter(Sonic1SpecialStageManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public String key() {
        return SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY;
    }

    @Override
    public Sonic1SpecialStageSnapshot capture() {
        return manager.captureRewindSnapshot();
    }

    @Override
    public void restore(Sonic1SpecialStageSnapshot snapshot) {
        manager.restoreRewindSnapshot(snapshot);
    }
}
