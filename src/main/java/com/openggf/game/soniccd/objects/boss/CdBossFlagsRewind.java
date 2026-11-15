package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.LevelManager;

import java.util.function.Supplier;

/**
 * Rewind capture of the loaded stage's {@code bossFlags} ({@link com.openggf.game.soniccd.objects.common.CdLevelFlags#bossFlags}):
 * level RAM the act 3 events read every frame (Tidal Tempest's {@code R43Events}, common/events.asm:348-366), so a
 * rewind to before its write must clear it again. The flags belong to the stage load ({@code CdLevel}); outside a Sonic
 * CD stage the capture is 0 and a restore does nothing.
 */
public final class CdBossFlagsRewind implements RewindSnapshottable<Integer> {
    private final Supplier<LevelManager> levels;

    public CdBossFlagsRewind(Supplier<LevelManager> levels) {
        this.levels = levels;
    }

    private CdLevel level() {
        LevelManager manager = levels.get();
        return manager != null && manager.getCurrentLevel() instanceof CdLevel cd ? cd : null;
    }

    @Override public String key() { return "soniccd.bossflags"; }

    @Override
    public Integer capture() {
        CdLevel level = level();
        return level == null ? 0 : level.flags().bossFlags;
    }

    @Override
    public void restore(Integer snapshot) {
        CdLevel level = level();
        if (level != null) level.flags().bossFlags = snapshot == null ? 0 : snapshot & 0xFF;
    }

    /** A snapshot taken before this adapter was registered: the level-start value. */
    @Override public void resetForMissingSnapshot() { restore(0); }
}
