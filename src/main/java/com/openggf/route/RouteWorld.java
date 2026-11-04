package com.openggf.route;

/** A search host: a replay host that can also checkpoint inside one boundary epoch. */
public interface RouteWorld extends ReplayWorld {
    /** Captures the whole world and host state, or throws {@link RouteHostException} when refused. */
    WorldCheckpoint capture();

    /** Restores a checkpoint this host captured in its current epoch. */
    void restore(WorldCheckpoint checkpoint);

    /**
     * A key the host certifies as complete: two states with equal keys (and equal search
     * controller state) have the same future under the same rows. Static terrain probes can
     * certify one; live worlds with objects, RNG and timers certify none, and are never merged.
     */
    default java.util.Optional<Object> equivalenceKey() {
        return java.util.Optional.empty();
    }
}
