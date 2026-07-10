package com.openggf.game.sonic2.specialstage;

import com.openggf.game.SpecialStageProvider;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

public final class Sonic2SpecialStageRewindAdapter
        implements RewindSnapshottable<Sonic2SpecialStageSnapshot> {
    private final Sonic2SpecialStageManager manager;

    public Sonic2SpecialStageRewindAdapter(Sonic2SpecialStageManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public String key() {
        return SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY;
    }

    @Override
    public Sonic2SpecialStageSnapshot capture() {
        return manager.captureRewindSnapshot();
    }

    @Override
    public void restore(Sonic2SpecialStageSnapshot snapshot) {
        manager.restoreRewindSnapshot(snapshot);
    }
}
