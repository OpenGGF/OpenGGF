package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gameplay-session owner for single-use seamless resource handoffs.
 */
public final class SeamlessTransitionResourceHandoffRegistry
        implements RewindSnapshottable<
                SeamlessTransitionResourceHandoffRegistry.Snapshot> {
    private long nextId;
    private final Map<SeamlessTransitionResourceHandoffId,
            SeamlessTransitionResourceHandoff> pending =
            new LinkedHashMap<>();

    public SeamlessTransitionResourceHandoffId register(
            SeamlessTransitionResourceHandoff handoff) {
        Objects.requireNonNull(handoff, "handoff");
        SeamlessTransitionResourceHandoffId id =
                new SeamlessTransitionResourceHandoffId(nextId++);
        pending.put(id, handoff);
        return id;
    }

    public SeamlessTransitionResourceHandoff peek(
            SeamlessTransitionResourceHandoffId id) {
        SeamlessTransitionResourceHandoff handoff = pending.get(id);
        if (handoff == null) {
            throw new IllegalStateException(
                    "missing or already-consumed seamless resource handoff "
                            + id);
        }
        return handoff;
    }

    public SeamlessTransitionResourceHandoff claim(
            SeamlessTransitionResourceHandoffId id) {
        SeamlessTransitionResourceHandoff handoff = pending.remove(id);
        if (handoff == null) {
            throw new IllegalStateException(
                    "missing or already-consumed seamless resource handoff "
                            + id);
        }
        return handoff;
    }

    @Override
    public String key() {
        return "seamless_transition_resource_handoffs";
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(nextId, Map.copyOf(pending));
    }

    @Override
    public void restore(Snapshot snapshot) {
        nextId = snapshot.nextId();
        pending.clear();
        pending.putAll(snapshot.pending());
    }

    @Override
    public void resetForMissingSnapshot() {
        nextId = 0;
        pending.clear();
    }

    public record Snapshot(
            long nextId,
            Map<SeamlessTransitionResourceHandoffId,
                    SeamlessTransitionResourceHandoff> pending) {
        public Snapshot {
            pending = Map.copyOf(pending);
        }
    }
}
