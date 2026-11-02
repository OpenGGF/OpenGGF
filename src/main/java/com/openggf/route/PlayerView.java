package com.openggf.route;

import java.util.Objects;

/**
 * A copied post-row player observation. Centres are ROM {@code x_pos}/{@code y_pos} with their
 * 16-bit subpixel fractions; speeds are raw signed 8.8 values. {@link Motion} carries the live
 * movement constants native steering reads; it never drives physics.
 */
public record PlayerView(int centreX, int centreY, int xSub, int ySub, int xSpeed, int ySpeed, int groundSpeed,
                         int angle, boolean air, boolean rolling, boolean spindash, boolean crouching, boolean hurt,
                         boolean dead, int rings, boolean onObject, boolean objectControlled, boolean facingLeft,
                         Motion motion) {
    public PlayerView {
        Objects.requireNonNull(motion, "motion");
    }

    /**
     * Live movement facts: top speed, acceleration, deceleration (roll deceleration while
     * rolling), friction (roll drag while rolling), the slope push this tick, and skidding.
     */
    public record Motion(int maxSpeed, int runAccel, int runDecel, int friction, int slopeImpulse, boolean skidding) {
        public static final Motion NONE = new Motion(0, 0, 0, 0, 0, false);
    }

    /** Centre X with its subpixel fraction, in pixels. */
    public double x() {
        return centreX + xSub / 65536.0;
    }

    /** Centre Y with its subpixel fraction, in pixels. */
    public double y() {
        return centreY + ySub / 65536.0;
    }
}
