package com.openggf;

/**
 * Window output observation. The epoch changes when the monitor, window mode or context
 * changes; an advertised refresh of zero is unknown. It makes no variable-refresh claim.
 */
record DisplayOutput(long epoch, int advertisedRefreshHz) {
    static final DisplayOutput UNKNOWN = new DisplayOutput(0, 0);
}
