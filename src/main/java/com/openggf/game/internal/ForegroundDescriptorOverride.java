package com.openggf.game.internal;

/** Internal render-only retained descriptor owner. Zero revision uses the ordinary level layout. */
public interface ForegroundDescriptorOverride {
    long foregroundDescriptorRevision();
    int foregroundDescriptorAt(int sourceX, int sourceY);

    /**
     * Rows of the VDP plane ring the retained descriptors repeat over, or 0 when they follow
     * the finite layout. The tile pass then builds and wraps exactly this many rows, as the
     * VDP wraps V-scroll; wrapping at a layout height that is not a whole number of rings
     * would shift every wrapped row.
     */
    default int foregroundDescriptorRingRows() {
        return 0;
    }
}
