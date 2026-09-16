package com.openggf.game.internal;

/** Internal render-only descriptor owner. Zero revision leaves ordinary tile caching unchanged. */
public interface BackgroundDescriptorOverride {
    long backgroundDescriptorRevision();
    int backgroundDescriptorAt(int sourceX, int sourceY);
}
