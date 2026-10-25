package com.openggf.stageplay.route;

import com.openggf.physics.RouteTerrainProbe;

/** Shared directed-object visual support; this is not an object's native controller. */
public final class StageplayVisualSupport {
    private StageplayVisualSupport() {}

    public sealed interface Support permits Supported, Gap {}
    public record Supported(int anchorY, RouteTerrainProbe.Floor left,
                            RouteTerrainProbe.Floor centre, RouteTerrainProbe.Floor right) implements Support {}
    public record Gap(int columnX) implements Support {}

    public static Support support(RouteTerrainProbe terrain, StageplayRouteFootprint f,
                                  double x, double nearY) {
        int feet = (int) Math.round(nearY + f.footOffsetY());
        int[] columns = {(int) Math.round(x - f.halfWidth()), (int) Math.round(x),
                (int) Math.round(x + f.halfWidth())};
        var floors = new RouteTerrainProbe.Floor[3];
        int y = Integer.MAX_VALUE;
        for (int i = 0; i < columns.length; i++) {
            floors[i] = terrain.floor(columns[i], feet, f.collisionPath());
            if (!floors[i].found()) return new Gap(columns[i]);
            y = Math.min(y, floors[i].y());
        }
        return new Supported(y - f.footOffsetY(), floors[0], floors[1], floors[2]);
    }

    public static boolean clear(RouteTerrainProbe terrain, StageplayRouteFootprint f,
                                double x, double y) {
        int feet = (int) Math.round(y + f.footOffsetY());
        return terrain.clear((int) Math.round(x - f.halfWidth()), feet - f.height(),
                (int) Math.round(x + f.halfWidth()), feet, f.collisionPath());
    }

    public sealed interface Rest permits Resting, Unsupported, Unstable, Obstructed {}
    public record Resting(Supported support) implements Rest {}
    public record Unsupported(Gap gap) implements Rest {}
    public record Unstable(int firstAnchorY, int secondAnchorY) implements Rest {}
    public record Obstructed(Supported support) implements Rest {}

    /** Recheck at the final anchor: support(a) == a and clear(a), by construction. */
    public static Rest rest(RouteTerrainProbe terrain, StageplayRouteFootprint f, int x, int surfaceY) {
        var first = support(terrain, f, x, surfaceY - f.footOffsetY());
        if (first instanceof Gap gap) return new Unsupported(gap);
        int anchor = ((Supported) first).anchorY();
        var second = support(terrain, f, x, anchor);
        if (second instanceof Gap gap) return new Unsupported(gap);
        var finalSupport = (Supported) second;
        if (finalSupport.anchorY() != anchor) return new Unstable(anchor, finalSupport.anchorY());
        return clear(terrain, f, x, anchor) ? new Resting(finalSupport) : new Obstructed(finalSupport);
    }
}
