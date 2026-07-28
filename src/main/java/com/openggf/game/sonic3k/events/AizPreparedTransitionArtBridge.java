package com.openggf.game.sonic3k.events;

/**
 * Session-owned boundary for AIZ art that the ROM leaves resident across the
 * act 1-to-act 2 level-data reload.
 */
public interface AizPreparedTransitionArtBridge {

    void retainAizFireOverlay(byte[] tiles8x8);

    byte[] aizFireOverlayCopy();

    int aizFireOverlayTileCount();
}
