package com.openggf.physics;

import com.openggf.game.GroundMode;

/** Pure native paired distance/tie selection; angle response remains CollisionSystem-owned. */
final class NativeGroundSelection {
    private NativeGroundSelection() {}
    static SensorResult select(GroundMode mode, SensorResult right, SensorResult left) {
        if (right == null) return left;
        if (left == null) return right;
        boolean leftIsPrimary = mode == GroundMode.RIGHTWALL || mode == GroundMode.LEFTWALL;
        SensorResult primary = leftIsPrimary ? left : right;
        SensorResult secondary = leftIsPrimary ? right : left;
        return primary.distance() < secondary.distance() ? primary : secondary;
    }
}
