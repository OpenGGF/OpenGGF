package com.openggf.game.internal;

/** Internal render-only retained descriptor owner. Zero revision uses the ordinary level layout. */
public interface ForegroundDescriptorOverride {
    long foregroundDescriptorRevision();
    int foregroundDescriptorAt(int sourceX, int sourceY);

    /**
     * Whether the retained descriptors form a VDP plane ring that the tile pass must
     * sample wrapped, as the VDP wraps V-scroll, instead of clamping to the layout height.
     */
    default boolean foregroundDescriptorsWrapVertically() {
        return false;
    }
}
