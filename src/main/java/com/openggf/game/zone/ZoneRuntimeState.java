package com.openggf.game.zone;

import com.openggf.physics.BackgroundPlaneCollisionProvider;

@com.openggf.game.ModApi
public interface ZoneRuntimeState {
    String gameId();
    int zoneIndex();
    int actIndex();

    /**
     * Whether the current zone runtime forces a full-width BG tilemap rather
     * than the normal 512px VDP-plane wrap. Used by HTZ earthquake mode where
     * BG high-priority tiles (cave ceiling) are rendered as a direct overlay
     * spanning the full BG map.
     *
     * <p>Default {@code false}: zones honour {@code ZoneFeatureProvider.bgWrapsHorizontally()}.
     */
    default boolean requiresFullWidthBgTilemap() {
        return false;
    }

    /** Retains the fixed 64x32 Plane-B cache; zone events own row/column refreshes. */
    default boolean usesPersistentBackgroundVdpPlane() { return false; }

    /**
     * Optional explicit semantic collision mode for zones whose event state
     * owns plane reversal. Empty retains the legacy game-state/scroll adapter.
     */
    default BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
        return null;
    }

    /**
     * Captures gameplay-relevant per-zone runtime state as a byte buffer for
     * rewind. Default returns an empty array (no state to capture).
     * Implementations override and serialize their fields deterministically.
     */
    default byte[] captureBytes() {
        return new byte[0];
    }

    /**
     * Restores from a previously-captured byte buffer. Default no-op.
     * Implementations override and deserialize deterministically.
     */
    default void restoreBytes(byte[] bytes) {
        // no-op
    }
}
