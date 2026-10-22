package com.openggf.stageplay.route;

/** Authored visual clearance, not an object's native collision/controller definition. */
public record StageplayRouteFootprint(
        int halfWidth, int height, int footOffsetY, int collisionPath) {
    public StageplayRouteFootprint {
        if (halfWidth < 1
                || halfWidth > 128
                || height < 1
                || height > 256
                || footOffsetY < -256
                || footOffsetY > 256
                || collisionPath < 0
                || collisionPath > 1)
            throw new IllegalArgumentException(
                    "Visual route footprint requires half-width1..128, height1..256, foot"
                            + " offset-256..256 and collision path0/1");
    }
}
