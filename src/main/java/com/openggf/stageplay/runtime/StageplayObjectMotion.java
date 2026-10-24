package com.openggf.stageplay.runtime;

/** Shared visual-Puppet integrator used by playback and terrain-route validation. */
public final class StageplayObjectMotion {
    private StageplayObjectMotion() { }
    public static double[] move(double x, double y, double targetX, double targetY, double speed) {
        double dx = targetX - x, dy = targetY - y, distance = StrictMath.hypot(dx, dy);
        if (distance <= speed) return new double[]{targetX, targetY};
        return new double[]{x + dx / distance * speed, y + dy / distance * speed};
    }
}
