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
import java.util.Map;
import java.util.Set;

/** Supplies an isolated authored set or cleared initial cast to the normal native load. */
final class StageplayWorldModule extends DelegatingGameModule {
    private final Level authored;
    private final boolean clearPlacements;
    private final Set<PlacementIdentity> suppressedPlacements;
    private List<ObjectSpawn> originalObjects = List.of();

    StageplayWorldModule(GameModule base, Level authored, boolean clearPlacements) {
        this(base, authored, clearPlacements, Set.of());
    }

    StageplayWorldModule(
            GameModule base,
            Level authored,
            boolean clearPlacements,
            Set<PlacementIdentity> suppressedPlacements) {
        super(base, "stageplay-native-world");
        this.authored = authored;
        this.clearPlacements = clearPlacements;
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
        // entry scenes must not remove its companions; legacy worlds use the
        // undecorated module and keep their original suppression policy.
        return false;
    }

    @Override
    public Level transformDecodedLevel(Level source) {
        var transformed = super.transformDecodedLevel(source);
        originalObjects = List.copyOf(transformed.getObjects());
        if (!clearPlacements && suppressedPlacements.isEmpty()) return transformed;
        // Initial scene assembly, before gameplay publication: do not mutate the decoder's
        // snapshot or install editor commands into a running world.
        var isolated = MutableLevel.snapshot(transformed);
        if (clearPlacements)
            isolated.restoreObjectBackedRingState(
                    new MutableLevel.ObjectBackedRingState(List.of(), List.of(), Map.of()));
        else {
            var state = isolated.snapshotObjectBackedRingState();
            var placements =
                    state.objects().stream()
                            .filter(
                                    spawn ->
                                            spawn.layoutIndex() < 0
                                                    || !suppressedPlacements.contains(
                                                            PlacementIdentity.of(spawn)))
                            .toList();
            isolated.restoreObjectBackedRingState(
                    new MutableLevel.ObjectBackedRingState(
                            placements, state.rings(), state.mapping()));
        }
        // This transformed snapshot is the initial decoder input to both managers.
        // Its editor mutation notifications would otherwise resync the placement
        // lists on the first story tick, discarding explicitly admitted Free roles.
        isolated.consumeObjectsDirty();
        isolated.consumeRingsDirty();
        return isolated;
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
                            world -> new StageplayObjectLoadPolicy(clearPlacements, suppressedPlacements),
                            world -> new com.openggf.level.objects.StageplayActivationPolicy()));
        return super.getGameService(type);
    }
}
