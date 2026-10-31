package com.openggf.game.rules;

import com.openggf.game.CollisionModel;

@com.openggf.game.ModApi
public record CollisionRules(
        CollisionModel collisionModel,
        boolean groundWallCollisionEnabled,
        boolean groundWallPushRequiresFacingIntoWall,
        boolean repeatedObjectRideGroundWallResponseDeferred,
        boolean topSolidLandingAllowsZeroDist,
        AirCollisionRules air,
        boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
        boolean solidObjectOffscreenGate,
        boolean solidObjectRequiresSidekickOnScreen,
        boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
        boolean rightWallDeepProbePreservesPenetration,
        boolean solidObjectBarelyPokingResolvesAsSide,
        boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
        boolean advanceWaterLevelBeforePlayerPhysics,
        int defaultCollisionLayoutYMask,
        boolean layoutYMaskAppliesToAllLookups) {

    /** Backward-compatible constructor for rules that retain targeted Y masking. */
    public CollisionRules(CollisionModel collisionModel,
            boolean groundWallCollisionEnabled, boolean groundWallPushRequiresFacingIntoWall,
            boolean repeatedObjectRideGroundWallResponseDeferred, boolean topSolidLandingAllowsZeroDist,
            AirCollisionRules air, boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
            boolean solidObjectOffscreenGate, boolean solidObjectRequiresSidekickOnScreen,
            boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
            boolean rightWallDeepProbePreservesPenetration, boolean solidObjectBarelyPokingResolvesAsSide,
            boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
            boolean advanceWaterLevelBeforePlayerPhysics, int defaultCollisionLayoutYMask) {
        this(collisionModel, groundWallCollisionEnabled, groundWallPushRequiresFacingIntoWall,
                repeatedObjectRideGroundWallResponseDeferred, topSolidLandingAllowsZeroDist, air,
                fullSolidBottomOverlapUsesCurrentYRadiusOnly, solidObjectOffscreenGate,
                solidObjectRequiresSidekickOnScreen,
                solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
                rightWallDeepProbePreservesPenetration, solidObjectBarelyPokingResolvesAsSide,
                solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                advanceWaterLevelBeforePlayerPhysics, defaultCollisionLayoutYMask, false);
    }
}
