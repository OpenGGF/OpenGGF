package com.openggf.route;

/**
 * A copied post-row player observation. Centres are ROM {@code x_pos}/{@code y_pos} with their
 * 16-bit subpixel fractions; speeds are raw signed 8.8 values.
 */
public record PlayerView(int centreX, int centreY, int xSub, int ySub, int xSpeed, int ySpeed, int groundSpeed,
                         int angle, boolean air, boolean rolling, boolean spindash, boolean crouching, boolean hurt,
                         boolean dead, int rings, boolean onObject, boolean objectControlled) {
}
