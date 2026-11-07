package com.openggf.game.internal;

/** Bounded production-caller ownership; never retained across a deferred load row. */
@FunctionalInterface
public interface NativeLevelLoadCallerScope extends AutoCloseable {
    NativeLevelLoadCallerScope NONE = () -> { };
    @Override void close();
}
