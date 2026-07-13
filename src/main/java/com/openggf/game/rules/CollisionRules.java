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
    /** Compatibility constructor for API 1.1 rules, which used the original 11-bit layout mask. */
    public CollisionRules(CollisionModel collisionModel,
                          boolean groundWallCollisionEnabled,
                          boolean groundWallPushRequiresFacingIntoWall,
                          boolean repeatedObjectRideGroundWallResponseDeferred,
                          boolean topSolidLandingAllowsZeroDist,
                          boolean airBottomSolidHitClearsGroundSpeed,
                          boolean airRightWallHitContinuesIntoCeilingSeparation,
                          boolean airLeftWallHitContinuesIntoCeilingSeparation,
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
                          boolean advanceWaterLevelBeforePlayerPhysics) {
        this(collisionModel, groundWallCollisionEnabled, groundWallPushRequiresFacingIntoWall,
                repeatedObjectRideGroundWallResponseDeferred, topSolidLandingAllowsZeroDist,
                airBottomSolidHitClearsGroundSpeed, airRightWallHitContinuesIntoCeilingSeparation,
                airLeftWallHitContinuesIntoCeilingSeparation, fullSolidBottomOverlapUsesCurrentYRadiusOnly,
                solidObjectOffscreenGate, solidObjectRequiresSidekickOnScreen, sidekickPushBypassUsesGraceStatus,
                sidekickSuppressesFastLeaderTinyFollowNudge, sidekickClearsStalePushVelocityBeforeGroundMove,
                solidObjectTopBranchAlwaysLiftsOnUpwardVelocity, rightWallDeepProbePreservesPenetration,
                solidObjectBarelyPokingResolvesAsSide, solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                advanceWaterLevelBeforePlayerPhysics, 0x07FF);
    }
}
