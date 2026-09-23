package com.openggf.game.internal;

/** Internal render-only retained descriptor owner. Zero revision uses the ordinary level layout. */
public interface ForegroundDescriptorOverride {
    long foregroundDescriptorRevision();
    int foregroundDescriptorAt(int sourceX, int sourceY);
}
