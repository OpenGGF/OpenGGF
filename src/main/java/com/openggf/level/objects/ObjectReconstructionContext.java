package com.openggf.level.objects;

import java.util.Optional;

/** Creator reconstruction inputs, excluding engine restore queues and dynamic-entry bookkeeping. */
@com.openggf.game.ModApi
public final class ObjectReconstructionContext {
    private final RewindRecreateContext source;
    ObjectReconstructionContext(RewindRecreateContext source) { this.source=java.util.Objects.requireNonNull(source); }
    public ObjectSpawn spawn() { return source.spawn(); }
    public PerObjectRewindSnapshot state() { return source.state(); }
    public ObjectServices services() { return source.objectServices(); }
    /** Already reconstructed parents can be queried; captured references reconnect in phase two. */
    public ObjectQuery objects() {
        return source.objectManager() != null ? source.objectManager().objectQuery()
                : services() == null ? ObjectQuery.EMPTY : services().objectQuery();
    }
    public ObjectPlayerQuery players() { return services() == null ? null : services().playerQuery(); }
    public <T extends PerObjectRewindSnapshot.ObjectSubclassRewindExtra> Optional<T> payload(Class<T> type) {
        Object payload=state() == null ? null : state().objectSubclassExtra();
        return type.isInstance(payload) ? Optional.of(type.cast(payload)) : Optional.empty();
    }
    /** Preserves deferred player-bound reconstruction without exposing the host's captured entry. */
    public void enqueuePendingPlayerBoundEntry(Class<?> baseType) { source.enqueuePendingPlayerBoundEntry(baseType); }
}
