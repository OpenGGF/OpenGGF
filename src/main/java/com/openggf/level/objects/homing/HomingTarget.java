package com.openggf.level.objects.homing;

import java.util.Objects;

/**
 * One live homing target. {@code priority} is the hidden progress priority: zero for ordinary
 * targets, higher for the part a multi-part encounter must actually be struck through.
 * {@code x}/{@code y} are the ROM-centre aim point.
 */
public record HomingTarget(HomingTargetKind kind, int priority, int x, int y) {
    public HomingTarget {
        Objects.requireNonNull(kind, "kind");
        if (priority < 0) throw new IllegalArgumentException("Hidden homing priority cannot be negative");
    }
}
