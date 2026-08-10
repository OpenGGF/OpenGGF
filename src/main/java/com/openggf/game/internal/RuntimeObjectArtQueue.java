package com.openggf.game.internal;

/** Engine-only capability for providers that pump deferred object-art work. */
public interface RuntimeObjectArtQueue {
    void processRuntimeArtQueue();

    /** Completes work submitted by an earlier object pass before object polling. */
    default void processRuntimeArtQueueBeforeObjects() {
    }
}
