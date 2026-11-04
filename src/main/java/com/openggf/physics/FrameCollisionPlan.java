package com.openggf.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the collision phases a caller intends to run for a frame.
 *
 * <p>The plan is deliberately descriptive: terrain probes stay in
 * {@link TerrainCollisionManager} and {@link CollisionSystem} only
 * orchestrates the requested phases. Solid objects are not a player
 * collision phase: each object resolves its own solid calls in its own
 * object slot, as the ROM's SolidObject family does. Playable movement still owns its
 * ROM-order ground-mode recomputation at the call sites that perform
 * terrain attachment or air landing, so the current named factories leave
 * {@link Phase#POST_RESOLUTION_GROUND_MODE} disabled.</p>
 */
@com.openggf.game.ModApi
public record FrameCollisionPlan(
        boolean runsTerrainProbes,
        boolean runsPostResolutionGroundMode,
        boolean recordsTrace) {

    @com.openggf.game.ModApi
    public enum Phase {
        TERRAIN_PROBES,
        POST_RESOLUTION_GROUND_MODE
    }

    /**
     * Full playable terrain pass, with trace events emitted around the probes.
     */
    public static FrameCollisionPlan playableFrame() {
        return new FrameCollisionPlan(true, false, true);
    }

    /**
     * Terrain sensor probes only. Callers that need trace diagnostics should
     * use their existing terrain-probe instrumentation explicitly.
     */
    public static FrameCollisionPlan terrainOnly() {
        return new FrameCollisionPlan(true, false, false);
    }

    public List<Phase> orderedPhases() {
        List<Phase> phases = new ArrayList<>(2);
        if (runsTerrainProbes) {
            phases.add(Phase.TERRAIN_PROBES);
        }
        if (runsPostResolutionGroundMode) {
            phases.add(Phase.POST_RESOLUTION_GROUND_MODE);
        }
        return List.copyOf(phases);
    }
}
