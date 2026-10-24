package com.openggf.stageplay.route;

/** Bounded shot-entry route request. Positions are authored actor-centre coordinates. */
public record StageplayRouteRequest(
        String actorId,
        int startFrame,
        double targetX,
        double targetY,
        int maxFrames,
        int maxNodes,
        int maxTicks,
        int maxMillis,
        double urgency,
        StageplayRouteFootprint footprint,
        double objectSpeed, com.openggf.stageplay.StageplayDocument.RouteMotion motion) {
    public StageplayRouteRequest(String actorId, int startFrame, double targetX, double targetY,
            int maxFrames, int maxNodes, int maxTicks, int maxMillis, double urgency,
            StageplayRouteFootprint footprint, double objectSpeed) {
        this(actorId, startFrame, targetX, targetY, maxFrames, maxNodes, maxTicks, maxMillis,
                urgency, footprint, objectSpeed, com.openggf.stageplay.StageplayDocument.RouteMotion.DEFAULT);
    }
    public StageplayRouteRequest(
            String actorId, int startFrame, double targetX, double targetY, int maxFrames) {
        this(actorId, startFrame, targetX, targetY, maxFrames, 1024, 120_000, 60_000, 1, null, 1);
    }

    public StageplayRouteRequest {
        java.util.Objects.requireNonNull(motion, "motion");
        if (actorId == null || !actorId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Route actorId must be a portable actor identity");
        if (startFrame < 0
                || startFrame >= 36_000
                || !Double.isFinite(targetX)
                || !Double.isFinite(targetY)
                || targetX < 0
                || targetX > 32767
                || targetY < -32768
                || targetY > 32767)
            throw new IllegalArgumentException(
                    "Route frame or destination exceeds native coordinate limits");
        if (maxFrames < 1
                || maxFrames > 3600
                || maxNodes < 1
                || maxNodes > 4096
                || maxTicks < 1
                || maxTicks > 500_000
                || maxMillis < 1
                || maxMillis > 120_000)
            throw new IllegalArgumentException(
                    "Route budgets require frames 1..3600, nodes 1..4096, ticks 1..500000 and"
                            + " milliseconds 1..120000");
        if (!Double.isFinite(urgency)
                || urgency <= 0
                || urgency > 1
                || !Double.isFinite(objectSpeed)
                || objectSpeed <= 0
                || objectSpeed > 16)
            throw new IllegalArgumentException(
                    "Route urgency must be 0..1 exclusive of 0; visual object speed must be 0..16"
                            + " exclusive of 0");
    }
}
