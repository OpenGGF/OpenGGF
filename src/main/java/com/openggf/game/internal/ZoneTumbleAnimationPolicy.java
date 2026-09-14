package com.openggf.game.internal;

/** Zone-owned presentation rule for negative type-zero tumble angles. */
public interface ZoneTumbleAnimationPolicy {
    /** Whether the native angle is used without reflection for this facing. */
    boolean negativeTumbleUsesUnreflectedAngle(boolean facingLeft);
}
