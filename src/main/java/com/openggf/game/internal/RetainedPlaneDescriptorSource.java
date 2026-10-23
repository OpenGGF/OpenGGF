package com.openggf.game.internal;

/**
 * Internal render-only owner of complete retained plane descriptor images.
 * Null planes, or planes with revision zero, use the ordinary level layout.
 * This contract does not expose or replace gameplay geometry.
 */
public interface RetainedPlaneDescriptorSource {
    Plane foregroundPlane();
    Plane backgroundPlane();

    interface Plane {
        /** Positive power-of-two tile dimensions, at most 128 on either axis. */
        int widthTiles();
        int heightTiles();

        /** Nonzero while active; changes whenever descriptor contents change. */
        long revision();

        /**
         * Native 16-bit pattern word at plane pixel coordinates, anchored at zero.
         * The owner wraps both axes using this plane's dimensions. Consumers build
         * the complete image, independently of the level layout and camera window.
         */
        int descriptorAt(int pixelX, int pixelY);
    }
}
