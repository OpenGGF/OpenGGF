package com.openggf.level.objects;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.sprites.managers.SpriteManager;

import java.util.HashSet;
import java.util.Set;

/** Session-owned admission decisions, installed before a native level constructs its objects. */
public final class StageplayObjectLoadPolicy implements WorldSessionPolicyState {
    final Set<StageplayObjectDirection.PlacementIdentity> removed = new HashSet<>();
    boolean clearObjects;
    boolean clearBadniks;
    private final boolean initiallyCleared;

    public StageplayObjectLoadPolicy() { this(false, Set.of()); }
    public StageplayObjectLoadPolicy(boolean initiallyCleared,
            Set<StageplayObjectDirection.PlacementIdentity> suppressed) {
        this.initiallyCleared = initiallyCleared;
        clearObjects = initiallyCleared;
        removed.addAll(suppressed);
    }
    void seedInitialLayout(java.util.List<ObjectSpawn> placements) {
        if (initiallyCleared) for (var spawn : placements)
            if (spawn.layoutIndex() >= 0) removed.add(StageplayObjectDirection.PlacementIdentity.of(spawn));
    }

    public record Snapshot(
            Set<StageplayObjectDirection.PlacementIdentity> removed,
            boolean clearObjects,
            boolean clearBadniks) {
        public Snapshot {
            removed = Set.copyOf(removed);
        }
    }

    public <T> T getService(Class<T> type) {
        return type == StageplayObjectLoadPolicy.class ? type.cast(this) : null;
    }

    public RewindSnapshottable<Snapshot> rewindAdapter() {
        return new RewindSnapshottable<>() {
            public String key() {
                return "stageplay-object-load-policy";
            }

            public Snapshot capture() {
                return snapshot();
            }

            public void restore(Snapshot snapshot) {
                restorePolicy(snapshot);
            }

            public void resetForMissingSnapshot() {
                clear();
            }
        };
    }

    Snapshot snapshot() {
        return new Snapshot(removed, clearObjects, clearBadniks);
    }

    void restorePolicy(Snapshot snapshot) {
        clear();
        removed.addAll(snapshot.removed());
        clearObjects = snapshot.clearObjects();
        clearBadniks = snapshot.clearBadniks();
    }

    private void clear() {
        removed.clear();
        clearObjects = false;
        clearBadniks = false;
    }

    public void beforeAssembly(LevelLoadCause cause) {}

    public void bindRoster(SpriteManager sprites) {}

    public void failedAssembly(LevelLoadCause cause) {}

    public void closeScreens() {}

    public void retire() {
        clear();
    }
}
