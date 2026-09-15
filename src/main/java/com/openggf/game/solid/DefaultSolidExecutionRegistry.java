package com.openggf.game.solid;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultSolidExecutionRegistry
        implements SolidExecutionRegistry, RewindSnapshottable<SolidExecutionSnapshot> {
    /**
     * Expected entry count for the per-player maps built in the solid path:
     * a leader plus its sidekicks. {@code IdentityHashMap}'s no-arg constructor
     * expects 21 entries and sizes a 64-slot backing table accordingly, which is
     * an order of magnitude more than these maps ever hold — and they are built
     * for every solid object every frame. Exceeding the hint is merely a resize,
     * never a correctness problem.
     */
    private static final int TEAM_SIZE_HINT = 4;

    private final IdentityHashMap<ObjectInstance, IdentityHashMap<PlayableEntity, PlayerStandingState>> previous =
            new IdentityHashMap<>();
    private final IdentityHashMap<ObjectInstance, SolidCheckpointBatch> current =
            new IdentityHashMap<>();
    private ObjectSolidExecutionContext currentContext = ObjectSolidExecutionContext.inert();

    @Override
    public void beginFrame(int frameCounter, List<? extends PlayableEntity> players) {
        current.clear();
        currentContext = ObjectSolidExecutionContext.inert();
    }

    @Override
    public void beginObject(ObjectInstance object, ObjectSolidExecutionContext.Resolver resolver) {
        currentContext = new ObjectSolidExecutionContext(this, object, resolver);
    }

    @Override
    public ObjectSolidExecutionContext currentObject() {
        return currentContext;
    }

    @Override
    public PlayerStandingState previousStanding(ObjectInstance object, PlayableEntity player) {
        IdentityHashMap<PlayableEntity, PlayerStandingState> perPlayer = previous.get(object);
        if (perPlayer == null) {
            return PlayerStandingState.NONE;
        }
        return perPlayer.getOrDefault(player, PlayerStandingState.NONE);
    }

    @Override
    public void publishCheckpoint(SolidCheckpointBatch batch) {
        ObjectInstance executingObject = currentContext.object();
        if (executingObject != null && (batch == null || batch.object() != executingObject)) {
            throw new IllegalStateException(
                    "Published checkpoint batch must match the currently executing object.");
        }
        current.put(batch.object(), batch);
    }

    @Override
    public void endObject(ObjectInstance object) {
        currentContext = ObjectSolidExecutionContext.inert();
    }

    @Override
    public void finishFrame() {
        previous.clear();
        // forEach rather than entrySet: both maps here are identity-keyed, and
        // IdentityHashMap's entry iterator allocates a fresh Map.Entry for every
        // element it hands back. This runs once per solid object per frame, with
        // an inner pass per player, so entry-wise iteration was allocating
        // garbage proportional to the level's solid-object count every frame.
        current.forEach((object, batch) -> {
            IdentityHashMap<PlayableEntity, PlayerStandingState> perPlayer =
                    new IdentityHashMap<>(TEAM_SIZE_HINT);
            batch.perPlayer().forEach((player, result) -> perPlayer.put(player,
                    new PlayerStandingState(result.kind(), result.standingNow(), result.pushingNow())));
            previous.put(object, perPlayer);
        });
    }

    @Override
    public void clearTransientState() {
        current.clear();
        currentContext = ObjectSolidExecutionContext.inert();
    }

    @Override
    public String key() {
        return "solid-execution";
    }

    @Override
    public SolidExecutionSnapshot capture() {
        LevelManager levelManager = GameServices.levelOrNull();
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        if (objectManager == null) {
            return new SolidExecutionSnapshot(List.of());
        }
        var identities = objectManager.captureIdentityContext().requireIdentityTable();
        List<SolidExecutionSnapshot.PreviousStandingEntry> entries = new ArrayList<>();
        for (Map.Entry<ObjectInstance, IdentityHashMap<PlayableEntity, PlayerStandingState>> objectEntry
                : previous.entrySet()) {
            ObjectRefId objectId = identities.encodeObject(objectEntry.getKey());
            if (objectId == null) {
                continue;
            }
            for (Map.Entry<PlayableEntity, PlayerStandingState> playerEntry
                    : objectEntry.getValue().entrySet()) {
                PlayerRefId playerId = identities.encodePlayer(playerEntry.getKey());
                if (playerId == null) {
                    continue;
                }
                PlayerStandingState state = playerEntry.getValue();
                entries.add(new SolidExecutionSnapshot.PreviousStandingEntry(
                        objectId,
                        playerId,
                        state.kind(),
                        state.standing(),
                        state.pushing()));
            }
        }
        entries.sort(java.util.Comparator
                .comparing((SolidExecutionSnapshot.PreviousStandingEntry e) -> e.objectId().kind())
                .thenComparingInt(e -> e.objectId().spawnId())
                .thenComparingInt(e -> e.objectId().dynamicId())
                .thenComparingInt(e -> e.objectId().slotIndex())
                .thenComparingInt(e -> e.objectId().generation())
                .thenComparingInt(e -> e.playerId().encoded()));
        return new SolidExecutionSnapshot(entries);
    }

    @Override
    public void restore(SolidExecutionSnapshot s) {
        previous.clear();
        current.clear();
        currentContext = ObjectSolidExecutionContext.inert();
        LevelManager levelManager = GameServices.levelOrNull();
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        if (objectManager == null) {
            return;
        }
        var identities = objectManager.captureIdentityContext().requireIdentityTable();
        for (SolidExecutionSnapshot.PreviousStandingEntry entry : s.previousStanding()) {
            ObjectInstance object = identities.resolveObject(entry.objectId(), true);
            PlayableEntity player = identities.resolvePlayer(entry.playerId(), true);
            previous.computeIfAbsent(object, ignored -> new IdentityHashMap<>(TEAM_SIZE_HINT))
                    .put(player, new PlayerStandingState(
                            entry.kind(), entry.standing(), entry.pushing()));
        }
    }
}
