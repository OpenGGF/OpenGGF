package com.openggf.stageplay.runtime;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;

import java.util.List;
import java.util.Map;

/** Supplies an isolated authored set or cleared initial cast to the normal native load. */
final class StageplayWorldModule extends DelegatingGameModule {
    private final Level authored;
    private final boolean clearPlacements;

    StageplayWorldModule(GameModule base, Level authored, boolean clearPlacements) {
        super(base, "stageplay-native-world");
        this.authored = authored;
        this.clearPlacements = clearPlacements;
    }

    @Override public Level loadLevelOverride(int levelIndex) throws java.io.IOException {
        return authored == null ? super.loadLevelOverride(levelIndex) : MutableLevel.snapshot(authored);
    }

    @Override public boolean isSidekickSuppressedForZone(int zoneId) {
        // The director owns this disposable world's authored roster. Native zone
        // entry scenes must not remove its companions; legacy worlds use the
        // undecorated module and keep their original suppression policy.
        return false;
    }

    @Override public Level transformDecodedLevel(Level source) {
        var transformed = super.transformDecodedLevel(source);
        if (!clearPlacements) return transformed;
        // Initial scene assembly, before gameplay publication: do not mutate the decoder's
        // snapshot or install editor commands into a running world.
        var isolated = MutableLevel.snapshot(transformed);
        isolated.restoreObjectBackedRingState(new MutableLevel.ObjectBackedRingState(List.of(), List.of(), Map.of()));
        return isolated;
    }
}
