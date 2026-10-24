package com.openggf.stageplay.runtime;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StageplayObjectDirection.PlacementIdentity;
import com.openggf.level.objects.StageplayObjectLoadPolicy;

import java.util.List;
import java.util.Set;

/** Supplies an isolated authored set or cleared initial cast to the normal native load. */
final class StageplayWorldModule extends DelegatingGameModule {
    private final Level authored;
    private final boolean initiallyCleared;
    private final Set<PlacementIdentity> suppressedPlacements;
    private List<ObjectSpawn> originalObjects = List.of();

    StageplayWorldModule(GameModule base, Level authored) {
        this(base, authored, Set.of());
    }

    StageplayWorldModule(GameModule base, Level authored, Set<PlacementIdentity> suppressedPlacements) {
        this(base, authored, suppressedPlacements, false);
    }

    StageplayWorldModule(GameModule base, Level authored, Set<PlacementIdentity> suppressedPlacements,
            boolean initiallyCleared) {
        super(base, "stageplay-native-world");
        this.initiallyCleared = initiallyCleared;
        this.authored = authored;
        this.suppressedPlacements = Set.copyOf(suppressedPlacements);
    }

    @Override
    public Level loadLevelOverride(int levelIndex) throws java.io.IOException {
        return authored == null
                ? super.loadLevelOverride(levelIndex)
                : MutableLevel.snapshot(authored);
    }

    @Override
    public boolean isSidekickSuppressedForZone(int zoneId) {
        // The director owns this disposable world's authored roster. Native zone
        // entry scenes must not remove its companions.
        return false;
    }

    @Override
    public Level transformDecodedLevel(Level source) {
        var transformed = super.transformDecodedLevel(source);
        originalObjects = List.copyOf(transformed.getObjects());
        return transformed;
    }

    List<ObjectSpawn> originalObjects() {
        return originalObjects;
    }

    @Override
    public <T> T getGameService(Class<T> type) {
        if (type == com.openggf.level.scroll.DirectedScrollPresentation.class)
            return type.cast(com.openggf.level.scroll.DirectedScrollPresentation.ENABLED);
        if (type == com.openggf.level.objects.NativePlacementSource.class)
            return type.cast((com.openggf.level.objects.NativePlacementSource) () -> originalObjects);
        if (type == WorldSessionPolicyProvider.class)
            return type.cast(
                    WorldSessionPolicyProvider.compose(
                            super.getGameService(WorldSessionPolicyProvider.class),
                            world -> new StageplayObjectLoadPolicy(initiallyCleared, suppressedPlacements),
                            world -> new com.openggf.level.objects.StageplayActivationPolicy()));
        return super.getGameService(type);
    }
}
