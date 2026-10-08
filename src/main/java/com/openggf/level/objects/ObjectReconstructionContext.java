package com.openggf.level.objects;

import java.util.Optional;

/** Creator reconstruction inputs, excluding engine restore queues and dynamic-entry bookkeeping. */
@com.openggf.game.ModApi
public final class ObjectReconstructionContext {
    private final RewindRecreateContext source;
    ObjectReconstructionContext(RewindRecreateContext source) { this.source=java.util.Objects.requireNonNull(source); }
    public ObjectSpawn spawn() { return source.spawn(); }
    public PerObjectRewindSnapshot state() { return source.state(); }
    /** Restore-time services, or null when the host supplied no optional services. */
    public ObjectServices services() { return source.objectServices(); }
    /** Already reconstructed parents can be queried; captured references reconnect in phase two. */
    public ObjectQuery objects() {
        ObjectServices restoredServices = source.objectServices();
        return source.objectManager() != null ? source.objectManager().objectQuery()
                : restoredServices == null ? ObjectQuery.EMPTY : restoredServices.objectQuery();
    }
    public ObjectPlayerQuery players() {
        ObjectServices restoredServices = source.objectServices();
        return restoredServices == null ? null : restoredServices.playerQuery();
    }
    public <T extends PerObjectRewindSnapshot.ObjectSubclassRewindExtra> Optional<T> payload(Class<T> type) {
        Object payload=state() == null ? null : state().objectSubclassExtra();
        return type.isInstance(payload) ? Optional.of(type.cast(payload)) : Optional.empty();
    }
    /** Preserves deferred player-bound reconstruction without exposing the host's captured entry. */
    public void enqueuePendingPlayerBoundEntry(Class<?> baseType) { source.enqueuePendingPlayerBoundEntry(baseType); }
}
