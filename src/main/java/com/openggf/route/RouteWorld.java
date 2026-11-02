package com.openggf.route;

/** A search host: a replay host that can also checkpoint inside one boundary epoch. */
public interface RouteWorld extends ReplayWorld {
    /** Captures the whole world and host state, or throws {@link RouteHostException} when refused. */
    WorldCheckpoint capture();

    /** Restores a checkpoint this host captured in its current epoch. */
    void restore(WorldCheckpoint checkpoint);
}
