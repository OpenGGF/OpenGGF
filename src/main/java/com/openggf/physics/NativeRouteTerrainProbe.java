package com.openggf.physics;

import com.openggf.level.LevelManager;
import java.util.Objects;

/** Fixed foreground geometry sampled by the native FindFloor/FindWall/upward FindFloor owners.
 * Moving dual-plane collision has no static route graph and is explicitly unsupported.
 */
public final class NativeRouteTerrainProbe implements RouteTerrainProbe {
    private final LevelManager level;
    private final BackgroundPlaneCollisionProvider provider;

    public NativeRouteTerrainProbe(LevelManager level, BackgroundPlaneCollisionProvider provider) {
        this.level = Objects.requireNonNull(level, "route terrain");
        this.provider = Objects.requireNonNull(provider, "background collision provider");
    }
    @Override public boolean dynamicCollision() { return provider.state(level).active(); }

    @Override public Floor floor(int x, int feetY, int collisionPath) {
        path(collisionPath);
        TerrainCheckResult best = null;
        int bestY = 0;
        // Authoring search bound, not a movement/gravity constant. Each call still
        // enters the native current/adjacent 16px collision-tile state machine.
        for (int delta = -16; delta <= 16; delta += 16) {
            int origin = feetY + delta;
            var found = ObjectTerrainUtils.checkFloorDistOnLayer(level, x, origin, (byte) 0,
                    collisionPath == 1, true);
            if (!found.foundSurface() || found.distance() < -16 || found.distance() >= 16) continue;
            int y = origin + found.distance();
            if (best == null || Math.abs(y - feetY) < Math.abs(bestY - feetY)) {
                best = found; bestY = y;
            }
        }
        return best == null ? Floor.missing() : new Floor(true, bestY, best.angle() & 255);
    }

    @Override public boolean clear(int left, int top, int right, int bottom, int collisionPath) {
        path(collisionPath);
        if (right < left || bottom < top || (long) right - left > 256 || (long) bottom - top > 256)
            throw new IllegalArgumentException("Visual footprint exceeds the bounded terrain probe");
        // A native horizontal sensor detects the first all-sided surface through
        // the complete body row, including an obstruction inside a wide composite.
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x += 16) {
                var wall = ObjectTerrainUtils.routeWallDist(level, x, y, collisionPath == 1);
                if (wall.foundSurface() && wall.distance() < Math.min(15, (long) right - x)) return false;
            }
        }
        for (int x = left; x <= right; x++) {
            var ceiling = ObjectTerrainUtils.routeCeilingDist(level, x, top, collisionPath == 1);
            if (ceiling.foundSurface() && ceiling.distance() < 0) return false;
        }
        return true;
    }
    private static void path(int value) {
        if (value < 0 || value > 1) throw new IllegalArgumentException("Collision path must be primary or secondary");
    }
}
