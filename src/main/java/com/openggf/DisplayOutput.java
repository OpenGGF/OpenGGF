package com.openggf;

/**
 * Window output observation. The epoch changes when the monitor, window mode or context
 * changes. A window refresh of zero is unknown; the presentation ceiling is only an
 * Auto rendering cap and may cover several possible outputs. Neither claims VRR.
 */
record DisplayOutput(long epoch, int windowRefreshHz, int presentationCeilingHz) {
    DisplayOutput(long epoch, int knownRefreshHz) { this(epoch, knownRefreshHz, knownRefreshHz); }
    static final DisplayOutput UNKNOWN = new DisplayOutput(0, 0, 0);
}
