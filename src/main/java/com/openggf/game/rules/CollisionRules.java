package com.openggf.game.rules;

import com.openggf.game.CollisionModel;

@com.openggf.game.ModApi
public record CollisionRules(
        CollisionModel collisionModel,
        boolean groundWallCollisionEnabled,
        boolean groundWallPushRequiresFacingIntoWall,
        boolean repeatedObjectRideGroundWallResponseDeferred,
        boolean topSolidLandingAllowsZeroDist,
        boolean airBottomSolidHitClearsGroundSpeed,
        boolean airRightWallHitContinuesIntoCeilingSeparation,
        boolean airLeftWallHitContinuesIntoCeilingSeparation,
        AirCollisionRules air,
        boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
        boolean solidObjectOffscreenGate,
        boolean solidObjectRequiresSidekickOnScreen,
        boolean sidekickPushBypassUsesGraceStatus,
        boolean sidekickSuppressesFastLeaderTinyFollowNudge,
        boolean sidekickClearsStalePushVelocityBeforeGroundMove,
        boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
        boolean rightWallDeepProbePreservesPenetration,
        boolean solidObjectBarelyPokingResolvesAsSide,
        boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
        boolean advanceWaterLevelBeforePlayerPhysics,
        int defaultCollisionLayoutYMask) {
    public CollisionRules {
        airBottomSolidHitClearsGroundSpeed = air.bottomSolidHitClearsGroundSpeed();
        airRightWallHitContinuesIntoCeilingSeparation =
                air.rightWallHitContinuesIntoCeilingSeparation();
        airLeftWallHitContinuesIntoCeilingSeparation =
                air.leftWallHitContinuesIntoCeilingSeparation();
    }

    /** Convenience constructor for the nested air-rule shape. */
    public CollisionRules(CollisionModel collisionModel,
            boolean groundWallCollisionEnabled, boolean groundWallPushRequiresFacingIntoWall,
            boolean repeatedObjectRideGroundWallResponseDeferred, boolean topSolidLandingAllowsZeroDist,
            AirCollisionRules air, boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
            boolean solidObjectOffscreenGate, boolean solidObjectRequiresSidekickOnScreen,
            boolean sidekickPushBypassUsesGraceStatus, boolean sidekickSuppressesFastLeaderTinyFollowNudge,
            boolean sidekickClearsStalePushVelocityBeforeGroundMove,
            boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
            boolean rightWallDeepProbePreservesPenetration, boolean solidObjectBarelyPokingResolvesAsSide,
            boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
            boolean advanceWaterLevelBeforePlayerPhysics, int defaultCollisionLayoutYMask) {
        this(collisionModel, groundWallCollisionEnabled, groundWallPushRequiresFacingIntoWall,
                repeatedObjectRideGroundWallResponseDeferred, topSolidLandingAllowsZeroDist,
                air.bottomSolidHitClearsGroundSpeed(),
                air.rightWallHitContinuesIntoCeilingSeparation(),
                air.leftWallHitContinuesIntoCeilingSeparation(), air,
                fullSolidBottomOverlapUsesCurrentYRadiusOnly, solidObjectOffscreenGate,
                solidObjectRequiresSidekickOnScreen, sidekickPushBypassUsesGraceStatus,
                sidekickSuppressesFastLeaderTinyFollowNudge,
                sidekickClearsStalePushVelocityBeforeGroundMove,
                solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
                rightWallDeepProbePreservesPenetration, solidObjectBarelyPokingResolvesAsSide,
                solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                advanceWaterLevelBeforePlayerPhysics, defaultCollisionLayoutYMask);
    }
}
