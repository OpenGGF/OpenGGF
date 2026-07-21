package com.openggf.game.internal;

/** Engine-only capability for providers that pump deferred object-art work. */
public interface RuntimeObjectArtQueue {
    void processRuntimeArtQueue();
}
